import groovy.json.JsonSlurper

pipeline {
  agent any
  
  stages {
    stage('Call GitLab IaS Job') {
      steps {
        script {
        
          def yourProjectID = "44253509"
          def yourGiblabToken = "glpat-dCHhnx8e6fJsjTNLZxAA"  
          
          def iasJobID = "3993993127"
          
          def gitlabProjectUrl = "https://gitlab.com/api/v4/projects/${yourProjectID}/"
          def gitlabToken = yourGiblabToken
          
          def gitlabPipelineParams = [
            'ref': 'master'
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
    }
  }
}
