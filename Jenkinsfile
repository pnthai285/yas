pipeline {
    agent any

    environment {
        // Tạm thời cố định service product để test Yêu cầu 5
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
                dir("${SERVICE_NAME}") {
                    // Chạy test và ép Maven sinh ra file jacoco.xml
                    sh 'mvn clean verify'
                }
            }
            post {
                always {
                    // 1. Upload Test Result (Vẫn giữ nguyên JUnit)
                    junit '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
                    
                    // 2. Upload Code Coverage bằng Coverage Plugin
                    // Chúng ta chỉ định công cụ đọc là 'jacoco' và đường dẫn trỏ tới file XML
                    recordCoverage(
                        tools: [[tool: 'jacoco', pattern: '**/target/site/jacoco/jacoco.xml']]
                    )
                }
            }
        }

        stage('Build Artifact') {
            steps {
                dir("${SERVICE_NAME}") {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }
    }
}
