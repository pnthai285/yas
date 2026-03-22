// ============================================================================
// 1. CẤU HÌNH BIẾN TOÀN CỤC
// ============================================================================
def BACKEND_SERVICES = [
    'common-library', 'backoffice-bff', 'cart', 'customer', 'delivery',
    'inventory', 'location', 'media', 'order', 'payment', 'payment-paypal',
    'product', 'promotion', 'rating', 'recommendation', 'sampledata',
    'search', 'storefront-bff', 'tax', 'webhook'
]
def FRONTEND_SERVICES = ['storefront', 'backoffice']

def changedBackend = []
def changedFrontend = []
def skipBuild = false

// ============================================================================
// 2. HÀM PHÁT HIỆN THAY ĐỔI (HỖ TRỢ CHO PR & MULTIBRANCH)
// ============================================================================
def getChangedFiles() {
    def files = [] as List

    // Nếu là Pull Request, so sánh với base branch
    if (env.CHANGE_TARGET) {
        echo "🔍 PR detected: comparing with origin/${env.CHANGE_TARGET}"
        sh "git fetch origin ${env.CHANGE_TARGET}:refs/remotes/origin/${env.CHANGE_TARGET} --no-tags"
        def raw = sh(script: "git diff --name-only origin/${env.CHANGE_TARGET}...HEAD", returnStdout: true).trim()
        if (raw) files.addAll(raw.split('\n') as List)
        return files
    }

    // Không phải PR: dùng changeSets của Jenkins
    currentBuild.changeSets.each { changeSet ->
        changeSet.each { entry -> files.addAll(entry.affectedPaths) }
    }
    if (files) return files

    // Fallback 1: Lấy thay đổi từ commit thành công trước đó
    if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        def raw = sh(script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT} ${env.GIT_COMMIT}", returnStdout: true).trim()
        if (raw) files.addAll(raw.split('\n') as List)
    }
    if (files) return files

    // Fallback 2: Lấy thay đổi của commit cuối cùng
    try {
        def raw = sh(script: 'git diff --name-only HEAD~1 HEAD', returnStdout: true).trim()
        if (raw) files.addAll(raw.split('\n') as List)
    } catch (e) { echo "⚠️ No previous commit (first build?)" }

    return files
}

// ============================================================================
// 3. HÀM BUILD BACKEND SERVICE 
// ============================================================================
def runBackendService(String service) {
    stage("Backend: ${service}") {
        def localRepo = "${env.WORKSPACE}/.m2-repo-${service}"
        sh """
            mkdir -p ${localRepo}
            cp -al ${env.WORKSPACE}/.m2-cache/. ${localRepo}/ || true
        """
        
        // 3.1. Test & Phân tích Coverage (Bắt buộc Coverage >= 80%)
        retry(2) {
            sh """
                mvn clean verify -pl ${service} -am -B \
                    -Dmaven.test.failure.ignore=false \
                    -Dmaven.repo.local=${localRepo} \
                    -DforkCount=1
            """
        }
        
        // Upload Test Result
        junit testResults: "${service}/target/surefire-reports/*.xml, ${service}/target/failsafe-reports/*.xml", allowEmptyResults: true
        
        // Upload Coverage & Kiểm tra Threshold 80%
        recordCoverage(
            tools: [[parser: 'JACOCO', pattern: "${service}/target/site/jacoco/jacoco.xml"]],
            sourceDirectories: [[path: "${service}/src/main/java"]],
            qualityGates: [
                [threshold: env.MIN_COVERAGE.toDouble(), metric: 'LINE', baseline: 'PROJECT', criticality: 'FAILURE'],
                [threshold: env.MIN_COVERAGE.toDouble(), metric: 'BRANCH', baseline: 'PROJECT', criticality: 'FAILURE']
            ]
        )

        // 3.2. Quét SonarQube
        withSonarQubeEnv('SonarQube') {
            def sonarParams = "-Dsonar.projectKey=${env.SONAR_BASE_KEY}-${service} -Dsonar.projectName=YAS-${service}"
            if (env.CHANGE_ID) {
                sonarParams += " -Dsonar.pullrequest.key=${env.CHANGE_ID} -Dsonar.pullrequest.branch=${env.CHANGE_BRANCH} -Dsonar.pullrequest.base=${env.CHANGE_TARGET}"
            } else {
                sonarParams += " -Dsonar.branch.name=${env.BRANCH_NAME}"
            }
            sh "mvn sonar:sonar -pl ${service} -am -B ${sonarParams} -Dmaven.repo.local=${localRepo}"
        }
        
        // Chờ Quality Gate (yêu cầu cấu hình Webhook trên SonarQube trỏ về Jenkins)
        timeout(time: 5, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }

        // 3.3. Quét Snyk
        withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
            sh "snyk auth \$SNYK_TOKEN"
            sh "snyk test --file=${service}/pom.xml --severity-threshold=high"
        }

        // 3.4. Đóng gói & Build Docker Image
        sh "mvn package -pl ${service} -am -B -Dmaven.test.skip=true -Dmaven.repo.local=${localRepo}"
        dir(service) {
            def dockerTag = "yas-${service}:${BUILD_ID}"
            sh "docker build --build-arg BUILDKIT_INLINE_CACHE=1 -t ${dockerTag} ."
            if (env.CHANGE_ID == null && env.BRANCH_NAME == 'main') {
                sh "docker tag ${dockerTag} yas-${service}:latest"
            }
        }
        archiveArtifacts artifacts: "${service}/target/*.jar", allowEmptyArchive: true
    }
}

// ============================================================================
// 4. HÀM BUILD FRONTEND SERVICE 
// ============================================================================
def runFrontendService(String service) {
    stage("Frontend: ${service}") {
        dir(service) {
            // 4.1. Test & Coverage
            sh 'npm ci --prefer-offline --no-audit'
            sh 'npm run test -- --coverage --reporters=jest-junit'
            sh 'npm run build'
        }
        
        junit testResults: "${service}/junit.xml", allowEmptyResults: true
        
        // Kiểm tra Threshold 80% bằng jq
        def covFile = "${service}/coverage/coverage-summary.json"
        if (fileExists(covFile)) {
            def coverage = sh(script: "jq '.total.lines.pct' ${covFile}", returnStdout: true).trim()
            if (coverage.toDouble() < env.MIN_COVERAGE.toDouble()) {
                error "❌ Frontend coverage ${coverage}% < ${env.MIN_COVERAGE}%"
            }
        }

        // 4.2. Quét SonarQube
        withSonarQubeEnv('SonarQube') {
            def scannerHome = tool name: 'SonarScanner'
            sh """
                ${scannerHome}/bin/sonar-scanner \
                    -Dsonar.projectKey=${env.SONAR_BASE_KEY}-${service} \
                    -Dsonar.projectName=YAS-${service} \
                    -Dsonar.sources=${service}/src \
                    -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/coverage/**
            """
        }
        timeout(time: 5, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }

        // 4.3. Quét Snyk
        withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
            sh "snyk auth \$SNYK_TOKEN"
            sh "snyk test --file=${service}/package.json --severity-threshold=high"
        }

        // 4.4. Build Docker Image
        dir(service) {
            def dockerTag = "yas-${service}:${BUILD_ID}"
            sh "docker build --build-arg BUILDKIT_INLINE_CACHE=1 -t ${dockerTag} ."
            if (env.CHANGE_ID == null && env.BRANCH_NAME == 'main') {
                sh "docker tag ${dockerTag} yas-${service}:latest"
            }
        }
    }
}

// ============================================================================
// 5. PIPELINE CHÍNH
// ============================================================================
node('jenkins-agent') {
    env.MIN_COVERAGE = '80'
    env.SONAR_BASE_KEY = 'my-yas' 

    try {
        stage('Checkout') {
            checkout scm
        }

        stage('Smart Impact Analysis') {
            def allChangedFiles = getChangedFiles()
            def skipPatterns = [
                /^README\.md$/, /^\.gitignore$/, /^Jenkinsfile$/,
                /^\.github\/.*/, /^docs\/.*/, /^\.git\/.*/
            ]
            def onlyTrivial = !allChangedFiles.isEmpty() && allChangedFiles.every { file ->
                skipPatterns.any { pattern -> file ==~ pattern }
            }

            if (onlyTrivial) {
                echo "✅ Only documentation/config changes. Skipping full build."
                skipBuild = true
                currentBuild.result = 'SUCCESS'
                return
            }

            if (allChangedFiles.isEmpty()) {
                echo "⚠️ No changes detected (first build?). Building all."
                changedBackend = BACKEND_SERVICES
                changedFrontend = FRONTEND_SERVICES
            } else {
                def globalChanged = allChangedFiles.any { it == 'pom.xml' || it.startsWith('common-library/') }
                changedBackend = BACKEND_SERVICES.findAll { svc ->
                    globalChanged || allChangedFiles.any { it.startsWith("${svc}/") }
                }
                changedFrontend = FRONTEND_SERVICES.findAll { svc ->
                    allChangedFiles.any { it.startsWith("${svc}/") }
                }
            }

            changedBackend.remove('common-library') // Sẽ build riêng ở stage Pre-build
            echo "🔍 Backend services to build: ${changedBackend ?: 'none'}"
            echo "🔍 Frontend services to build: ${changedFrontend ?: 'none'}"
        }

        // 🛡️ Security: Gitleaks (chạy trực tiếp và dùng allowlist/ignore)
        if (!skipBuild && (changedBackend.size() > 0 || changedFrontend.size() > 0)) {
            stage('Security: Gitleaks') {
                script {
                    // Tự động nhận diện cấu hình toml (có chấm hoặc không chấm)
                    def configFlag = ""
                    if (fileExists('.gitleaks.toml')) {
                        configFlag = "--config .gitleaks.toml"
                    } else if (fileExists('gitleaks.toml')) {
                        configFlag = "--config gitleaks.toml"
                    }
                    
                    // Lệnh chạy: Gitleaks sẽ tự động tìm thấy file .gitleaksignore ở thư mục hiện tại để bỏ qua False Positives
                    sh "gitleaks detect --source=. --no-git --verbose --exit-code=1 ${configFlag}"
                }
            }
        }

        // 📦 Pre-build Dependencies
        if (!skipBuild && (changedBackend.size() > 0 || (getChangedFiles().any { it.startsWith('common-library/') }))) {
            stage('Pre-build Dependencies') {
                def mavenCache = "${env.WORKSPACE}/.m2-cache"
                sh """
                    mvn install -N -B -Dmaven.test.skip=true -q -Dmaven.repo.local=${mavenCache}
                    mvn install -pl common-library -am -B -Dmaven.test.skip=true -q -Dmaven.repo.local=${mavenCache}
                """
            }
        }

        // 🚀 Thực thi song song (Parallel Backend)
        if (!skipBuild && changedBackend.size() > 0) {
            stage('Backend CI (Parallel)') {
                def parallelBackend = [:]
                changedBackend.each { service ->
                    parallelBackend[service] = { runBackendService(service) }
                }
                parallel parallelBackend
            }
        }

        // 🚀 Thực thi song song (Parallel Frontend)
        if (!skipBuild && changedFrontend.size() > 0) {
            stage('Frontend CI (Parallel)') {
                def parallelFrontend = [:]
                changedFrontend.each { service ->
                    parallelFrontend[service] = { runFrontendService(service) }
                }
                parallel parallelFrontend
            }
        }

    } catch (Exception e) {
        currentBuild.result = 'FAILURE'
        throw e
    } finally {
        stage('Cleanup') {
            sh "rm -rf ${env.WORKSPACE}/.m2-cache || true"
            if (changedBackend) {
                changedBackend.each { svc ->
                    sh "rm -rf ${env.WORKSPACE}/.m2-repo-${svc} || true"
                }
            }
            cleanWs()
            if (currentBuild.result == 'SUCCESS' && !skipBuild) {
                echo '✅ CI Pipeline completed successfully!'
            } else if (!skipBuild) {
                echo '❌ CI Pipeline failed. Check logs for details.'
            }
        }
    }
}
