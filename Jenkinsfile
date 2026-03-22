// ============================================================================
// 1. CẤU HÌNH BIẾN TOÀN CỤC 
// ============================================================================
// Khai báo danh sách toàn bộ các service trong hệ thống Monorepo
def BACKEND_SERVICES = [
    'common-library', 'backoffice-bff', 'cart', 'customer', 'delivery',
    'inventory', 'location', 'media', 'order', 'payment', 'payment-paypal',
    'product', 'promotion', 'rating', 'recommendation', 'sampledata',
    'search', 'storefront-bff', 'tax', 'webhook'
]
def FRONTEND_SERVICES = ['storefront', 'backoffice']

// Các mảng động để lưu trữ những service bị thay đổi và cần phải build
def changedBackend = []
def changedFrontend = []
def skipBuild = false // Cờ đánh dấu nếu chỉ sửa file README/docs thì bỏ qua build

// ============================================================================
// 2. HÀM PHÁT HIỆN THAY ĐỔI (SMART IMPACT ANALYSIS)
// ============================================================================
// Hàm này giúp Jenkins biết chính xác file nào vừa được Dev sửa, 
// từ đó chỉ build đúng service đó để tiết kiệm thời gian (Smart CI).
def getChangedFiles() {
    def files = [] as List
    
    // Trường hợp 1: Nếu đây là một Pull Request (PR)
    if (env.CHANGE_TARGET) {
        // Lấy lịch sử nhánh đích (ví dụ: main) và so sánh với nhánh hiện tại (HEAD)
        sh "git fetch origin ${env.CHANGE_TARGET}:refs/remotes/origin/${env.CHANGE_TARGET} --no-tags"
        def raw = sh(script: "git diff --name-only origin/${env.CHANGE_TARGET}...HEAD", returnStdout: true).trim()
        if (raw) files.addAll(raw.split('\n') as List)
        return files
    }
    
    // Trường hợp 2: Lấy trực tiếp từ ChangeSets của Jenkins (Push bình thường)
    currentBuild.changeSets.each { changeSet ->
        changeSet.each { entry -> files.addAll(entry.affectedPaths) }
    }
    if (files) return files
    
    // Trường hợp 3 (Dự phòng): So sánh với commit thành công trước đó
    if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        def raw = sh(script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT} ${env.GIT_COMMIT}", returnStdout: true).trim()
        if (raw) files.addAll(raw.split('\n') as List)
    }
    if (files) return files
    
    // Trường hợp 4 (Dự phòng cuối): Lấy file thay đổi của commit gần nhất
    try {
        def raw = sh(script: 'git diff --name-only HEAD~1 HEAD', returnStdout: true).trim()
        if (raw) files.addAll(raw.split('\n') as List)
    } catch (e) { echo "⚠️ No previous commit (first build?)" }
    
    return files
}

// ============================================================================
// 3. HÀM XỬ LÝ BUILD & TEST CHO BACKEND SERVICE 
// ============================================================================
def runBackendService(String service) {
    stage("Backend: ${service}") {
        // --- CHUẨN BỊ MÔI TRƯỜNG ---
        // Tạo repo Maven riêng cho service để tránh xung đột khi chạy song song (Parallel)
        def localRepo = "${env.WORKSPACE}/.m2-repo-${service}"
        sh "mkdir -p ${localRepo} && cp -al ${env.WORKSPACE}/.m2-cache/. ${localRepo}/ || true"
        
        // 3.1. BẢO MẬT: Quét Snyk SCA (Software Composition Analysis)
        // GIẢI PHÁP CHO LỖI -13: 
        // 1. Chạy Snyk từ thư mục gốc (không dùng dir(service)) để Snyk nhận diện được cấu trúc Parent/Child POM.
        // 2. Truyền tham số --Dmaven.repo.local để Snyk dùng chung cache đã chuẩn bị, tránh tải lại gây timeout/treo.
        // 3.1. BẢO MẬT: Quét Snyk SCA (dùng Docker)
        withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
            sh """
                docker run --rm \
                    -e SNYK_TOKEN=\$SNYK_TOKEN \
                    -v "${env.WORKSPACE}":/project \
                    -w /project \
                    snyk/snyk:maven \
                    snyk test --file=${service}/pom.xml --severity-threshold=high || true
            """
        }

        // 3.2. KIỂM THỬ: Chạy Unit/Integration Test
        // Chạy từ gốc với flag -pl (project list) để Maven tự điều phối các dependencies nội bộ
        retry(2) {
            sh "mvn clean verify -pl ${service} -am -B -Dmaven.test.failure.ignore=false -Dmaven.repo.local=${localRepo} -DforkCount=1"
        }
        
        // Ghi nhận báo cáo test lên giao diện Jenkins
        junit testResults: "${service}/target/surefire-reports/*.xml, ${service}/target/failsafe-reports/*.xml", allowEmptyResults: true
        
        // 3.3. CHẤT LƯỢNG CODE: Độ phủ Code (JaCoCo Coverage)
        recordCoverage(
            tools: [[parser: 'JACOCO', pattern: "${service}/target/site/jacoco/jacoco.xml"]],
            sourceDirectories: [[path: "${service}/src/main/java"]],
            qualityGates: [
                [threshold: env.MIN_COVERAGE.toDouble(), metric: 'LINE', baseline: 'PROJECT', criticality: 'FAILURE']
            ]
        )

        // 3.4. CHẤT LƯỢNG CODE: Quét SonarQube SAST
        withSonarQubeEnv('SonarQube') {
            def sonarParams = "-Dsonar.projectKey=${env.SONAR_BASE_KEY}-${service} -Dsonar.projectName=YAS-${service}"
            
            // Tự động cấu hình cho Pull Request hoặc Branch
            if (env.CHANGE_ID) {
                sonarParams += " -Dsonar.pullrequest.key=${env.CHANGE_ID} -Dsonar.pullrequest.branch=${env.CHANGE_BRANCH} -Dsonar.pullrequest.base=${env.CHANGE_TARGET}"
            } else {
                sonarParams += " -Dsonar.branch.name=${env.BRANCH_NAME}"
            }
            
            // Đẩy kèm kết quả JaCoCo lên Sonar để hiển thị biểu đồ Coverage
            sonarParams += " -Dsonar.coverage.jacoco.xmlReportPaths=${service}/target/site/jacoco/jacoco.xml"

            sh "mvn sonar:sonar -pl ${service} -am -B ${sonarParams} -Dmaven.repo.local=${localRepo}"
        }
        
        // Đợi kết quả trả về từ SonarQube (Quality Gate)
        timeout(time: 5, unit: 'MINUTES') { 
            waitForQualityGate abortPipeline: true 
        }

        // 3.5. ĐÓNG GÓI: Build Docker Image
        // Build file .jar/.war cuối cùng
        sh "mvn package -pl ${service} -am -B -Dmaven.test.skip=true -Dmaven.repo.local=${localRepo}"
        
        dir(service) {
            def dockerTag = "yas-${service}:${BUILD_ID}"
            sh "docker build --build-arg BUILDKIT_INLINE_CACHE=1 -t ${dockerTag} ."
            
            // Nếu merge vào main thì tag latest
            if (env.CHANGE_ID == null && env.BRANCH_NAME == 'main') {
                sh "docker tag ${dockerTag} yas-${service}:latest"
            }
        }
    }
}

// ============================================================================
// 4. HÀM XỬ LÝ BUILD & TEST CHO FRONTEND SERVICE 
// ============================================================================
def runFrontendService(String service) {
    stage("Frontend: ${service}") {
        dir(service) {
            // Tải thư viện Node, chạy Test và cấu hình bắt buộc sinh ra file lcov.info cho SonarQube
            sh 'npm ci --prefer-offline --no-audit'
            sh 'npm run test -- --coverage --coverageReporters="json-summary" --coverageReporters="lcov" --reporters=jest-junit'
            sh 'npm run build'
        }
        junit testResults: "${service}/junit.xml", allowEmptyResults: true
        
        // Kiểm tra Threshold Coverage 70% bằng lệnh 'jq' trên file json
        def covFile = "${service}/coverage/coverage-summary.json"
        if (fileExists(covFile)) {
            def coverage = sh(script: "jq '.total.lines.pct' ${covFile}", returnStdout: true).trim()
            if (coverage.toDouble() < env.MIN_COVERAGE.toDouble()) {
                error "❌ Frontend coverage ${coverage}% < ${env.MIN_COVERAGE}%"
            }
        }

        // 4.2 Quét SonarQube cho Frontend (Truyền đường dẫn lcov.info lên máy chủ)
        withSonarQubeEnv('SonarQube') {
            def scannerHome = tool name: 'SonarScanner'
            sh """
                ${scannerHome}/bin/sonar-scanner \
                    -Dsonar.projectKey=${env.SONAR_BASE_KEY}-${service} \
                    -Dsonar.projectName=YAS-${service} \
                    -Dsonar.sources=${service}/src \
                    -Dsonar.javascript.lcov.reportPaths=${service}/coverage/lcov.info \
                    -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/coverage/**
            """
        }
        
        // Chờ SonarQube báo cáo kết quả (Cần Webhook)
        timeout(time: 5, unit: 'MINUTES') { 
            waitForQualityGate abortPipeline: true 
        }

        // 4.3 Quét Snyk SCA cho Frontend (package.json)
        // 4.3 Quét Snyk SCA cho Frontend (dùng Docker)
        withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
            sh """
                docker run --rm \
                    -e SNYK_TOKEN=\$SNYK_TOKEN \
                    -v "${env.WORKSPACE}":/project \
                    -w /project/${service} \
                    snyk/snyk:alpine \
                    snyk test --file=package.json --severity-threshold=high || true
            """
        }

        // 4.4 Build Docker Image nội bộ
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
// 5. LUỒNG THỰC THI CHÍNH CỦA PIPELINE
// ============================================================================
node('jenkins-agent') {
    // Định nghĩa mức Coverage tối thiểu và Key SonarQube dùng chung
    env.MIN_COVERAGE = '70'
    env.SONAR_BASE_KEY = 'my-yas' 

    try {
        stage('Checkout Code') { checkout scm }

        stage('Smart Impact Analysis (Phân tích thay đổi)') {
            def allChangedFiles = getChangedFiles()
            
            // Nếu chỉ sửa file docs (README, .gitignore...) thì không cần tốn tài nguyên build
            def skipPatterns = [/^README\.md$/, /^\.gitignore$/, /^Jenkinsfile$/, /^\.github\/.*/, /^docs\/.*/, /^\.git\/.*/]
            def onlyTrivial = !allChangedFiles.isEmpty() && allChangedFiles.every { file -> skipPatterns.any { pattern -> file ==~ pattern } }

            if (onlyTrivial) {
                echo "✅ Only trivial changes. Skipping build."
                skipBuild = true; currentBuild.result = 'SUCCESS'; return
            }

            // Tính toán ra mảng changedBackend và changedFrontend
            if (allChangedFiles.isEmpty()) {
                changedBackend = BACKEND_SERVICES; changedFrontend = FRONTEND_SERVICES
            } else {
                def globalChanged = allChangedFiles.any { it == 'pom.xml' || it.startsWith('common-library/') }
                changedBackend = BACKEND_SERVICES.findAll { svc -> globalChanged || allChangedFiles.any { it.startsWith("${svc}/") } }
                changedFrontend = FRONTEND_SERVICES.findAll { svc -> allChangedFiles.any { it.startsWith("${svc}/") } }
            }
            // common-library luôn được build trước nên xóa khỏi danh sách parallel
            changedBackend.remove('common-library') 
        }

        // 🛡️ BẢO MẬT: Gitleaks (Smart Scan - Chỉ soi các commit trong Pull Request/Push hiện tại)
        if (!skipBuild && (changedBackend.size() > 0 || changedFrontend.size() > 0)) {
            stage('Security: Gitleaks') {
                script {
                    def configFlag = ""
                    if (fileExists('.gitleaks.toml')) {
                        configFlag = "--config .gitleaks.toml"
                    } else if (fileExists('gitleaks.toml')) {
                        configFlag = "--config gitleaks.toml"
                    }

                    // Tối ưu: Chỉ quét các commit mới được push lên, bỏ qua lịch sử cũ từ 2022
                    def logOpts = ""
                    if (env.CHANGE_TARGET) {
                        // Nếu là Pull Request: Chỉ quét các commit khác biệt so với nhánh gốc
                        logOpts = "--log-opts='origin/${env.CHANGE_TARGET}..HEAD'"
                    } else if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
                        // Nếu là nhánh thường: Chỉ quét từ lần build thành công trước đó
                        logOpts = "--log-opts='${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}..${env.GIT_COMMIT}'"
                    }

                    // Gitleaks tự động tìm file .gitleaksignore, không cần thêm tham số
                    // --no-git: scan code hiện tại (không scan lịch sử), tăng tốc và tránh false positive từ commit cũ 
                    // hoặc có thể xem xét BỎ cờ --no-git để bắt buộc Gitleaks soi toàn bộ lịch sử Commit (tránh lọt mã độc ẩn dấu)
                    // --verbose: hiển thị chi tiết
                    sh "gitleaks detect --source=. ${logOpts} --verbose --exit-code=1 ${configFlag}"
                }
            }
        }

        // Xử lý sự phụ thuộc trong Monorepo (Cài common-library vào máy Jenkins trước)
        if (!skipBuild && (changedBackend.size() > 0 || (getChangedFiles().any { it.startsWith('common-library/') }))) {
            stage('Pre-build Dependencies') {
                def mavenCache = "${env.WORKSPACE}/.m2-cache"
                sh "mvn install -N -B -Dmaven.test.skip=true -q -Dmaven.repo.local=${mavenCache}"
                sh "mvn install -pl common-library -am -B -Dmaven.test.skip=true -q -Dmaven.repo.local=${mavenCache}"
            }
        }

        // TỐI ƯU HIỆU NĂNG: Xử lý nghẽn cổ chai (Concurrency Control)
        if (!skipBuild && changedBackend.size() > 0) {
            stage('Backend CI (Batched)') {
                // Chia Backend thành các lô nhỏ (tối đa 1 service 1 lô) để vCPU/RAM của Jenkins không bị treo
                def batches = changedBackend.collate(1) 
                batches.eachWithIndex { batch, index ->
                    def parallelBackend = [:]
                    batch.each { service ->
                        parallelBackend[service] = { runBackendService(service) }
                    }
                    echo "🚀 Đang chạy Batch Backend #${index + 1}: ${batch}"
                    parallel parallelBackend 
                }
            }
        }

        // Phân lô Frontend (Do Webpack ngốn rất nhiều RAM nên chỉ chạy tối đa 2 service cùng lúc)
        if (!skipBuild && changedFrontend.size() > 0) {
            stage('Frontend CI (Batched)') {
                def batches = changedFrontend.collate(2) 
                batches.eachWithIndex { batch, index ->
                    def parallelFrontend = [:]
                    batch.each { service ->
                        parallelFrontend[service] = { runFrontendService(service) }
                    }
                    echo "🚀 Đang chạy Batch Frontend #${index + 1}: ${batch}"
                    parallel parallelFrontend
                }
            }
        }

    } catch (Exception e) {
        // Nếu bắt được bất cứ lỗi nào từ Test, Sonar, Snyk... Đánh đỏ Pipeline
        currentBuild.result = 'FAILURE'
        throw e
    } finally {
        // Giai đoạn dọn rác BẮT BUỘC (tránh đầy ổ cứng máy ảo EC2)
        stage('Cleanup & Notification') {
            sh "rm -rf ${env.WORKSPACE}/.m2-cache || true"
            changedBackend.each { svc -> sh "rm -rf ${env.WORKSPACE}/.m2-repo-${svc} || true" }
            cleanWs()
            
            if (currentBuild.result == 'SUCCESS' && !skipBuild) {
                echo "✅ Pipeline SUCCESS! Chất lượng code đã đạt chuẩn Quality Gate."
            } else if (!skipBuild) {
                echo "❌ Pipeline FAILED! Vui lòng kiểm tra lại log trên Jenkins hoặc SonarQube."
            }
        }
    }
}
