import groovy.json.JsonSlurper

pipeline {
  agent any
  
  environment {
        GITLAB_URL = "https://gitlab.com"
        GITLAB_REPOSITORY_URL = "https://gitlab.com/pyang6771211/jenkins-demo.git"
        GITLAB_CREDENTIAL_ID = <Your Gitlab credential with username/password>
        GITLAB_PROJECT_ID = "44253509"
        GITLAB_PROJECT_NAME = "jenkins-demo"
        GIT_BRANCH = "master"
        GITLAB_TOKEN = <Your gitlab token>
        GITLAB_IAS_JOB_ID = <The id of the Gitlab job to be executed>
        
        KUBE_CREDENTIAL_ID = <the credential id for the Kubernetes cluster>
        KUBE_SERVER_URL = <The url of the Kubernetes cluster>
        KUBE_YAML_FILE = "jenkins-demo.yaml"
        KUBE_NAMESPACE = <The namespace to deploy this application>
        
        IMAGE_REPOSITORY_USERNAME = <your username for image repository>
        IMAGE_REPOSITORY_PASSWD = <your password for image repository>
        
    }
  
  stages {
    stage('Call GitLab IaS Job') {
      steps {
        script {
        
          def yourProjectID = GITLAB_PROJECT_ID
          def gitlabToken = GITLAB_TOKEN  
          
          def iasJobID = GITLAB_IAS_JOB_ID
          
          def gitlabProjectUrl = "${GITLAB_URL}/api/v4/projects/${yourProjectID}/"
          
          def gitlabPipelineParams = [
            'ref': '${GIT_BRANCH}'
          ]
          
          def iasJobPlayUrl = gitlabProjectUrl + "jobs/${iasJobID}/play"
          
          def iasJobResponse = httpRequest url: iasJobPlayUrl, acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_FORM', customHeaders: [[name: 'PRIVATE-TOKEN', value: gitlabToken]], httpMode: 'POST'
          
          if (iasJobResponse.status != 200) {
            error("Failed to trigger GitLab job: ${iasJobResponse.status}")
          } else {
             
            def gitlabStatusUrl = gitlabProjectUrl + "jobs/${iasJobID}" 
            def gitlabStatusResponse = null
            def gitlabStatus = null
            
            for (int i = 0; i < 60; i++) {

              gitlabStatusResponse = httpRequest url: gitlabStatusUrl, acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_FORM', customHeaders: [[name: 'PRIVATE-TOKEN', value: gitlabToken]], httpMode: 'GET'

              json = new JsonSlurper().parseText(gitlabStatusResponse.content)
              echo "GitLab status response: ${json}"
              gitlabStatus = json.status
              echo "GitLab job status: ${gitlabStatus}"
              json=null
              
              if (gitlabStatus == 'success') {

                break
              } else if (gitlabStatus == 'failed' || gitlabStatus == 'canceled') {
                
                error("GitLab pipeline failed with status ${gitlabStatus}")
              }
              
              sleep(10)
            }
            
          } 
             def gitlabJobLogUrl = gitlabProjectUrl + "jobs/${iasJobID}/trace"
             def gitlabJobArtifactUrl = gitlabProjectUrl + "jobs/${iasJobID}/artifacts" 
             
             echo ("-------------------------------logs for job ${iasJobID}}: ================================")
             def response = sh "curl --header \"PRIVATE-TOKEN: ${gitlabToken}\" \"${gitlabJobLogUrl}\""
             response = null
                
             echo ("-------------------------------Downloading artifact for job ${iasJobID}}: ================================")
             response = sh "curl --location --output ${iasJobID}.zip --header \"PRIVATE-TOKEN: ${gitlabToken}\" \"${gitlabJobArtifactUrl}\""
          }  
        }
      }
      
       stage('Checkout') {
            steps {
                // Checkout code from Git repository
              checkout([
                    $class: 'GitSCM',
                    branches: [[name: "${GIT_BRANCH}"]],
                    extensions: [[$class: 'CloneOption', depth: 1]],
                    userRemoteConfigs: [[
                        url: "${GITLAB_REPOSITORY_URL}",
                        credentialsId: "${GITLAB_CREDENTIAL_ID}"
                    ]]
                ])
            }
        }
        
        stage('Build docker image and push into repository') {
            steps {
                // Build container image using Dockerfile
                sh './mvnw -Djib.to.auth.username="${IMAGE_REPOSITORY_USERNAME}" -Djib.to.auth.password="${IMAGE_REPOSITORY_PASSWD}" compile jib:build'
            }
        }
        
        stage('Deploy') {
            steps {
                // Deploy container image in Kubernetes cluster
                sh 'curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"'
                sh 'chmod a+x ./kubectl'
                withKubeConfig([credentialsId: "${KUBE_CREDENTIAL_ID}", serverUrl: "${KUBE_SERVER_URL}"]) {
                    sh './kubectl apply -f ${KUBE_YAML_FILE} -n ${KUBE_NAMESPACE}'
                }
            }
        }    
    }
  }

