pipeline {
    agent any

    // Khai báo công cụ Maven đã cài trên Jenkins
    tools {
        maven 'maven-3.9'
    }

    environment {
        // Cố định service product để test Yêu cầu 5
        SERVICE_NAME = 'product'
        // Thêm biến thư viện dùng chung
        COMMON_LIBRARY = 'common-library' 
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Common Library') {
            steps {
                dir("${COMMON_LIBRARY}") {
                    // Chạy lệnh install để đẩy thư viện vào local cache của Jenkins (.m2)
                    // Dùng -DskipTests để tiết kiệm thời gian vì  không cần test thư viện ở bước này
                    sh 'mvn clean install -DskipTests'
                }
            }
        }
        // ------------------------------

        stage('Test & Coverage') {
            steps {
                dir("${SERVICE_NAME}") {
                    // Bây giờ Jenkins đã có common-library, lệnh này sẽ chạy trơn tru
                    sh 'mvn clean verify'
                }
            }
            post {
                always {
                    // 1. Upload Test Result
                    junit '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
                    
                    // 2. Upload Code Coverage bằng Coverage Plugin
                    recordCoverage(tools: [[tool: 'jacoco', pattern: '**/target/site/jacoco/jacoco.xml']])
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
