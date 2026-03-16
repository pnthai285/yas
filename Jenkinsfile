pipeline {

    /*
    agent any:
    Jenkins có thể chạy pipeline trên bất kỳ node/agent nào available
    */
    agent any


    /*
    tools:
    Khai báo các tool Jenkins đã cấu hình trong Global Tool Configuration
    Jenkins sẽ tự add chúng vào PATH khi pipeline chạy
    */
    tools {
        maven 'maven-3.9'   // Maven version đã cài trong Jenkins
        jdk 'jdk-25'        // JDK đã cài trong Jenkins
    }


    /*
    environment:
    Khai báo biến môi trường dùng chung cho toàn pipeline
    */
    environment {

        /*
        SERVICE_NAME:
        service sẽ được build/test
        Ở requirement 5 ta chỉ cần test 1 service
        ví dụ: product
        */
        SERVICE_NAME = 'product'
    }



    stages {

        /*
        ========================
        Stage 1: Checkout Source
        ========================
        */
        stage('Checkout') {

            steps {

                /*
                checkout scm:
                Jenkins clone source code từ GitHub repo
                branch được trigger pipeline
                */
                checkout scm
            }
        }



        /*
        ========================
        Stage 2: Test + Coverage
        ========================

        Phase test của requirement 5
        */
        stage('Test & Coverage') {

            steps {

                /*
                mvn command explanation

                -pl ${SERVICE_NAME}
                build module cụ thể (product)

                -am
                build luôn các module dependency
                ví dụ:
                product -> depends -> common-library

                clean
                xóa build cũ

                verify
                chạy:
                - compile
                - unit test
                - integration test
                - jacoco coverage
                */
                sh """
                mvn -pl ${SERVICE_NAME} -am clean verify
                """
            }


            /*
            post section:
            chạy sau khi stage kết thúc
            */
            post {

                always {

                    /*
                    junit step:
                    upload test result lên Jenkins UI

                    Jenkins sẽ đọc các file XML được
                    Maven Surefire/Failsafe tạo ra
                    */
                    junit(
                        allowEmptyResults: true,
                        testResults: '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
                    )


                    /*
                    recordCoverage:
                    upload code coverage từ JaCoCo

                    file coverage được tạo tại:
                    target/site/jacoco/jacoco.xml
                    */
                    recordCoverage(
                        tools: [
                            [
                                parser: 'JACOCO',
                                pattern: '**/target/site/jacoco/jacoco.xml'
                            ]
                        ]
                    )
                }
            }
        }



        /*
        ========================
        Stage 3: Build Artifact
        ========================

        Phase build của requirement 5
        */
        stage('Build Artifact') {

            steps {

                /*
                mvn package:
                build jar artifact

                -DskipTests:
                skip test vì test đã chạy ở stage trước
                */
                sh """
                mvn -pl ${SERVICE_NAME} -am clean package -DskipTests
                """
            }
        }

    }
}
