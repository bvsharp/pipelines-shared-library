import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-239') _

def karateEnvironment = "jenkins"

pipeline {
    agent { label 'jenkins-agent-java11' }

    parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Karate tests repository branch to checkout')
        string(name: 'okapiUrl', defaultValue: 'https://ptf-perf-okapi.ci.folio.org', description: 'Target environment OKAPI URL')
        string(name: 'tenant', defaultValue: 'fs09000000', description: 'Tenant name for tests execution')
        string(name: 'adminUserName', defaultValue: 'folio', description: 'Admin user name')
        string(name: 'adminPassword', defaultValue: 'folio', description: 'Admin user password')
        //password(name: 'adminPassword', defaultValue: 'folio', description: 'Admin user password')
    }

    stages {
        stage("Checkout") {
            steps {
                script {
                    sshagent(credentials: ['11657186-f4d4-4099-ab72-2a32e023cced']) {
                        checkout([
                            $class           : 'GitSCM',
                            branches         : [[name: "*/${params.branch}"]],
                            extensions       : scm.extensions + [[$class             : 'SubmoduleOption',
                                                                  disableSubmodules  : false,
                                                                  parentCredentials  : false,
                                                                  recursiveSubmodules: true,
                                                                  reference          : '',
                                                                  trackingSubmodules : false]],
                            userRemoteConfigs: [[url: 'https://github.com/folio-org/folio-integration-tests.git']]
                        ])
                    }
                }
            }
        }

        stage("Build karate config") {
            steps {
                script {
                    def jenkinsKarateConfigContents = getKarateConfig()
                    def files = findFiles(glob: '**/karate-config.js')

                    files.each { file ->
                        String path = file.path.replaceAll("\\\\", "/")
                        def folderPath = path.substring(0, path.lastIndexOf("/"))

                        echo "Creating file ${folderPath}/karate-config-${karateEnvironment}.js"
                        writeFile file: "${folderPath}/karate-config-${karateEnvironment}.js", text: jenkinsKarateConfigContents
                    }
                }
            }
        }

        stage('Run karate tests') {
            steps {
                script {
                    withMaven(
                        jdk: 'openjdk-11-jenkins-slave-all',
                        maven: 'maven3-jenkins-slave-all',
                        mavenSettingsConfig: 'folioci-maven-settings'
                    ) {
                        sh "mvn test -DfailIfNoTests=false -DargLine=-Dkarate.env=${karateEnvironment}"

//                        withCredentials([usernamePassword(credentialsId: 'testrail-ut56', passwordVariable: 'testrail_password', usernameVariable: 'testrail_user'), string(credentialsId: 'mod-kb-ebsco-key', variable: 'ebsco_key'), string(credentialsId: 'mod-kb-ebsco-url', variable: 'ebsco_url'), string(credentialsId: 'mod-kb-ebsco-id', variable: 'ebsco_id'), string(credentialsId: 'mod-kb-ebsco-usageId', variable: 'ebsco_usage_id'), string(credentialsId: 'mod-kb-ebsco-usageSecret', variable: 'ebsco_usage_secret'), string(credentialsId: 'mod-kb-ebsco-usageKey', variable: 'ebsco_usage_key')]) {
//                            sh """
//      export kbEbscoCredentialsApiKey=${ebsco_key}
//      export kbEbscoCredentialsUrl=${ebsco_url}
//      export kbEbscoCredentialsCustomerId=${ebsco_id}
//      export usageConsolidationCredentialsId=${ebsco_usage_id}
//      export usageConsolidationCredentialsSecret=${ebsco_usage_secret}
//      export usageConsolidationCustomerKey=${ebsco_usage_key}
//      mvn test -Dkarate.env=${okapiDns} -DfailIfNoTests=false -Dtestrail_url=${TestRailUrl} -Dtestrail_userId=${testrail_user} -Dtestrail_pwd=${testrail_password} -Dtestrail_projectId=${TestRailProjectId} -DkbEbscoCredentialsApiKey=${ebsco_key} -DkbEbscoCredentialsUrl=${ebsco_url} -DkbEbscoCredentialsCustomerId=${ebsco_id} -DusageConsolidationCredentialsId=${ebsco_usage_id} -DusageConsolidationCredentialsSecret=${ebsco_usage_secret} -DusageConsolidationCustomerKey=${ebsco_usage_key}
//      """
                    }
                }
            }
        }

        stage('Publish tests report') {
            steps {
                script {
                    cucumber buildStatus: "UNSTABLE",
                        fileIncludePattern: "**/target/karate-reports*/*.json"

                    junit testResults: '**/target/karate-reports*/*.xml'
                }
            }
        }
    }
}

def getKarateConfig() {
    """
function fn() {
    var config = {
        baseUrl: '${params.okapiUrl}',
        admin: {
            tenant: '${params.tenant}',
            name: '${params.adminUserName}',
            password: '${adminPassword}'
        }
    }

    return config;
}
"""
}




//sh "mkdir ${env.WORKSPACE}/folio-integration-tests/cucumber-reports"
//sh "find . | grep json | grep '/target/karate-reports' | xargs -i cp {} ${env.WORKSPACE}/folio-integration-tests/cucumber-reports"
//teams = ['thunderjet', 'bama', 'firebird', 'prokopovych', 'folijet', 'spitfire', 'vega', 'core-platform', 'erm-delivery', 'fse', 'stripes', 'leipzig',
//         'ncip', 'thor', 'falcon', 'volaris', 'knowledgeware', 'spring']
//teams_test = ['spitfire', 'bama', 'folijet', 'thunderjet', 'firebird', 'prokopovych', 'vega', 'core_platform', 'falcon']
//team_modules = [spitfire: ['mod-kb-ebsco-java', 'tags', 'codexekb', 'mod-notes', 'mod-quick-marc', 'passwordvalidator'],
//                folijet: ['mod-source-record-storage', 'mod-source-record-manager', 'mod-data-import', 'data-import', 'mod-data-import-converter-storage'],
//                thunderjet: ['mod-finance', 'edge-orders', 'mod-gobi', 'mod-orders', 'mod-organizations', 'mod-invoice', 'mod-ebsconet', 'cross-modules'],
//                firebird: ['mod-audit', 'edge-dematic', 'edge-caiasoft', 'dataexport', 'oaipmh'],
//                prokopovych: ['mod-inventory', 'mod-users-bl', 'edge-patron', 'edge-rtac', 'mod-users'],
//                vega: ['mod-event-config', 'mod-sender', 'mod-circulation', 'mod-template-engine', 'mod-email', 'mod-notify', 'mod-feesfines', 'mod-patron-blocks', 'mod-circulation'],
//                core_platform: ['mod-configuration', 'mod-permissions', 'mod-login-saml', 'mod-user-import'],
//                falcon: ['mod-search'],
//                bama: ['mod-calendar']
//]

//sh "mkdir ${env.WORKSPACE}/folio-integration-tests/cucumber-reports"
//sh "find . | grep json | grep '/target/karate-reports' | xargs -i cp {} ${env.WORKSPACE}/folio-integration-tests/cucumber-reports"
//teams = ['thunderjet', 'bama', 'firebird', 'prokopovych', 'folijet', 'spitfire', 'vega', 'core-platform', 'erm-delivery', 'fse', 'stripes', 'leipzig',
//         'ncip', 'thor', 'falcon', 'volaris', 'knowledgeware', 'spring']
//teams_test = ['spitfire', 'bama', 'folijet', 'thunderjet', 'firebird', 'prokopovych', 'vega', 'core_platform', 'falcon']
//team_modules = [spitfire: ['mod-kb-ebsco-java', 'tags', 'codexekb', 'mod-notes', 'mod-quick-marc', 'passwordvalidator'],
//                folijet: ['mod-source-record-storage', 'mod-source-record-manager', 'mod-data-import', 'data-import', 'mod-data-import-converter-storage'],
//                thunderjet: ['mod-finance', 'edge-orders', 'mod-gobi', 'mod-orders', 'mod-organizations', 'mod-invoice', 'mod-ebsconet', 'cross-modules'],
//                firebird: ['mod-audit', 'edge-dematic', 'edge-caiasoft', 'dataexport', 'oaipmh'],
//                prokopovych: ['mod-inventory', 'mod-users-bl', 'edge-patron', 'edge-rtac', 'mod-users'],
//                vega: ['mod-event-config', 'mod-sender', 'mod-circulation', 'mod-template-engine', 'mod-email', 'mod-notify', 'mod-feesfines', 'mod-patron-blocks', 'mod-circulation'],
//                core_platform: ['mod-configuration', 'mod-permissions', 'mod-login-saml', 'mod-user-import'],
//                falcon: ['mod-search'],
//                bama: ['mod-calendar']
//]
//dir("${env.WORKSPACE}/folio-integration-tests/cucumber-reports"){
//    for (team in teams_test){
//        sh """
//        mkdir ${team}
//        touch ${team}/status.txt
//        touch ${team}/failed.txt
//        echo -n SUCCESS > ${team}/status.txt
//        """
//        for (mod in team_modules[team]){
//            sh """
//          for i in \$(find .. | grep json | grep '/target/karate-reports'| grep summary); do
//		        if [[ \$(cat \$i | grep ${mod}) ]]; then
//			        if [[ \$(cat \$i | grep '"failed":true') ]]; then
//				        echo -n FAILED > ${team}/status.txt
//                echo ${mod} >> ${team}/failed.txt
//				        break
//			        fi
//		        fi
//	        done
//          """
//        }
//    }
//}
