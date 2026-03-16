pipeline {
    // Jenkins có thể chạy pipeline trên bất kỳ agent nào
    agent any

    // Khai báo tool được cấu hình trong Jenkins Global Tool Configuration
    tools {
        // Maven dùng để build project
        maven 'maven-3.9'

        // JDK dùng để compile và chạy test
        jdk 'jdk-25'
    }

    environment {
        // Service cần test (ví dụ product)
        SERVICE_NAME = 'product'

        // Thư viện dùng chung của toàn hệ thống
        COMMON_LIBRARY = 'common-library'
    }

    stages {

        // =========================================================
        // 1. Checkout source code từ Git
        // =========================================================
        stage('Checkout') {
            steps {
                // Lấy code của branch hiện tại
                checkout scm
            }
        }

        // =========================================================
        // 2. Install parent pom
        // =========================================================
        stage('Install Parent POM') {
            steps {
                // Repo YAS có root pom (artifactId = yas)
                // Các service con (product, cart...) đều kế thừa từ parent này
                //
                // Nếu không install parent pom vào local .m2
                // Maven sẽ cố download từ Maven Central → build fail
                //
                // -N = non-recursive (chỉ build pom root)
                sh 'mvn -N install'
            }
        }

        // =========================================================
        // 3. Build common library
        // =========================================================
        stage('Build Common Library') {
            steps {

                // Chuyển vào thư mục common-library
                dir("${COMMON_LIBRARY}") {

                    // Build và install thư viện vào local Maven repository
                    // để các service khác có thể sử dụng
                    //
                    // -DskipTests để tiết kiệm thời gian CI
                    sh 'mvn clean install -DskipTests'
                }
            }
        }

        // =========================================================
        // 4. Test & Code Coverage
        // =========================================================
        stage('Test & Coverage') {
            steps {

                // Chạy test cho service cần build
                dir("${SERVICE_NAME}") {

                    // verify sẽ chạy:
                    // - compile
                    // - unit test
                    // - integration test
                    // - generate JaCoCo coverage report
                    sh 'mvn clean verify'
                }
            }

            post {
                always {

                    // -------------------------------------------------
                    // Upload Test Results lên Jenkins
                    // -------------------------------------------------
                    // Jenkins sẽ đọc file XML từ Surefire/Failsafe
                    junit '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'

                    // -------------------------------------------------
                    // Upload Code Coverage (JaCoCo)
                    // -------------------------------------------------
                    // Plugin "Coverage" sẽ hiển thị % coverage trên Jenkins UI
                    recordCoverage(
                        tools: [
                            [tool: 'jacoco', pattern: '**/target/site/jacoco/jacoco.xml']
                        ]
                    )
                }
            }
        }

        // =========================================================
        // 5. Build Artifact
        // =========================================================
        stage('Build Artifact') {
            steps {

                dir("${SERVICE_NAME}") {

                    // Build file jar của service
                    // -DskipTests vì test đã chạy ở stage trước
                    sh 'mvn clean package -DskipTests'
                }
            }
        }
    }
}
