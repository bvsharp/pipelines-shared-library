package org.folio.client.jira

import groovy.json.JsonOutput
import hudson.AbortException
import org.apache.http.HttpHeaders
import org.folio.client.jira.model.*

import java.util.logging.Logger

class JiraClient {

    private static final Logger log = Logger.getLogger(JiraClient.class.getName())

    def pipeline

    String url

    String authToken

    JiraParser parser = new JiraParser()

    def createIgnoreFields = ["issuetype"]

    JiraClient(def pipeline, String url, String user, String password) {
        this.pipeline = pipeline
        this.url = url
        this.authToken = Base64.getEncoder().encodeToString("${user}:${password}".getBytes())
    }

    String createJiraTicket(String projectKey, String issueTypeName, Map fields) {
        def issueCreateMeta = getJiraIssueCreateMeta(projectKey, issueTypeName)

        def (issueId, updateFieldsCandidates) = createJiraTicketInternal(projectKey, issueTypeName, fields, issueCreateMeta)

        if (updateFieldsCandidates) {
            updateJiraTicket(issueId, updateFieldsCandidates)
        }

        issueId
    }

    def createJiraTicketInternal(String projectKey, String issueTypeName, Map fields, JiraIssueCreateMeta issueCreateMeta) {
        def jiraFields = issueCreateMeta.getFieldsByName()
        def updateFieldsCandidates = [:]
        def createFields = [:]
        fields.each { name, value ->
            if (!createIgnoreFields.contains(name)) {
                if (!jiraFields[name]) {
                    updateFieldsCandidates[name] = value
                } else {
                    def jiraField = jiraFields[name]
                    if (!jiraField.allowedValues) {
                        createFields[jiraField.id] = value
                    } else {
                        createFields[jiraField.id] = ["id": jiraField.allowedValues[value]]
                    }
                }
            }
        }

        def content = """
        {
          "fields": {
            "project": {
              "id": "${issueCreateMeta.projectId}"
            },
            "issuetype": {
              "id": "${issueCreateMeta.issueTypeId}"
            }
            ${
            if (fields) {
                def json = JsonOutput.toJson(createFields)
                ",\n" + json.substring(1, json.length() - 1)
            }
        }
          }
        }
"""

        def response = postRequest(JiraResources.ISSUE, content)
        if (response.status < 300) {
            def body = pipeline.readJSON text: response.content
            [body.id, updateFieldsCandidates]
        } else {
            throw new AbortException("Unable to create jira ticket. Server retuned ${response.status} status code. Content: ${response.content}")
        }
    }

    def updateJiraTicket(String issueId, Map fields) {
        def issueUpdateMeta = getJiraIssueUpdateMeta(issueId)
        def jiraFields = issueUpdateMeta.getFieldsByName()

        def preparedFields = [:]
        fields.each { name, value ->
            if (!jiraFields[name]) {
                log.info "Jira field for '${name}' was not found. This value will be ignored."
            } else {
                def jiraField = jiraFields[name]
                if (!jiraField.allowedValues) {
                    preparedFields[jiraField.id] = value
                } else {
                    preparedFields[jiraField.id] = ["id": jiraField.allowedValues[value]]
                }
            }
        }

        if (preparedFields) {
            def content = """
        {
          "fields": ${JsonOutput.toJson(preparedFields)}
        }
"""

            def response = putRequest("${JiraResources.ISSUE}/${issueId}", content)
            if (response.status > 300) {
                throw new AbortException("Unable to update jira ticket. Server retuned ${response.status} status code. Content: ${response.content}")
            }
        }
    }

    JiraIssueCreateMeta getJiraIssueCreateMeta(String projectKey, String issueTypeName) {
        withResponse("${JiraResources.ISSUE_CREATE_META}?projectKeys=${projectKey}&issuetypeNames=${issueTypeName}&expand=projects.issuetypes.fields",
            { response, body ->
                parser.parseIssueCreateMeta(body)
            },
            "Unable to get issue create meta for project '${projectKey}' and issue type '${issueTypeName}'"
        )
    }

    JiraIssueUpdateMeta getJiraIssueUpdateMeta(String issueId) {
        withResponse("${JiraResources.ISSUE}/${issueId}/${JiraResources.ISSUE_EDIT_META}",
            { response, body ->
                parser.parseIssueUpdateMeta(body)
            },
            "Unable to get issue update meta for issue '${issueId}'"
        )
    }

    JiraProject getJiraProject(String key) {
        withResponse("${JiraResources.PROJECT}/${key}",
            { response, body ->
                parser.parseProject(body)
            },
            "Unable to get jira project with '${key}' key"
        )
    }

    JiraPriority getJiraPriority(String name) {
        withResponse("${JiraResources.PRIORITY}",
            { response, body ->
                def priority = body.find { priority -> priority.name == name }
                parser.parseJiraPriority(priority)
            },
            "Unable to get jira priority with '${name}' name"
        )
    }

    JiraStatus getJiraStatus(String name) {
        withResponse("${JiraResources.STATUS}/${name}",
            { response, body ->
                parser.parseJiraStatus(body)
            },
            "Unable to get jira priority with '${name}' name"
        )
    }

    private withResponse(endpoint, successClosure, errorMessage) {
        def response = getRequest(endpoint)

        if (response.status < 300) {
            def body = pipeline.readJSON text: response.content
            return successClosure(response, body)
        } else {
            throw new AbortException("${errorMessage}. Server retuned ${response.status} status code. Content: ${response.content}")
        }
    }

    private getRequest(String endpoint) {
        pipeline.httpRequest url: "${url}/rest/api/2/${endpoint}",
            contentType: "APPLICATION_JSON",
            customHeaders: [[name: HttpHeaders.AUTHORIZATION, value: "Basic ${authToken}"]],
            validResponseCodes: "100:599"
    }

    private postRequest(String endpoint, String contents) {
        pipeline.httpRequest url: "${url}/rest/api/2/${endpoint}",
            httpMode: "POST",
            contentType: "APPLICATION_JSON",
            customHeaders: [[name: HttpHeaders.AUTHORIZATION, value: "Basic ${authToken}"]],
            requestBody: contents,
            validResponseCodes: "100:599"
    }

    private putRequest(String endpoint, String contents) {
        pipeline.httpRequest url: "${url}/rest/api/2/${endpoint}",
            httpMode: "PUT",
            contentType: "APPLICATION_JSON",
            customHeaders: [[name: HttpHeaders.AUTHORIZATION, value: "Basic ${authToken}"]],
            requestBody: contents,
            validResponseCodes: "100:599"
    }

}
