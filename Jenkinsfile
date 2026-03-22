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
// 2. HÀM PHÁT HIỆN FILE THAY ĐỔI
// ============================================================================
def getChangedFiles() {
    def files = [] as List
    
    if (env.CHANGE_TARGET) {                       // PR build
        sh "git fetch origin ${env.CHANGE_TARGET}:refs/remotes/origin/${env.CHANGE_TARGET} --no-tags"
        def raw = sh(script: "git diff --name-only origin/${env.CHANGE_TARGET}...HEAD", returnStdout: true).trim()
        if (raw) files.addAll(raw.split('\n') as List)
        return files
    }
    
    currentBuild.changeSets.each { changeSet ->
        changeSet.each { entry -> files.addAll(entry.affectedPaths) }
    }
    if (files) return files
    
    if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        def raw = sh(script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT} ${env.GIT_COMMIT}", returnStdout: true).trim()
        if (raw) files.addAll(raw.split('\n') as List)
    }
    if (files) return files
    
    try {
        def raw = sh(script: 'git diff --name-only HEAD~1 HEAD', returnStdout: true).trim()
        if (raw) files.addAll(raw.split('\n') as List)
    } catch (e) { echo "⚠️ No previous commit (first build?)" }
    
    return files
}

// ============================================================================
// 3. HÀM XỬ LÝ BACKEND SERVICE
// ============================================================================
def runBackendService(String service) {
    stage("Backend: ${service}") {
        def localRepo = "${env.WORKSPACE}/.m2-repo-${service}"
        sh "mkdir -p ${localRepo} && cp -al ${env.WORKSPACE}/.m2-cache/. ${localRepo}/ || true"
        
        // Snyk scan (sửa lỗi auth)
        withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
            sh """
                snyk test --file=${service}/pom.xml \
                    --severity-threshold=high \
                    --maven-aggregate-project \
                    --token=\$SNYK_TOKEN || true
            """
        }
        
        // Unit & Integration Tests
        retry(2) {
            sh "mvn verify -pl ${service} -am -B -Dmaven.test.failure.ignore=false -Dmaven.repo.local=${localRepo} -DforkCount=1"
        }
        
        // Publish test results
        junit testResults: "${service}/target/surefire-reports/*.xml, ${service}/target/failsafe-reports/*.xml", allowEmptyResults: true
        
        // Coverage gate (≥70%)
        recordCoverage(
            tools: [[parser: 'JACOCO', pattern: "${service}/target/site/jacoco/jacoco.xml"]],
            sourceDirectories: [[path: "${service}/src/main/java"]],
            qualityGates: [
                [threshold: 70.0, metric: 'LINE', baseline: 'PROJECT', criticality: 'FAILURE']
            ]
        )
        
        // SonarQube analysis (không dùng pullrequest.* vì Community Edition)
        withSonarQubeEnv('SonarQube') {
            def sonarParams = "-Dsonar.projectKey=${env.SONAR_BASE_KEY}-${service} -Dsonar.projectName=YAS-${service}"
            sonarParams += " -Dsonar.branch.name=${env.BRANCH_NAME}"
            sonarParams += " -Dsonar.coverage.jacoco.xmlReportPaths=${service}/target/site/jacoco/jacoco.xml"
            sh "mvn sonar:sonar -pl ${service} -am -B ${sonarParams} -Dmaven.repo.local=${localRepo}"
        }
        
        // Wait for Quality Gate
        timeout(time: 5, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }
        
        // Build JAR (optional)
        // sh "mvn package -pl ${service} -am -B -DskipTests -Dmaven.repo.local=${localRepo}"
        // dir(service) { sh "docker build -t yas-${service}:${BUILD_ID} ." }
    }
}

// ============================================================================
// 4. HÀM XỬ LÝ FRONTEND SERVICE
// ============================================================================
def runFrontendService(String service) {
    stage("Frontend: ${service}") {
        dir(service) {
            sh 'npm ci --prefer-offline --no-audit'
            sh 'npm run test -- --coverage --coverageReporters="json-summary" --coverageReporters="lcov" --reporters=jest-junit'
            sh 'npm run build'
        }
        junit testResults: "${service}/junit.xml", allowEmptyResults: true
        
        def covFile = "${service}/coverage/coverage-summary.json"
        if (fileExists(covFile)) {
            def coverage = sh(script: "jq '.total.lines.pct' ${covFile}", returnStdout: true).trim()
            if (coverage.toDouble() < 70.0) {
                error "❌ Frontend coverage ${coverage}% < 70%"
            }
        }
        
        // SonarQube cho frontend (không dùng pullrequest.*)
        withSonarQubeEnv('SonarQube') {
            def scannerHome = tool name: 'SonarScanner'
            sh """
                ${scannerHome}/bin/sonar-scanner \
                    -Dsonar.projectKey=${env.SONAR_BASE_KEY}-${service} \
                    -Dsonar.projectName=YAS-${service} \
                    -Dsonar.branch.name=${env.BRANCH_NAME} \
                    -Dsonar.sources=${service}/src \
                    -Dsonar.javascript.lcov.reportPaths=${service}/coverage/lcov.info \
                    -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/coverage/**
            """
        }
        timeout(time: 5, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }
        
        // Snyk scan frontend
        withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
            sh """
                snyk test --file=package.json \
                    --severity-threshold=high \
                    --token=\$SNYK_TOKEN || true
            """
        }
        
        // Build Docker image (optional)
        // dir(service) { sh "docker build -t yas-${service}:${BUILD_ID} ." }
    }
}

// ============================================================================
// 5. PIPELINE CHÍNH
// ============================================================================
node('jenkins-agent') {
    env.MIN_COVERAGE = '70'
    env.SONAR_BASE_KEY = 'my-yas'
    // env.SONAR_HOST_URL không cần vì đã cấu hình trong Jenkins SonarQube server
    
    try {
        stage('Checkout Code') { checkout scm }
        
        stage('Smart Impact Analysis') {
            def allChangedFiles = getChangedFiles()
            def skipPatterns = [/^README\.md$/, /^\.gitignore$/, /^Jenkinsfile$/, /^\.github\/.*/, /^docs\/.*/, /^\.git\/.*/]
            def onlyTrivial = !allChangedFiles.isEmpty() && allChangedFiles.every { file -> skipPatterns.any { pattern -> file ==~ pattern } }
            
            if (onlyTrivial) {
                echo "✅ Only trivial changes. Skipping build."
                skipBuild = true
                currentBuild.result = 'SUCCESS'
                return
            }
            
            if (allChangedFiles.isEmpty()) {
                changedBackend = BACKEND_SERVICES
                changedFrontend = FRONTEND_SERVICES
            } else {
                def globalChanged = allChangedFiles.any { it == 'pom.xml' || it.startsWith('common-library/') }
                changedBackend = BACKEND_SERVICES.findAll { svc -> globalChanged || allChangedFiles.any { it.startsWith("${svc}/") } }
                changedFrontend = FRONTEND_SERVICES.findAll { svc -> allChangedFiles.any { it.startsWith("${svc}/") } }
            }
            changedBackend.remove('common-library')
            echo "Backend services to build: ${changedBackend ?: 'none'}"
            echo "Frontend services to build: ${changedFrontend ?: 'none'}"
        }
        
        // Gitleaks scan
        if (!skipBuild && (changedBackend || changedFrontend)) {
            stage('Security: Gitleaks') {
                script {
                    def configFlag = fileExists('.gitleaks.toml') ? '--config .gitleaks.toml' : (fileExists('gitleaks.toml') ? '--config gitleaks.toml' : '')
                    def logOpts = env.CHANGE_TARGET ? "--log-opts='origin/${env.CHANGE_TARGET}..HEAD'" : (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ? "--log-opts='${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}'" : '')
                    sh "gitleaks detect --source=. ${logOpts} --verbose --exit-code=1 ${configFlag}"
                }
            }
        }
        
        // Pre-build common-library
        if (!skipBuild && (changedBackend || getChangedFiles().any { it.startsWith('common-library/') })) {
            stage('Pre-build Dependencies') {
                def mavenCache = "${env.WORKSPACE}/.m2-cache"
                sh "rm -rf common-library/target || true"
                sh "mvn install -N -B -DskipTests -q -Dmaven.repo.local=${mavenCache}"
                sh "mvn install -pl common-library -am -B -DskipTests -Dmaven.repo.local=${mavenCache}"
            }
        }
        
        // Backend CI (song song)
        if (!skipBuild && changedBackend) {
            stage('Backend CI') {
                def parallelTasks = [:]
                changedBackend.each { svc ->
                    parallelTasks[svc] = { runBackendService(svc) }
                }
                parallel parallelTasks
            }
        }
        
        // Frontend CI (theo lô)
        if (!skipBuild && changedFrontend) {
            stage('Frontend CI') {
                def batches = changedFrontend.collate(2)
                batches.eachWithIndex { batch, idx ->
                    def parallelTasks = [:]
                    batch.each { svc ->
                        parallelTasks[svc] = { runFrontendService(svc) }
                    }
                    echo "🚀 Batch Frontend #${idx+1}: ${batch}"
                    parallel parallelTasks
                }
            }
        }
        
    } catch (Exception e) {
        currentBuild.result = 'FAILURE'
        throw e
    } finally {
        stage('Cleanup') {
            sh "rm -rf ${env.WORKSPACE}/.m2-cache || true"
            changedBackend.each { svc -> sh "rm -rf ${env.WORKSPACE}/.m2-repo-${svc} || true" }
            cleanWs()
            if (currentBuild.result == 'SUCCESS' && !skipBuild) {
                echo "✅ Pipeline SUCCESS!"
            } else if (!skipBuild) {
                echo "❌ Pipeline FAILED!"
            }
        }
    }
}
