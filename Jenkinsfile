node('local') {
    try {

        stage('Init') {
            tool 'JDK 8u102'
            tool 'sbt 0.13.15'
            tool 'Apache Maven 3.3.9'
        }

        timeout(45) {
            stage('Checkout') {
                checkout scm
                //sh 'git submodule update --init --recursive'
            }
        }

        stage('Build') {
            sh "${tool name: 'sbt 0.13.15', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'}/bin/sbt clean compile test"
        }

        stage('Results') {
            junit '**/test-output/junitreports/*.xml'
        }

        if (currentBuild.result == null || "SUCCESS" == currentBuild.result) {
            currentBuild.result = "SUCCESS"
            slackSend(color: '#00FF00', message: "${currentBuild.result}: Job '${env.JOB_NAME} #${env.BUILD_NUMBER}' (<${env.BUILD_URL}|Open>)", channel: '#biopet-bot', teamDomain: 'lumc', tokenCredentialId: 'lumc')
        } else {
            slackSend(color: '#FFFF00', message: "${currentBuild.result}: Job '${env.JOB_NAME} #${env.BUILD_NUMBER}' (<${env.BUILD_URL}|Open>)", channel: '#biopet-bot', teamDomain: 'lumc', tokenCredentialId: 'lumc')
        }
    } catch (e) {
        if (currentBuild.result == null || "FAILED" == currentBuild.result) {
            currentBuild.result = "FAILED"
        }
        slackSend(color: '#FF0000', message: "${currentBuild.result}: Job '${env.JOB_NAME} #${env.BUILD_NUMBER}' (<${env.BUILD_URL}|Open>)", channel: '#biopet-bot', teamDomain: 'lumc', tokenCredentialId: 'lumc')

        throw e
    }

}