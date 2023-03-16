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
        string(name: 'Snapshot_id', defaultValue: 'arn:aws:rds:us-west-2:732722833398:cluster-snapshot:nbf-bugfest-snapshot-11-07-2022',
            description: 'arn of snapshot', trim: true),
        string(name: 'backup_name', defaultValue: 'backup_name1', description: 'project namespace', trim: true),
        string(name: 'pg_password', defaultValue: '', description: 'database password', trim: true),
        string(name: 'kubernetice_secret_name', defaultValue: 'default-token-cxpdl', description: 'Used for escape error in container if we run it in namespace where we do not have these secrets s3CredentialsSecret and dbCredentialsSecret ', trim: true),
        string(name: 'asg_instance_types', defaultValue: 'db.r5.xlarge', description: 'DB instance type', trim: true),
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
               tfVars += terraform.generateTfVar('arn_db_snapshot', params.Snapshot_id)
               tfVars += terraform.generateTfVar('pg_password', params.pg_password)
               tfVars += terraform.generateTfVar('asg_instance_types', params.asg_instance_types)
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
                string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key')
            ]) {
                docker.image(Constants.TERRAFORM_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
                    terraform.tfInit(tfWorkDir, '')
                    terraform.tfWorkspaceSelect(tfWorkDir, "copy-rds-data-to-kubernetes")
                    terraform.tfStatePull(tfWorkDir)
                    if (params.action == 'apply') {
                        terraform.tfPlan(tfWorkDir, tfVars)
                        terraform.tfApply(tfWorkDir)
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
            }
            stage('Backup Connection') {
                println"PGPASSWORD=${password} psql -h ${endpoint} --port=${port} -U ${user} --dbname=${database_name}"
                /// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                if (params.action == 'plan') {
                    helm.k8sClient {
                        psqlDumpMethods.configureKubectl(Constants.AWS_REGION, params.rancher_cluster_name)
                        psqlDumpMethods.configureHelm(Constants.FOLIO_HELM_HOSTED_REPO_NAME, Constants.FOLIO_HELM_HOSTED_REPO_URL)
                        try {
                            psqlDumpMethods.backupRDSHelmInstall(
                                env.BUILD_ID,
                                Constants.FOLIO_HELM_HOSTED_REPO_NAME,
                                Constants.PSQL_DUMP_HELM_CHART_NAME,
                                Constants.PSQL_RDS_DUMP_HELM_INSTALL_CHART_VERSION,
                                params.project_namespace,
                                params.rancher_cluster_name,
                                db_backup_name,
                                "s3://" + Constants.PSQL_DUMP_BACKUPS_BUCKET_NAME,
                                postgresql_backups_directory,
                                endpoint.replaceAll("\"", ""),
                                user.replaceAll("\"", ""),
                                password.replaceAll("\"", ""),
                                database_name.replaceAll("\"", ""),
                                params.kubernetice_secret_name,
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

                    withCredentials([
                        [$class           : 'AmazonWebServicesCredentialsBinding',
                         credentialsId    : Constants.AWS_CREDENTIALS_ID,
                         accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                         secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
                        [$class           : 'AmazonWebServicesCredentialsBinding',
                         credentialsId    : Constants.AWS_CREDENTIALS_ID,
                         accessKeyVariable: 'TF_VAR_aws_access_key_id',
                         secretKeyVariable: 'TF_VAR_aws_secret_access_key'],
                        string(credentialsId: Constants.RANCHER_TOKEN_ID, variable: 'TF_VAR_rancher_token_key')
                    ]) {
                        docker.image(Constants.TERRAFORM_DOCKER_CLIENT).inside("-u 0:0 --entrypoint=") {
                            terraform.tfInit(tfWorkDir, '')
                            terraform.tfWorkspaceSelect(tfWorkDir, "copy-rds-data-to-kubernetes")
                            terraform.tfStatePull(tfWorkDir)
                            terraform.tfDestroy(tfWorkDir, tfVars)

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

