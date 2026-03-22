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
// 2. HÀM HỖ TRỢ: PHÁT HIỆN THAY ĐỔI (3 TIER FALLBACK)
// ============================================================================
def getChangedFiles(currentBuild, env) {
    def files = []

    // Method 1: Jenkins changeSets (most reliable for PRs and pushes)
    currentBuild.changeSets.each { changeSet ->
        changeSet.each { entry -> files.addAll(entry.affectedPaths) }
    }

    // Method 2: GIT_PREVIOUS_SUCCESSFUL_COMMIT (for rebuilds)
    if (files.isEmpty() && env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        def raw = sh(script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT} ${env.GIT_COMMIT}", returnStdout: true).trim()
        files = raw ? raw.split('\n') : []
    }

    // Method 3: HEAD~1 (fallback for first build)
    if (files.isEmpty()) {
        try {
            def raw = sh(script: 'git diff --name-only HEAD~1 HEAD', returnStdout: true).trim()
            files = raw ? raw.split('\n') : []
        } catch (e) { echo "⚠️ No previous commit; will build all." }
    }

    return files
}

// ============================================================================
// 3. PIPELINE CHÍNH
// ============================================================================
pipeline {
    agent { label 'jenkins-agent' } // Use dedicated agent

    tools {
        jdk 'jdk-25'                // JDK 21 (compatible with Spring Boot 3.2)
        maven 'maven-3'             // Maven 3.9+
        nodejs 'nodejs-20'          // Node.js 20 for frontend
        snyk 'snyk-cli'             // Snyk CLI tool
    }

    environment {
        // JVM tuning for Maven (keep memory low, avoid OOM)
        MAVEN_OPTS = '-Xmx512m -XX:+TieredCompilation -XX:TieredStopAtLevel=1'
        // Node.js memory limit (1GB)
        NODE_OPTIONS = '--max-old-space-size=1024'
        // Docker BuildKit for faster builds
        DOCKER_BUILDKIT = '1'
        // Testcontainers configuration (to work with Docker socket)
        TESTCONTAINERS_RYUK_DISABLED = 'true'
        TESTCONTAINERS_HOST_OVERRIDE = 'host.docker.internal'
        DOCKER_HOST = 'unix:///var/run/docker.sock'

        // SonarQube server (internal)
        SONAR_HOST = 'http://172.31.46.99:9000'
        SONAR_BASE_KEY = 'my-yas'
        MIN_COVERAGE = '70'

        // Use a local Maven cache for pre-built dependencies (hardlink later)
        MAVEN_CACHE = "${WORKSPACE}/.m2-cache"
    }

    options {
        skipDefaultCheckout(true)
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 90, unit: 'MINUTES')
        parallelsAlwaysFailFast()
    }

    stages {
        // --------------------------------------------------------------------
        // STAGE: CHECKOUT
        // --------------------------------------------------------------------
        stage('Checkout') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: scm.branches,
                    userRemoteConfigs: scm.userRemoteConfigs,
                    extensions: scm.extensions + [
                        [$class: 'CloneOption', shallow: false, noTags: true, timeout: 60]
                    ]
                ])
            }
        }

        // --------------------------------------------------------------------
        // STAGE: IMPACT ANALYSIS (Detect changed services, skip trivial)
        // --------------------------------------------------------------------
        stage('Smart Impact Analysis') {
            steps {
                script {
                    def allChangedFiles = getChangedFiles(currentBuild, env)

                    // Skip if only docs/config files changed
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

                    // Determine services to build
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

                    // Remove common-library from matrix to build it separately
                    changedBackend.remove('common-library')

                    echo "🔍 Backend services to build: ${changedBackend ?: 'none'}"
                    echo "🔍 Frontend services to build: ${changedFrontend ?: 'none'}"
                }
            }
        }

        // --------------------------------------------------------------------
        // STAGE: GITLEAKS (secret scan) – FAIL if secrets found
        // --------------------------------------------------------------------
        stage('Security: Gitleaks') {
            when { expression { !skipBuild && (changedBackend || changedFrontend) } }
            steps {
                script {
                    // Cache Gitleaks binary (optional, but saves download time)
                    def gitleaksPath = "${WORKSPACE}/gitleaks-bin/gitleaks"
                    if (!fileExists(gitleaksPath)) {
                        sh '''
                            mkdir -p gitleaks-bin
                            curl -sSfL https://github.com/gitleaks/gitleaks/releases/download/v8.22.1/gitleaks_8.22.1_linux_x64.tar.gz | tar xz -C gitleaks-bin/
                            chmod +x gitleaks-bin/gitleaks
                        '''
                    }
                    // Fail on secrets (exit code 1)
                    sh "${gitleaksPath} detect --source=. --no-git --verbose --exit-code=1"
                }
            }
        }

        // --------------------------------------------------------------------
        // STAGE: PRE-BUILD DEPENDENCIES (install parent pom & common-library)
        // --------------------------------------------------------------------
        stage('Pre-build Dependencies') {
            when { expression { !skipBuild && (changedBackend || allChangedFiles?.any { it.startsWith('common-library/') }) } }
            steps {
                // Install into a shared cache location
                sh """
                    mvn install -N -B -Dmaven.test.skip=true -q -Dmaven.repo.local=${MAVEN_CACHE}
                    mvn install -pl common-library -am -B -Dmaven.test.skip=true -q -Dmaven.repo.local=${MAVEN_CACHE}
                """
            }
        }

        // --------------------------------------------------------------------
        // BACKEND CI MATRIX (with hardlink cache, concurrency=2)
        // --------------------------------------------------------------------
        stage('Backend CI') {
            when { expression { !skipBuild && changedBackend } }
            matrix {
                axes { axis { name 'SERVICE', values changedBackend } }
                // Run up to 2 services in parallel (safe for 8GB agent)
                options { matrix { concurrency(2) } }
                stages {
                    stage('Test & Coverage') {
                        steps {
                            script {
                                // Create a dedicated Maven repository for this service
                                def localRepo = "${WORKSPACE}/.m2-repo-${SERVICE}"
                                // Hardlink copy from cache (zero disk usage, instant)
                                sh """
                                    mkdir -p ${localRepo}
                                    cp -al ${MAVEN_CACHE}/. ${localRepo}/ || true
                                """
                                retry(2) {
                                    sh """
                                        mvn clean verify -pl ${SERVICE} -am -B \
                                            -Dmaven.test.failure.ignore=false \
                                            -Dmaven.repo.local=${localRepo} \
                                            -DforkCount=1
                                    """
                                }
                            }
                        }
                        post {
                            always {
                                // Publish JUnit test results
                                junit testResults: "${SERVICE}/target/surefire-reports/*.xml, ${SERVICE}/target/failsafe-reports/*.xml",
                                         allowEmptyResults: true

                                // Record coverage with threshold (LINE & BRANCH >= 70%)
                                recordCoverage(
                                    tools: [[parser: 'JACOCO', pattern: "${SERVICE}/target/site/jacoco/jacoco.xml"]],
                                    sourceDirectories: [[path: "${SERVICE}/src/main/java"]],
                                    qualityGates: [
                                        [threshold: Double.parseDouble(env.MIN_COVERAGE), metric: 'LINE', baseline: 'PROJECT', criticality: 'FAILURE'],
                                        [threshold: Double.parseDouble(env.MIN_COVERAGE), metric: 'BRANCH', baseline: 'PROJECT', criticality: 'FAILURE']
                                    ]
                                )
                            }
                        }
                    }

                    stage('SonarQube & Quality Gate') {
                        steps {
                            withSonarQubeEnv('SonarQube') {
                                script {
                                    def localRepo = "${WORKSPACE}/.m2-repo-${SERVICE}"
                                    def sonarParams = "-Dsonar.host.url=${env.SONAR_HOST} " +
                                                      "-Dsonar.projectKey=${env.SONAR_BASE_KEY}-${SERVICE} " +
                                                      "-Dsonar.projectName=YAS-${SERVICE}"

                                    // PR support
                                    if (env.CHANGE_ID) {
                                        sonarParams += " -Dsonar.pullrequest.key=${env.CHANGE_ID} " +
                                                       "-Dsonar.pullrequest.branch=${env.CHANGE_BRANCH} " +
                                                       "-Dsonar.pullrequest.base=${env.CHANGE_TARGET}"
                                    } else {
                                        sonarParams += " -Dsonar.branch.name=${env.BRANCH_NAME}"
                                    }

                                    sh "mvn sonar:sonar -pl ${SERVICE} -am -B ${sonarParams} -Dmaven.repo.local=${localRepo}"
                                }
                            }
                            // Wait for SonarQube analysis and enforce quality gate
                            timeout(time: 5, unit: 'MINUTES') {
                                waitForQualityGate abortPipeline: true
                            }
                        }
                    }

                    stage('Snyk & Docker') {
                        steps {
                            // Snyk scan – FAIL on high severity vulnerabilities
                            withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                                script {
                                    // Ensure Snyk is authenticated
                                    sh "snyk auth \$SNYK_TOKEN"
                                    // Fail if high severity issues found
                                    sh "snyk test --file=${SERVICE}/pom.xml --severity-threshold=high"
                                }
                            }

                            // Package JAR and build Docker image
                            script {
                                def localRepo = "${WORKSPACE}/.m2-repo-${SERVICE}"
                                sh "mvn package -pl ${SERVICE} -am -B -Dmaven.test.skip=true -Dmaven.repo.local=${localRepo}"

                                dir(SERVICE) {
                                    def dockerTag = "yas-${SERVICE}:${BUILD_ID}"
                                    sh "docker build --build-arg BUILDKIT_INLINE_CACHE=1 -t ${dockerTag} ."

                                    // Tag 'latest' only when not a PR (safe for deployments)
                                    if (env.CHANGE_ID == null) {
                                        sh "docker tag ${dockerTag} yas-${SERVICE}:latest"
                                    }
                                }
                            }
                        }
                        post {
                            success {
                                archiveArtifacts artifacts: "${SERVICE}/target/*.jar", allowEmptyArchive: true
                            }
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // FRONTEND CI MATRIX (with test coverage, Sonar, Snyk, Docker)
        // --------------------------------------------------------------------
        stage('Frontend CI') {
            when { expression { !skipBuild && changedFrontend } }
            matrix {
                axes { axis { name 'SERVICE', values changedFrontend } }
                options { matrix { concurrency(2) } }
                stages {
                    stage('Test & Build') {
                        steps {
                            dir(SERVICE) {
                                // Install dependencies (fast with offline mode)
                                sh 'npm ci --prefer-offline --no-audit'
                                // Run tests with coverage
                                sh 'npm run test -- --coverage --reporters=jest-junit'
                                // Build production bundle
                                sh 'npm run build'
                            }
                        }
                        post {
                            always {
                                // Publish test results (JUnit)
                                junit testResults: "${SERVICE}/junit.xml", allowEmptyResults: true

                                // Check coverage threshold manually
                                script {
                                    def covFile = "${SERVICE}/coverage/coverage-summary.json"
                                    if (fileExists(covFile)) {
                                        def coverage = sh(script: "jq '.total.lines.pct' ${covFile}", returnStdout: true).trim()
                                        if (coverage.toDouble() < env.MIN_COVERAGE.toDouble()) {
                                            error "❌ Frontend coverage ${coverage}% < ${env.MIN_COVERAGE}%"
                                        }
                                    } else {
                                        echo "⚠️ No coverage report found for ${SERVICE}"
                                    }
                                }
                            }
                        }
                    }

                    stage('SonarQube Frontend') {
                        steps {
                            withSonarQubeEnv('SonarQube') {
                                script {
                                    def scannerHome = tool name: 'SonarScanner'
                                    sh """
                                        ${scannerHome}/bin/sonar-scanner \
                                            -Dsonar.projectKey=${env.SONAR_BASE_KEY}-${SERVICE} \
                                            -Dsonar.projectName=YAS-${SERVICE} \
                                            -Dsonar.host.url=${env.SONAR_HOST} \
                                            -Dsonar.sources=${SERVICE}/src \
                                            -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/coverage/**
                                    """
                                }
                            }
                            timeout(time: 5, unit: 'MINUTES') {
                                waitForQualityGate abortPipeline: true
                            }
                        }
                    }

                    stage('Snyk & Docker') {
                        steps {
                            withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                                dir(SERVICE) {
                                    sh "snyk auth \$SNYK_TOKEN"
                                    sh "snyk test --file=package.json --severity-threshold=high"
                                }
                            }
                            dir(SERVICE) {
                                script {
                                    def dockerTag = "yas-${SERVICE}:${BUILD_ID}"
                                    sh "docker build --build-arg BUILDKIT_INLINE_CACHE=1 -t ${dockerTag} ."
                                    if (env.CHANGE_ID == null) {
                                        sh "docker tag ${dockerTag} yas-${SERVICE}:latest"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------------
    // POST: CLEANUP
    // --------------------------------------------------------------------
    post {
        always {
            script {
                // Remove temporary Maven repositories to save disk space
                sh "rm -rf ${MAVEN_CACHE} || true"
                if (changedBackend) {
                    changedBackend.each { svc ->
                        sh "rm -rf ${WORKSPACE}/.m2-repo-${svc} || true"
                    }
                }
            }
            cleanWs()
        }
        success {
            echo '✅ CI Pipeline completed successfully!'
        }
        failure {
            echo '❌ CI Pipeline failed. Check logs for details.'
        }
    }
}
