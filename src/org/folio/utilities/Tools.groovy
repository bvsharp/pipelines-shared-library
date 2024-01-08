package org.folio.utilities

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic
import groovy.text.GStringTemplateEngine
import org.folio.Constants
import org.folio.models.OkapiTenant
import org.folio.models.RancherNamespace

class Tools {
    Object steps

    Tools(Object steps) {
        this.steps = steps
    }

    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    String copyResourceFileToWorkspace(String srcPath) {
        String destPath = steps.env.WORKSPACE + File.separator + new File(srcPath).getName()
        steps.writeFile file: destPath, text: steps.libraryResource(srcPath)
        logger.info("Copied ${srcPath} to ${steps.env.WORKSPACE}")
        return destPath
    }

    List getGitHubTeamsIds(List teams) {
        steps.withCredentials([steps.usernamePassword(credentialsId: Constants.PRIVATE_GITHUB_CREDENTIALS_ID, passwordVariable: 'token', usernameVariable: 'username')]) {
            String url = "https://api.github.com/orgs/folio-org/teams?per_page=100"
            ArrayList headers = [[name: "Authorization", value: "Bearer " + steps.token]]
            def response = new HttpClient(steps).getRequest(url, headers)
            List ids = []
            if (response.status == HttpURLConnection.HTTP_OK) {
                teams.each { team ->
                    try {
                        ids.add(jsonParse(response.content).find { it["name"] == team }["id"])
                    } catch (ignored) {
                        logger.warning("Unable to get GitHub id for team ${team}")
                    }
                }
            } else {
                logger.warning(new HttpClient(steps).buildHttpErrorMessage(response))
            }
            return ids
        }
    }

    /**
     * Parse json object to groovy map
     * @param json
     * @return
     */
    @NonCPS
    static def jsonParse(String json) {
        new JsonSlurperClassic().parseText(json)
    }

    /**
     * Remove last char from string
     * @param str
     * @return
     */
    @NonCPS
    static def removeLastChar(String str) {
        return (str == null || str.length() == 0) ? null : (str.substring(0, str.length() - 1))
    }

    /**
     * Evaluate groovy expression
     * @param expression groovy expression
     * @param parameters parameters
     * @return result
     */
    static def eval(String expression, Map<String, Object> parameters) {
        Binding b = new Binding();
        parameters.each { k, v ->
            b.setVariable(k, v);
        }
        GroovyShell sh = new GroovyShell(b);
        return sh.evaluate(expression);
    }

    List findAllRegex(String list, String regex) {
        return new JsonSlurperClassic().parseText(list).findAll { s -> s ==~ /${regex}/ }
    }

    void createFileFromString(String filePathName, String fileContent) {
        steps.writeFile file: filePathName, text: """${fileContent}"""
        logger.info("Created file in ${filePathName}")
    }

    String buildLdpConfigMap(RancherNamespace namespace, Map dbParameters) {
        String templateName = 'ldp_ldpconf.json.template'
        String tenantId = namespace.defaultTenantId
        OkapiTenant tenant = namespace.tenants[tenantId]
        if (tenant?.getOkapiConfig()?.getLdpConfig()) {
            Map binding = [
                tenant_id             : tenantId,
                tenant_admin_user     : tenant.adminUser.username,
                tenant_admin_password : tenant.adminUser.password,
                okapi_url             : namespace.getDomains()['okapi'],
                deployment_environment: 'staging',
                db_host               : dbParameters.dbHost,
                db_port               : dbParameters.dbPort,
                folio_db_name         : dbParameters.dbName,
                folio_db_user         : dbParameters.dbUsername,
                folio_db_password     : dbParameters.dbPassword,
                ldp_db_name           : tenant.okapiConfig.ldpConfig.ldpDbName,
                ldp_db_user_name      : tenant.okapiConfig.ldpConfig.ldpDbUserName,
                ldp_db_user_password  : tenant.okapiConfig.ldpConfig.ldpDbUserPassword,
                sqconfig_repo_name    : tenant.okapiConfig.ldpConfig.sqconfigRepoName,
                sqconfig_repo_owner   : tenant.okapiConfig.ldpConfig.sqconfigRepoOwner,
                sqconfig_repo_token   : tenant.okapiConfig.ldpConfig.sqconfigRepoToken
            ]
            this.copyResourceFileToWorkspace("okapi/configurations/" + templateName)
            def content = steps.readFile templateName
            String body = new GStringTemplateEngine().createTemplate(content).make(binding).toString()
            createFileFromString('ldpconf.json', body)
            return './ldpconf.json'
        } else {
            throw new Exception("LDP config not found for tenant '${tenantId}'")
        }
    }
}
