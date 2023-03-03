#!groovy
@Library('pipelines-shared-library@RANCHER-314-misha') _

import org.folio.Constants
import org.jenkinsci.plugins.workflow.libs.Library

properties([
    buildDiscarder(logRotator(numToKeepStr: '20')),
    disableConcurrentBuilds(),
    parameters([
        booleanParam(name: 'refreshParameters', defaultValue: false, description: 'Do a dry run and refresh pipeline configuration'),
        choice(name: 'action', choices: ['apply', 'destroy',], description: 'Choose what should be done with cluster'),
        jobsParameters.clusterName(),
        string(name: 'project_namespace', defaultValue: 'monitoring', description: 'project namespace', trim: true),
        string(name: 'backup_name', defaultValue: 'backup_name1', description: 'project namespace', trim: true),

        string(name: 'asg_instance_types', defaultValue: 'm5.xlarge', description: 'List of EC2 shapes to be used in cluster provisioning', trim: true),
        ])
])

String tfWorkDir = 'terraform/rancher/RDS-for-get-dump'
String tfVars = ''
String cluster_name = params.rancher_cluster_name
terraform_output = ''
String postgresql_backups_directory = "rds"
String db_backup_name = params.backup_name
user=""
password=""
database_name=""
port=""
endpoint=""


ansiColor('xterm') {
    if (params.refreshParameters) {
        currentBuild.result = 'ABORTED'
        error('DRY RUN BUILD, NO STAGE IS ACTIVE!')
    }
    node(params.agent) {
        try {
            stage('Ini') {
                buildName cluster_name + '.' + env.BUILD_ID
                buildDescription "action: ${params.action}\n"
            }
            stage('TF vars') {
               // tfVars += terraform.generateTfVar('admin_users', Constants.AWS_ADMIN_USERS)
               // tfVars += terraform.generateTfVar('projectID', Constants.AWS_PROJECT_ID)
                           }
            stage('Checkout') {
                checkout scm
            }
            withCredentials([
                [$class           : 'AmazonWebServicesCredentialsBinding',
                 credentialsId    : Constants.AWS_CREDENTIALS_ID,
                 accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                 secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                [$class           : 'AmazonWebServicesCredentialsBinding',
                 credentialsId    : Constants.AWS_CREDENTIALS_ID,
                 accessKeyVariable: 'TF_VAR_aws_access_key_id',
                 secretKeyVariable: 'TF_VAR_aws_secret_access_key'],
                [$class           : 'AmazonWebServicesCredentialsBinding',
                 credentialsId    : Constants.KUBECOST_AWS_CREDENTIALS_ID,
                 accessKeyVariable: 'TF_VAR_aws_kubecost_access_key_id',
                 secretKeyVariable: 'TF_VAR_aws_kubecost_secret_access_key'],
                string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key'),
                string(credentialsId: Constants.KUBECOST_LICENSE_KEY, variable: 'TF_VAR_kubecost_licence_key')
            ]) {
                docker.image(Constants.TERRAFORM_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
                    terraform.tfInit(tfWorkDir, '')
                    terraform.tfWorkspaceSelect(tfWorkDir, "copy-rds-data-to-kubernetes")
                    terraform.tfStatePull(tfWorkDir)
                    if (params.action == 'apply') {
                        terraform.tfPlan(tfWorkDir, tfVars)
                        //terraform.tfPlanApprove(tfWorkDir)
                        terraform.tfApply(tfWorkDir)
                        terraform_output=terraform.tfOutput(tfWorkDir, '')
                        user=terraform.tfOutputValue(tfWorkDir, 'user')
                        password=terraform.tfOutputValue(tfWorkDir, 'password')
                        database_name=terraform.tfOutputValue(tfWorkDir, 'database_name')
                        port=terraform.tfOutputValue(tfWorkDir, 'port')
                        endpoint=terraform.tfOutputValue(tfWorkDir, 'endpoint')
                    } else if (params.action == 'destroy') {
                        input message: "Are you shure that you want to destroy ?"
                        terraform.tfDestroy(tfWorkDir, tfVars)
                    }
                }
                stage('pslq') {

                    println terraform_output

                    println "${user}  ${password} ${database_name} ${port} ${endpoint}"

                    helm.k8sClient {
                        psqlDumpMethods.configureKubectl(Constants.AWS_REGION, params.rancher_cluster_name)
                        psqlDumpMethods.configureHelm(Constants.FOLIO_HELM_HOSTED_REPO_NAME, Constants.FOLIO_HELM_HOSTED_REPO_URL)
                        try {

                            psqlDumpMethods.backupRDSHelmInstall(env.BUILD_ID, Constants.FOLIO_HELM_HOSTED_REPO_NAME,
                                Constants.PSQL_DUMP_HELM_CHART_NAME, Constants.PSQL_RDS_DUMP_HELM_INSTALL_CHART_VERSION,
                                params.project_namespace, params.rancher_cluster_name, db_backup_name,
                                "s3://" + Constants.PSQL_DUMP_BACKUPS_BUCKET_NAME, postgresql_backups_directory,
                                endpoint.replaceAll("\"",""), user.replaceAll("\"",""),
                                password.replaceAll("\"",""), database_name.replaceAll("\"",""),
                                AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
                            )

                            psqlDumpMethods.helmDelete(env.BUILD_ID, params.project_namespace)
                            println("\n\n\n" + "\033[32m" + "PostgreSQL backup process SUCCESSFULLY COMPLETED\nYou can find your backup in AWS s3 bucket folio-postgresql-backups/" +
                                "${params.rancher_cluster_name}/${params.rancher_project_name}/${db_backup_name}" + "\n\n\n" + "\033[0m")

                        }
                        catch (exception) {
                            psqlDumpMethods.helmDelete(env.BUILD_ID, params.project_namespace)
                            println("\n\n\n" + "\033[1;31m" + "PostgreSQL backup/restore process was FAILED!!!\nPlease, check logs and try again.\n\n\n" + "\033[0m")
                            throw exception
                        }
                    }
                }
            }
        } catch (exception) {
            println(exception)
            error(exception.getMessage())
        } finally {
            stage('Cleanup') {
                cleanWs notFailBuild: true
            }
        }
    }
}
