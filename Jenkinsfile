pipeline {
    agent any

    tools {
        maven 'maven-3.9'
        jdk 'jdk-25'
    }

    environment {
        SERVICE_NAME = 'product'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test & Coverage') {
            steps {
                sh """
                mvn -pl ${SERVICE_NAME} -am clean test
                """
            }

            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/surefire-reports/*.xml'

                    recordCoverage(
                        tools: [[parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']]
                    )
                }
            }
        }

        stage('Build Artifact') {
            steps {
                sh """
                mvn -pl ${SERVICE_NAME} -am package -DskipTests
                """
            }
        }
    }
}
