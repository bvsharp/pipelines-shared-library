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
        string(name: 'custom_cluster_name', defaultValue: '', description: 'Custom cluster name (Will override rancher_cluster_name)', trim: true),
        string(name: 'asg_instance_types', defaultValue: 'm5.xlarge', description: 'List of EC2 shapes to be used in cluster provisioning', trim: true),
        ])
])

String tfWorkDir = 'terraform/rancher/RDS-for-get-dump'
String tfVars = ''
String cluster_name = params.custom_cluster_name.isEmpty() ? params.rancher_cluster_name : params.custom_cluster_name


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
                        terraform.tfPlanApprove(tfWorkDir)
                        terraform.tfApply(tfWorkDir)
                    } else if (params.action == 'destroy') {
                        input message: "Are you shure that you want to destroy ?"
                        //terraform.tfRemoveElastic(tfWorkDir)
                        terraform.tfDestroy(tfWorkDir, tfVars)
                    }
                }
                stage('pslq') {

                    sh 'psql --version'
                    sh 'pg_dump -d postgres://folio:Gu9201rbh*12-@tf-20230302122957213300000001.cdxr1geeeqbb.us-west-2.rds.amazonaws.com:5432/folio > 1111.txt'
                    sh 'cat 1111.txt'
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
