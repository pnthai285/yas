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
// 2. HÀM PHÁT HIỆN THAY ĐỔI 
// ============================================================================
def getChangedFiles() {
    def files = [] as List

    // Nếu là Pull Request, so sánh với base branch
    if (env.CHANGE_TARGET) {
        echo "🔍 PR detected: comparing with origin/${env.CHANGE_TARGET}"
        sh "git fetch origin ${env.CHANGE_TARGET}:refs/remotes/origin/${env.CHANGE_TARGET} --no-tags"
        def raw = sh(script: "git diff --name-only origin/${env.CHANGE_TARGET}...HEAD", returnStdout: true).trim()
        if (raw) {
            files.addAll(raw.split('\n') as List)
            echo "📂 PR changed files (diff): ${files.size()} files"
        } else {
            echo "⚠️ No files changed in PR diff."
        }
        return files
    }

    // Không phải PR: dùng changeSets (Jenkins native)
    currentBuild.changeSets.each { changeSet ->
        changeSet.each { entry ->
            files.addAll(entry.affectedPaths)
        }
    }
    if (files) {
        echo "📂 ChangeSets: ${files.size()} files"
        return files
    }

    // Fallback 1: GIT_PREVIOUS_SUCCESSFUL_COMMIT
    if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        def raw = sh(script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT} ${env.GIT_COMMIT}", returnStdout: true).trim()
        if (raw) {
            files.addAll(raw.split('\n') as List)
            echo "📂 Previous commit diff: ${files.size()} files"
            return files
        }
    }

    // Fallback 2: HEAD~1
    try {
        def raw = sh(script: 'git diff --name-only HEAD~1 HEAD', returnStdout: true).trim()
        if (raw) {
            files.addAll(raw.split('\n') as List)
            echo "📂 HEAD~1 diff: ${files.size()} files"
            return files
        }
    } catch (e) {
        echo "⚠️ No previous commit (first build?)"
    }

    echo "⚠️ No changes detected, will build all services."
    return files
}

// ============================================================================
// 3. HÀM BUILD BACKEND SERVICE (giữ nguyên)
// ============================================================================
def runBackendService(String service) {
    node('jenkins-agent') {
        stage("Backend: ${service}") {
            script {
                def localRepo = "${env.WORKSPACE}/.m2-repo-${service}"
                sh """
                    mkdir -p ${localRepo}
                    cp -al ${env.WORKSPACE}/.m2-cache/. ${localRepo}/ || true
                """
                retry(2) {
                    sh """
                        mvn clean verify -pl ${service} -am -B \
                            -Dmaven.test.failure.ignore=false \
                            -Dmaven.repo.local=${localRepo} \
                            -DforkCount=1
                    """
                }
                junit testResults: "${service}/target/surefire-reports/*.xml, ${service}/target/failsafe-reports/*.xml", allowEmptyResults: true
                recordCoverage(
                    tools: [[parser: 'JACOCO', pattern: "${service}/target/site/jacoco/jacoco.xml"]],
                    sourceDirectories: [[path: "${service}/src/main/java"]],
                    qualityGates: [
                        [threshold: Double.parseDouble(env.MIN_COVERAGE), metric: 'LINE', baseline: 'PROJECT', criticality: 'FAILURE'],
                        [threshold: Double.parseDouble(env.MIN_COVERAGE), metric: 'BRANCH', baseline: 'PROJECT', criticality: 'FAILURE']
                    ]
                )
                withSonarQubeEnv('SonarQube') {
                    def sonarParams = "-Dsonar.host.url=${env.SONAR_HOST} " +
                                      "-Dsonar.projectKey=${env.SONAR_BASE_KEY}-${service} " +
                                      "-Dsonar.projectName=YAS-${service}"
                    if (env.CHANGE_ID) {
                        sonarParams += " -Dsonar.pullrequest.key=${env.CHANGE_ID} " +
                                       "-Dsonar.pullrequest.branch=${env.CHANGE_BRANCH} " +
                                       "-Dsonar.pullrequest.base=${env.CHANGE_TARGET}"
                    } else {
                        sonarParams += " -Dsonar.branch.name=${env.BRANCH_NAME}"
                    }
                    sh "mvn sonar:sonar -pl ${service} -am -B ${sonarParams} -Dmaven.repo.local=${localRepo}"
                }
                timeout(time: 5, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }
                withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                    sh "snyk auth \$SNYK_TOKEN"
                    sh "snyk test --file=${service}/pom.xml --severity-threshold=high"
                }
                sh "mvn package -pl ${service} -am -B -Dmaven.test.skip=true -Dmaven.repo.local=${localRepo}"
                dir(service) {
                    def dockerTag = "yas-${service}:${BUILD_ID}"
                    sh "docker build --build-arg BUILDKIT_INLINE_CACHE=1 -t ${dockerTag} ."
                    if (env.CHANGE_ID == null) {
                        sh "docker tag ${dockerTag} yas-${service}:latest"
                    }
                }
                archiveArtifacts artifacts: "${service}/target/*.jar", allowEmptyArchive: true
            }
        }
    }
}

// ============================================================================
// 4. HÀM BUILD FRONTEND SERVICE (giữ nguyên)
// ============================================================================
def runFrontendService(String service) {
    node('jenkins-agent') {
        stage("Frontend: ${service}") {
            script {
                dir(service) {
                    sh 'npm ci --prefer-offline --no-audit'
                    sh 'npm run test -- --coverage --reporters=jest-junit'
                    sh 'npm run build'
                }
                junit testResults: "${service}/junit.xml", allowEmptyResults: true
                def covFile = "${service}/coverage/coverage-summary.json"
                if (fileExists(covFile)) {
                    def coverage = sh(script: "jq '.total.lines.pct' ${covFile}", returnStdout: true).trim()
                    if (coverage.toDouble() < env.MIN_COVERAGE.toDouble()) {
                        error "❌ Frontend coverage ${coverage}% < ${env.MIN_COVERAGE}%"
                    }
                }
                withSonarQubeEnv('SonarQube') {
                    def scannerHome = tool name: 'SonarScanner'
                    sh """
                        ${scannerHome}/bin/sonar-scanner \
                            -Dsonar.projectKey=${env.SONAR_BASE_KEY}-${service} \
                            -Dsonar.projectName=YAS-${service} \
                            -Dsonar.host.url=${env.SONAR_HOST} \
                            -Dsonar.sources=${service}/src \
                            -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/coverage/**
                    """
                }
                timeout(time: 5, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }
                withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                    sh "snyk auth \$SNYK_TOKEN"
                    sh "snyk test --file=package.json --severity-threshold=high"
                }
                def dockerTag = "yas-${service}:${BUILD_ID}"
                sh "docker build --build-arg BUILDKIT_INLINE_CACHE=1 -t ${dockerTag} ."
                if (env.CHANGE_ID == null) {
                    sh "docker tag ${dockerTag} yas-${service}:latest"
                }
            }
        }
    }
}

// ============================================================================
// 5. PIPELINE CHÍNH
// ============================================================================
node('jenkins-agent') {
    env.MIN_COVERAGE = '70'
    env.SONAR_HOST = 'http://172.31.46.99:9000'   // Thay bằng IP SonarQube server của bạn
    env.SONAR_BASE_KEY = 'my-yas'

    try {
        stage('Checkout') {
            checkout scm
        }

        stage('Smart Impact Analysis') {
            script {
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

                if (changedBackend.contains('common-library')) {
                    changedBackend.remove('common-library')
                }
                echo "🔍 Backend services to build: ${changedBackend ?: 'none'}"
                echo "🔍 Frontend services to build: ${changedFrontend ?: 'none'}"
            }
        }

        if (!skipBuild && (changedBackend.size() > 0 || changedFrontend.size() > 0)) {
            stage('Security: Gitleaks') {
                script {
                    def gitleaksPath = "${env.WORKSPACE}/gitleaks-bin/gitleaks"
                    if (!fileExists(gitleaksPath)) {
                        sh '''
                            mkdir -p gitleaks-bin
                            curl -sSfL https://github.com/gitleaks/gitleaks/releases/download/v8.22.1/gitleaks_8.22.1_linux_x64.tar.gz | tar xz -C gitleaks-bin/
                            chmod +x gitleaks-bin/gitleaks
                        '''
                    }
                    if (fileExists('.gitleaksignore')) {
                        sh "${gitleaksPath} detect --source=. --no-git --verbose --exit-code=1 --gitleaks-ignore-path .gitleaksignore"
                    } else {
                        sh "${gitleaksPath} detect --source=. --no-git --verbose --exit-code=1"
                    }
                }
            }

            if (changedBackend.size() > 0 || (getChangedFiles().any { it.startsWith('common-library/') })) {
                stage('Pre-build Dependencies') {
                    script {
                        def mavenCache = "${env.WORKSPACE}/.m2-cache"
                        sh """
                            mvn install -N -B -Dmaven.test.skip=true -q -Dmaven.repo.local=${mavenCache}
                            mvn install -pl common-library -am -B -Dmaven.test.skip=true -q -Dmaven.repo.local=${mavenCache}
                        """
                    }
                }
            }

            if (changedBackend.size() > 0) {
                stage('Backend CI (Parallel)') {
                    def parallelBackend = [:]
                    changedBackend.each { service ->
                        parallelBackend[service] = { runBackendService(service) }
                    }
                    parallel parallelBackend
                }
            }

            if (changedFrontend.size() > 0) {
                stage('Frontend CI (Parallel)') {
                    def parallelFrontend = [:]
                    changedFrontend.each { service ->
                        parallelFrontend[service] = { runFrontendService(service) }
                    }
                    parallel parallelFrontend
                }
            }
        }

    } catch (Exception e) {
        currentBuild.result = 'FAILURE'
        throw e
    } finally {
        stage('Cleanup') {
            script {
                sh "rm -rf ${env.WORKSPACE}/.m2-cache || true"
                if (changedBackend) {
                    changedBackend.each { svc ->
                        sh "rm -rf ${env.WORKSPACE}/.m2-repo-${svc} || true"
                    }
                }
            }
            cleanWs()
        }
        if (currentBuild.result == 'SUCCESS') {
            echo '✅ CI Pipeline completed successfully!'
        } else {
            echo '❌ CI Pipeline failed. Check logs for details.'
        }
    }
}
