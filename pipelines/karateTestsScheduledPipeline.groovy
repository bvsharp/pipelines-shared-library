@Library('pipelines-shared-library@RANCHER-252') _

import org.folio.karate.results.KarateTestsExecutionSummary
import org.folio.karate.teams.TeamAssignment
import org.folio.version.VersionConstants
import org.folio.version.semantic.Order
import org.folio.version.semantic.SemanticVersionComparator
import org.jenkinsci.plugins.workflow.libs.Library

def okapiUrl, tenant, user, password
def spinUpEnvironmentJobName = "/Rancher/Project"
def spinUpEnvironmentJob
def tearDownEnvironmentJob
def karateTestsJobName = "/Testing/Karate tests"
def karateTestsJob
KarateTestsExecutionSummary karateTestsExecutionSummary
def teamAssignment

List<String> versions = Eval.me(jobsParameters.getOkapiVersion())
def okapiVersions = versions.toSorted(new SemanticVersionComparator(order: Order.DESC, preferredBranches: [VersionConstants.MASTER_BRANCH]))
List<String> uiImages = Eval.me("project_name", "karate", jobsParameters.getDockerImagesList())

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Karate tests repository branch to checkout')
        string(name: 'threadsCount', defaultValue: '4', description: 'Number of parallel threads')
    }

    stages {
        stage("Create environment") {
            steps {
                script {
                    def jobParameters = getEnvironmentJobParameters('apply', okapiVersions, uiImages)

                    spinUpEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Run karate tests") {
            steps {
                script {
                    def jobParameters = [
                        string(name: 'branch', value: params.branch),
                        string(name: 'threadsCount', value: "4"),
                        string(name: 'modules', value: ""),
                        string(name: 'okapiUrl', value: okapiUrl),
                        string(name: 'tenant', value: tenant),
                        string(name: 'adminUserName', value: user),
                        string(name: 'adminPassword', value: password)
                    ]

                    karateTestsJob = build job: karateTestsJobName, parameters: jobParameters, wait: true, propagate: false
                }
            }
        }

        stage("Parallel") {
            parallel {
                stage("Destroy environment") {
                    def jobParameters = getEnvironmentJobParameters('destroy', okapiVersions, uiImages)

                    tearDownEnvironmentJob = build job: spinUpEnvironmentJobName, parameters: jobParameters, wait: true, propagate: false
                }
                stage("Collect test results") {
                    stages {
                        stage("Copy downstream job artifacts") {
                            steps {
                                script {
                                    def jobNumber = karateTestsJob.number
                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "cucumber.zip")
                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "junit.zip")
                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "karate-summary.zip")
                                    copyArtifacts(projectName: karateTestsJobName, selector: specific("${jobNumber}"), filter: "teams-assignment.json")

                                    unzip zipFile: "cucumber.zip", dir: "cucumber"
                                    unzip zipFile: "junit.zip", dir: "junit"
                                    unzip zipFile: "karate-summary.zip", dir: "karate-summary"
                                }
                            }
                        }

                        stage('Publish tests report') {
                            steps {
                                script {
                                    cucumber buildStatus: "UNSTABLE",
                                        fileIncludePattern: "cucumber/**/target/karate-reports*/*.json"

                                    junit testResults: 'junit/**/target/karate-reports*/*.xml'
                                }
                            }
                        }

                        stage("Collect execution results") {
                            steps {
                                script {
                                    karateTestsExecutionSummary = karateTestUtils.collectTestsResults("karate-summary/**/target/karate-reports*/karate-summary-json.txt")

                                    karateTestUtils.attachCucumberReports(karateTestsExecutionSummary)
                                }
                            }
                        }

                        stage("Parse teams assignment") {
                            steps {
                                script {
                                    def jsonContents = readJSON file: "teams-assignment.json"
                                    teamAssignment = new TeamAssignment(jsonContents)
                                }
                            }
                        }


                        stage("Send slack notifications") {
                            steps {
                                script {
                                    karateTestUtils.sendSlackNotification(karateTestsExecutionSummary, teamAssignment)
                                }
                            }
                        }

                        stage("Sync jira tickets") {
                            steps {
                                script {
                                    karateTestUtils.syncJiraIssues(karateTestsExecutionSummary, teamAssignment)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private List getEnvironmentJobParameters(String action, List<String> okapiVersions, List<String> images) {
    [
        string(name: 'action', value: action),
        string(name: 'okapi_version', value: okapiVersions[0]),
        string(name: 'rancher_cluster_name', value: "folio-testing"),
        string(name: 'project_name', value: "test"),
        string(name: 'folio_repository', value: "complete"),
        string(name: 'folio_branch', " value:master"),
        string(name: 'stripes_image_tag', value: images[0]),
        string(name: 'tenant_id', value: "master"),
        string(name: 'tenant_name', value: "Master karate tenant"),
        string(name: 'tenant_description', value: "Karate tests main tenant"),
        booleanParam('load_reference', value: true),
        booleanParam('load_sample', value: false),
        string(name: 'folio_repository', "complete")
    ]
}
