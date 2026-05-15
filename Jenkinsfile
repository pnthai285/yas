pipeline {
    // ============================================================
    // 1. AGENT & OPTIONS
    // ============================================================
    agent none

    options {
        retry(3)                                           // Master retry khi Spot bị reclaim
        timeout(time: 60, unit: 'MINUTES')                 // Bảo vệ zombie pipeline
        disableConcurrentBuilds()                          // Tránh lãng phí spot agent
        buildDiscarder(logRotator(numToKeepStr: '10'))     // Giữ 10 build gần nhất
        timestamps()                                       // Log có timestamp
        githubCommitStatus(context: 'continuous-integration/jenkins/pr-head')
    }

    // ============================================================
    // 2. ENVIRONMENT VARIABLES
    // ============================================================
    environment {
        BRANCH_NAME        = "${env.BRANCH_NAME}"
        CHANGE_ID          = "${env.CHANGE_ID}"
        CHANGE_TARGET      = "${env.CHANGE_TARGET ?: 'main'}"
        GIT_COMMIT_SHORT   = "${env.GIT_COMMIT.take(7)}"
        
        // SonarCloud project keys (đã tạo trên UI)
        PROJECT_KEYS = '''
            backoffice:pnthai285_yas-backoffice
            backoffice-bff:pnthai285_yas-backoffice-bff
            cart:pnthai285_yas-cart
            common-library:pnthai285_yas-common-library
            customer:pnthai285_yas-customer
            delivery:pnthai285_yas-delivery
            inventory:pnthai285_yas-inventory
            location:pnthai285_yas-location
            media:pnthai285_yas-media
            order:pnthai285_yas-order
            payment:pnthai285_yas-payment
            payment-paypal:pnthai285_yas-payment-paypal
            product:pnthai285_yas-product
            promotion:pnthai285_yas-promotion
            rating:pnthai285_yas-rating
            recommendation:pnthai285_yas-recommendation
            sampledata:pnthai285_yas-sampledata
            search:pnthai285_yas-search
            storefront:pnthai285_yas-storefront
            storefront-bff:pnthai285_yas-storefront-bff
            tax:pnthai285_yas-tax
            webhook:pnthai285_yas-webhook
        '''
    }

    // ============================================================
    // 3. STAGES (Smart Routing + Gitleaks trên master)
    // ============================================================
    stages {
        stage('Smart Routing') {
            agent { label 'master' }
            steps {
                script {
                    echo "[INFO] Fetching target branch: ${CHANGE_TARGET}"
                    sh "git fetch origin ${CHANGE_TARGET}:refs/remotes/origin/${CHANGE_TARGET}"
                    
                    def changedFiles = sh(
                        script: "git diff --name-only origin/${CHANGE_TARGET}...HEAD",
                        returnStdout: true
                    ).trim().split('\n')
                    
                    // Lấy danh sách module (chứa pom.xml hoặc package.json)
                    def allModules = sh(
                        script: "find . -maxdepth 2 \\( -name 'pom.xml' -o -name 'package.json' \\) -printf '%h\\n' | sed 's|^\\./||' | grep -v '^.$' | sort -u",
                        returnStdout: true
                    ).trim().split('\n')
                    
                    def affected = []
                    def commonLibChanged = false
                    changedFiles.each { file ->
                        if (file.startsWith('common-library/')) commonLibChanged = true
                        def module = allModules.find { m -> file.startsWith(m + '/') || file == m }
                        if (module && !affected.contains(module)) affected << module
                    }
                    
                    env.AFFECTED_MODULES = affected.join(',')
                    env.COMMON_LIB_CHANGED = commonLibChanged.toString()
                    env.SHOULD_BUILD = (affected.size() > 0 || commonLibChanged) ? 'true' : 'false'
                    
                    echo "[RESULT] Affected modules: ${env.AFFECTED_MODULES}"
                    echo "[RESULT] Common lib changed: ${env.COMMON_LIB_CHANGED}"
                    echo "[RESULT] Should build: ${env.SHOULD_BUILD}"
                }
            }
        }

        stage('Gitleaks Scan') {
            when { expression { env.SHOULD_BUILD == 'true' } }
            agent { label 'master' }
            steps {
                script {
                    sh """
                        if ! command -v gitleaks &> /dev/null; then
                            echo "[INFO] Installing gitleaks binary..."
                            curl -sL https://github.com/gitleaks/gitleaks/releases/latest/download/gitleaks-linux-amd64 -o gitleaks
                            chmod +x gitleaks
                            sudo mv gitleaks /usr/local/bin/
                        fi
                        echo "[INFO] Running Gitleaks..."
                        gitleaks detect --source=. --verbose --redact
                    """
                }
                echo "[OK] Gitleaks passed."
            }
        }

        // Nếu không có thay đổi, skip toàn bộ heavy build
        stage('Skip Heavy Build') {
            when { expression { env.SHOULD_BUILD == 'false' } }
            steps {
                echo "[INFO] No changes in any module. Skipping heavy build."
                currentBuild.result = 'SUCCESS'
                // Không dùng return, pipeline sẽ tự kết thúc vì không còn stage nào khác
                // Để đảm bảo, ta dùng error để exit? Không, chỉ cần set result và không làm gì thêm.
            }
        }

        // ============================================================
        // 4. HEAVY BUILD TRÊN SPOT AGENT (NVMe)
        // ============================================================
        stage('Heavy Build') {
            when { expression { env.SHOULD_BUILD == 'true' } }
            agent { label 'aws-spot-nvme' }
            
            stages {
                stage('Checkout & Setup') {
                    steps {
                        checkout scm
                        script {
                            try {
                                env.ACCOUNT_ID = sh(script: 'aws sts get-caller-identity --query Account --output text', 
                                                   returnStdout: true).trim()
                                env.CACHE_BUCKET = "yas-cache-${env.ACCOUNT_ID}"
                                env.HUB_IP = sh(
                                    script: "aws ssm get-parameter --name /yas/hub/private_ip --region us-east-1 --query Parameter.Value --output text",
                                    returnStdout: true
                                ).trim()
                                env.REGISTRY = "${env.HUB_IP}:5000"
                                echo "[INFO] Local registry: ${env.REGISTRY}"
                                echo "[INFO] Cache bucket: ${env.CACHE_BUCKET}"
                            } catch (Exception e) {
                                error "❌ Failed to get AWS credentials or SSM parameter: ${e.message}. Check IAM role on Spot Agent."
                            }
                        }
                    }
                }

                stage('Verify Registry Connectivity') {
                    steps {
                        sh """
                            echo "[INFO] Testing registry connection to ${env.HUB_IP}:5000"
                            curl -f http://${env.HUB_IP}:5000/v2/_catalog || {
                                echo "[ERROR] Registry not reachable!"
                                exit 1
                            }
                            echo "[OK] Registry is accessible"
                        """
                    }
                }

                stage('Restore Maven Cache') {
                    steps {
                        script {
                            // Cache key bao gồm cả pom.xml và package.json
                            def cacheHash = sh(
                                script: """
                                    find . \\( -name 'pom.xml' -o -name 'package.json' \\) -exec md5sum {} + | md5sum | awk '{print \$1}'
                                """,
                                returnStdout: true
                            ).trim()
                            env.CACHE_KEY = cacheHash
                            echo "[INFO] Cache key: ${env.CACHE_KEY}"
                            sh """
                                set +e
                                aws s3 cp s3://${CACHE_BUCKET}/maven/${BRANCH_NAME}-${CACHE_KEY}.tar.gz ./cache.tar.gz
                                if [ \$? -eq 0 ]; then
                                    # Verify integrity (optional checksum)
                                    aws s3 cp s3://${CACHE_BUCKET}/maven/${BRANCH_NAME}-${CACHE_KEY}.tar.gz.md5 ./cache.tar.gz.md5 2>/dev/null
                                    if [ -f cache.tar.gz.md5 ]; then
                                        echo "[INFO] Verifying cache checksum..."
                                        md5sum -c cache.tar.gz.md5 || { echo "❌ Cache corrupt!"; exit 1; }
                                    else
                                        echo "[WARN] No checksum file, skipping verification"
                                    fi
                                    tar -xzf cache.tar.gz -C ~/.m2
                                    echo "[OK] Restored branch cache"
                                else
                                    aws s3 cp s3://${CACHE_BUCKET}/maven/main-${CACHE_KEY}.tar.gz ./cache.tar.gz
                                    if [ \$? -eq 0 ]; then
                                        tar -xzf cache.tar.gz -C ~/.m2
                                        echo "[OK] Restored main cache"
                                    else
                                        echo "[INFO] No cache found, starting fresh"
                                    fi
                                fi
                                rm -f cache.tar.gz cache.tar.gz.md5
                            """
                        }
                    }
                }

                stage('Build & Test') {
                    steps {
                        script {
                            def modules = env.AFFECTED_MODULES ? env.AFFECTED_MODULES.split(',') : []
                            if (modules.isEmpty() && env.COMMON_LIB_CHANGED == 'true') {
                                modules = PROJECT_KEYS.readLines().collect { it.split(':')[0] }
                                echo "[INFO] Common lib changed, will build all modules"
                            }
                            
                            // Tách riêng frontend và backend
                            def frontendModules = modules.findAll { it in ['backoffice', 'storefront'] }
                            def backendModules = modules.findAll { it !in ['backoffice', 'storefront'] }
                            
                            // 1. Frontend
                            frontendModules.each { module ->
                                echo "[INFO] Building frontend module: ${module}"
                                dir(module) {
                                    sh """
                                        npm ci --prefer-offline --no-audit
                                        npm run build
                                        npm test -- --coverage --watchAll=false
                                    """
                                }
                            }
                            
                            // 2. Backend Maven reactor (một lệnh duy nhất)
                            if (backendModules.size() > 0) {
                                def usesJava21 = backendModules.any { it.contains('automation-ui') }
                                def javaHome = usesJava21 
                                    ? '/usr/lib/jvm/java-21-amazon-corretto'
                                    : '/usr/lib/jvm/java-25-amazon-corretto'
                                def mvnCmd = "/opt/maven/bin/mvn clean verify -T 1C -pl ${backendModules.join(',')}"
                                if (env.CHANGE_ID) mvnCmd += " -fae"
                                if (env.COMMON_LIB_CHANGED == 'true') mvnCmd += " -amd"
                                
                                sh """
                                    export JAVA_HOME=${javaHome}
                                    export PATH=${javaHome}/bin:\$PATH
                                    ${mvnCmd}
                                """
                            }
                        }
                    }
                }

                stage('SonarQube Analysis') {
                    when {
                        anyOf {
                            branch 'main'
                            expression { env.CHANGE_ID != null }
                        }
                    }
                    steps {
                        script {
                            def modules = env.AFFECTED_MODULES ? env.AFFECTED_MODULES.split(',') : []
                            if (modules.isEmpty() && env.COMMON_LIB_CHANGED == 'true') {
                                modules = PROJECT_KEYS.readLines().collect { it.split(':')[0] }
                            }
                            modules.each { module ->
                                if (fileExists("${module}/pom.xml")) {
                                    def projectKey = PROJECT_KEYS.find { it.startsWith("${module}:") }?.split(':')?[1]
                                    if (!projectKey) {
                                        echo "[WARN] No Sonar project key for module: ${module}, skipping"
                                        return
                                    }
                                    withSonarQubeEnv('sonarcloud') {
                                        echo "[INFO] Analyzing module: ${module} -> ${projectKey}"
                                        sh """
                                            /opt/maven/bin/mvn sonar:sonar \
                                                -pl ${module} \
                                                -Dsonar.projectKey=${projectKey} \
                                                -Dsonar.projectName=${module} \
                                                -Dsonar.sources=${module}/src/main/java \
                                                -Dsonar.tests=${module}/src/test/java \
                                                -Dsonar.java.binaries=${module}/target/classes \
                                                -Dsonar.coverage.jacoco.xmlReportPaths=${module}/target/site/jacoco/jacoco.xml
                                        """
                                    }
                                } else {
                                    echo "[INFO] Skipping Sonar for non-Java module: ${module}"
                                }
                            }
                        }
                    }
                }

                stage('Quality Gate') {
                    when { expression { env.CHANGE_ID != null } }
                    steps {
                        timeout(time: 15, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }

                stage('Snyk Security Scan') {
                    when {
                        anyOf {
                            branch 'main'
                            expression { env.CHANGE_ID != null }
                        }
                    }
                    steps {
                        timeout(time: 10, unit: 'MINUTES') {  // Thêm timeout cho Snyk
                            withCredentials([string(credentialsId: 'snyk-api-token-yas', variable: 'SNYK_TOKEN')]) {
                                script {
                                    def modules = env.AFFECTED_MODULES ? env.AFFECTED_MODULES.split(',') : []
                                    if (modules.isEmpty() && env.COMMON_LIB_CHANGED == 'true') {
                                        modules = PROJECT_KEYS.readLines().collect { it.split(':')[0] }
                                    }
                                    modules.each { module ->
                                        def scanDir = (module == 'common-library') ? '.' : module
                                        // Xác định đường dẫn tuyệt đối để mount
                                        def scanPath = (scanDir == '.') ? "${WORKSPACE}" : "${WORKSPACE}/${scanDir}"
                                        sh """
                                            echo "[INFO] Running Snyk scan for module: ${module} (${scanPath})"
                                            docker run --rm \
                                                -v ${scanPath}:/app \
                                                -e SNYK_TOKEN=\${SNYK_TOKEN} \
                                                snyk/snyk:alpine \
                                                snyk test --severity-threshold=high --fail-on=all
                                        """
                                    }
                                }
                            }
                        }
                    }
                }

                stage('Build & Push Docker Image') {
                    when { branch 'main' }
                    steps {
                        script {
                            def modules = env.AFFECTED_MODULES ? env.AFFECTED_MODULES.split(',') : []
                            modules.each { module ->
                                def dockerfilePath = "${module}/Dockerfile"
                                if (!fileExists(dockerfilePath)) {
                                    echo "[WARN] No Dockerfile for ${module}, skipping image build"
                                    return
                                }
                                def immutableTag = "yas-${module}:${env.GIT_COMMIT_SHORT}"
                                echo "[INFO] Building image for module: ${module}"
                                
                                if (module in ['backoffice', 'storefront']) {
                                    sh """
                                        docker build -f ${dockerfilePath} \
                                            -t ${env.REGISTRY}/${immutableTag} .
                                        docker push ${env.REGISTRY}/${immutableTag}
                                    """
                                } else {
                                    dir(module) {
                                        sh """
                                            docker build -t ${env.REGISTRY}/${immutableTag} .
                                            docker push ${env.REGISTRY}/${immutableTag}
                                        """
                                    }
                                }
                                def mutableTag = "yas-${module}:${env.BRANCH_NAME.replace('/', '-')}-${env.GIT_COMMIT_SHORT}"
                                sh """
                                    docker tag ${env.REGISTRY}/${immutableTag} ${env.REGISTRY}/${mutableTag}
                                    docker push ${env.REGISTRY}/${mutableTag}
                                """
                                echo "[OK] Pushed ${immutableTag}"
                            }
                        }
                    }
                }

                stage('GitOps Handoff (Template)') {
                    when { branch 'main' }
                    steps {
                        script {
                            def modules = env.AFFECTED_MODULES ? env.AFFECTED_MODULES.split(',') : []
                            echo "=================================================="
                            echo "[GITOPS HANDOFF] Khi có repo GitOps, thay thế placeholder này bằng:"
                            echo "1. Clone repo yas-gitops-config (dùng lock để tránh conflict)"
                            echo "2. Update deployment.yaml với immutable tag: ${env.GIT_COMMIT_SHORT}"
                            echo "3. Commit và push (git pull --rebase trước push)"
                            echo ""
                            echo "Affected modules:"
                            modules.each { module ->
                                echo "  - ${module}: image = ${env.REGISTRY}/yas-${module}:${env.GIT_COMMIT_SHORT}"
                            }
                            echo "=================================================="
                            // Mẫu implement khi có repo:
                            // lock('gitops-repo') {
                            //     retry(3) {
                            //         withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
                            //             sh """
                            //                 git clone https://x-access-token:${GITHUB_TOKEN}@github.com/your-org/yas-gitops-config.git
                            //                 cd yas-gitops-config
                            //                 # update yaml files
                            //                 git add .
                            //                 git commit -m "Update images to ${GIT_COMMIT_SHORT}"
                            //                 git pull --rebase origin main
                            //                 git push origin main
                            //             """
                            //         }
                            //     }
                            // }
                        }
                    }
                }

                stage('Save Maven Cache') {
                    when { expression { currentBuild.result == null || currentBuild.result == 'SUCCESS' } }
                    steps {
                        sh """
                            echo "[INFO] Saving cache for branch ${BRANCH_NAME}"
                            tar -czf cache.tar.gz -C ~/.m2 repository
                            aws s3 cp cache.tar.gz s3://${CACHE_BUCKET}/maven/${BRANCH_NAME}-${CACHE_KEY}.tar.gz
                            # Optionally create checksum
                            md5sum cache.tar.gz | awk '{print \$1}' > cache.tar.gz.md5
                            aws s3 cp cache.tar.gz.md5 s3://${CACHE_BUCKET}/maven/${BRANCH_NAME}-${CACHE_KEY}.tar.gz.md5
                            rm -f cache.tar.gz cache.tar.gz.md5
                            echo "[OK] Cache saved"
                        """
                    }
                }
            }
        }

        // Stage để đo lường thời gian pipeline (optional)
        stage('Pipeline Metrics') {
            steps {
                script {
                    def duration = currentBuild.duration / 1000
                    echo "⏱️ Pipeline completed in ${duration} seconds"
                    // Có thể gửi metrics về CloudWatch/Grafana nếu cần
                }
            }
        }
    }

    // ============================================================
    // 5. POST ACTIONS
    // ============================================================
    post {
        always {
            // Archive test results
            junit allowEmptyResults: true,
                  testResults: '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
            // Archive coverage reports
            archiveArtifacts artifacts: '**/target/site/jacoco/jacoco.xml, **/coverage/coverage-final.json',
                         allowEmptyArchive: true
            cleanWs()
            echo "[INFO] Workspace cleaned."
        }
        failure {
            // Thông báo Slack (nếu đã cấu hình)
            slackSend channel: '#ci-cd',
                      color: 'danger',
                      message: "❌ Pipeline FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}\nBranch: ${env.BRANCH_NAME}\nCommit: ${env.GIT_COMMIT_SHORT}"
            echo "[ERROR] Pipeline failed. Check logs."
        }
        success {
            slackSend channel: '#ci-cd',
                      color: 'good',
                      message: "✅ Pipeline PASSED: ${env.JOB_NAME} #${env.BUILD_NUMBER}\nBranch: ${env.BRANCH_NAME}"
            echo "[SUCCESS] Pipeline completed."
        }
    }
}