package com.woojik.aircallai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.woojik.aircallai.AirCallApp
import com.woojik.aircallai.AppGraph
import com.woojik.aircallai.ai.cloud.CloudAIProvider
import com.woojik.aircallai.auth.ConnectionStatus
import com.woojik.aircallai.auth.GitHubDeviceFlowClient
import com.woojik.aircallai.auth.GoogleOAuthClient
import com.woojik.aircallai.auth.OAuthCallbackResult
import com.woojik.aircallai.core.logging.SecureLog
import com.woojik.aircallai.service.ConversationService
import com.woojik.aircallai.tools.GitHubApiClient
import com.woojik.aircallai.tools.GmailApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single-Activity app. Navigation: main -> call / conversation -> settings -> local models / privacy.
 * PRD-03: 마이크 권한은 필요한 시점에 최소 범위로 요청한다.
 * PRD-04: ProviderRouter가 설정에 따라 Local/Cloud를 고른다.
 * PRD-05: 대화 세션은 Foreground Service가 소유하고 Activity는 상태 통로로만 접근한다.
 * PRD-07: 통화형 UI가 음성 대화의 기본 진입점이다.
 * 로컬 기능 증분: 설정에서 로컬 모델 갤러리(다운로드/적용)로 이동한다.
 * PRD-08: 설정에서 개인정보(데이터 흐름) 화면으로 이동한다.
 * PRD-06: 설정에서 Gi
tHub/Gmail 토큰을 등록/삭제하고, 캘린더 권한을 요청하며,
 * WRITE 작업 승인 다이얼로그를 띄운다.
 * PRD-09: GitHub는 OAuth 기기 인증, Google은 시스템 브라우저 인증으로 연결한다.
 * 인증 취소·실패가 앱 사용을 중단하지 않는다.
 */
class MainActivity : ComponentActivity() {

    // 캘린더 권한 상태. 런타임 요청 결과가 설정 화면에 즉시 반영되도록 compose 상태로 관리한다.
    private var calendarGranted by mutableStateOf(false)

    // PRD-09: 진행 중인 Google 인증 요청(state/code_verifier 보관).
    private var pendingGoogleAuth: GoogleOAuthClient.AuthRequest? = null

    // 인증 콜백 처리 등 화면 밖 코루틴용 스코프.
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val requestMicPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.RECORD_AUDIO] == true) {
                startConversationService()
            }
        }

    // PRD-06: 기기 캘린더 조회/등록 권한. 설정 → Calendar 연동에서 요청한다.
    private val requestCalendarPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            calendarGranted = hasCalendarPermission()
        }

    private fun graph(): AppGraph = (application as AirCallApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calendarGranted = hasCalendarPermission()
        val appGraph = graph()

        // PRD-09: 재시작 후 저장된 자격증명 기준으로 연결 상태를 복원한다.
        uiScope.launch { appGraph.accountRepository.refresh(System.currentTimeMillis()) }

        val vm = MainViewModel(
            app = application,
            repository = appGraph.sessionRepository,
            isMicPermissionGranted = { hasMicPermission() },
            requestMicPermission = { requestMicPermission() },
            startSession = { startConversationService() },
            stopSession = { sendServiceAction(ConversationService.ACTION_END) },
            isProviderReady = { appGraph.providerRouter.current().isReady() },
        )
        setContent {
    
        AirCallUi(
                vm,
                appGraph,
                calendarPermissionGranted = { calendarGranted },
                requestCalendarPermission = { requestCalendarPermission() },
                connectGitHub = { connectGitHub() },
                connectGoogle = { connectGoogle() },
                disconnectAccount = { provider -> uiScope.launch { appGraph.accountRepository.disconnect(provider) } },
            )
        }
        // PRD-09: 앱이 인증 콜백으로 실행된 경우(cold start) 처리한다.
        handleGoogleCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // PRD-09: Google 인증 후 브라우저에서 돌아온 콜백을 처리한다.
        handleGoogleCallback(intent)
    }

    /** PRD-09 Phase 2: GitHub OAuth 기기 인증. user_code를 보여주고 토큰을 폴링한다. */
    private suspend fun connectGitHub(): String {
        val appGraph = graph()
        appGraph.accountRepository.mark("github", ConnectionStatus.CONNECTING)
        val startResult = appGraph.githubAuth.startDeviceFlowDetailed()
        val session = when (startResult) {
            is GitHubDeviceFlowClient.StartResult.Success -> startResult.session
            is GitHubDeviceFlowClient.StartResult.Failed -> {
                appGraph.accountRepository.mark("github", ConnectionStatus.ERROR)
                return startResult.reason
            }
        }
        if (!openBrowser(session.verificationUri)) {
            appGraph.accountRepository.mark("github", ConnectionStatus.ERROR)
            return "GitHub 로그인 페이지를 열 수 없습니다."
        }
        // 사용자가 브라우저에서 코드를 입력하고 승인할 때까지 폴링한다.
        while (System.currentTimeMillis() < session.expiresAtMs) {
            when (val result = appGraph.githubAuth.pollToken(session)) {
                is GitHubDeviceFlowClient.PollResult.Success -> {
                    appGraph.accountRepository.connect("github", result.tokens, null, System.currentTimeMillis())
                    return "GitHub 계정이 연결되었습니다. 코드: " + session.userCode + " 승인 완료."
                }
                is GitHubDeviceFlowClient.PollResult.Failed -> {
                    appGraph.accountRepository.mark("github", ConnectionStatus.ERROR)
                    return result.reason
                }
                GitHubDeviceFlowClient.PollResult.Pending -> delay(session.intervalSeconds * 1000L)
            }
        }
        appGraph.accountRepository.mark("github", ConnectionStatus.ERROR)
        return "기기 인증 시간이 만료되었습니다. 다시 연결해 주세요."
    }

    /** PRD-09 Phase 3: Google OAuth. 시스템 브라우저로 인증 화면을 연다. */
    private fun connectGoogle(): String {
        val appGraph = graph()
        // Android 유형 OAuth 클라이언트: 콜백 URI는 Client ID에서 유도된 리버스 스킴을 사용한다.
        val redirectUri = GoogleOAuthClient.redirectUriFor(appGraph.settings.googleOAuthClientId())
        val request = if (redirectUri == null) null else appGraph.googleAuth.buildAuthRequest(redirectUri)
        if (request == null) {
            appGraph.accountRepository.mark("gmail", ConnectionStatus.ERROR)
            return "Google OAuth Client ID를 먼저 저장해 주세요 (Gmail 연동 섹션)."
        }
        pendingGoogleAuth = request
        appGraph.accountRepository.mark("gmail", ConnectionStatus.CONNECTING)
        return if (openBrowser(request.authUrl)) {
            "Google 로그인 화면이 열렸습니다. 승인 후 앱으로 돌아오면 연결이 완료됩니다."
        } else {
            appGraph.accountRepository.mark("gmail", ConnectionStatus.ERROR)
            "브라우저를 열 수 없습니다. Chrome 등 브라우저를 설치해 주세요."
        }
    }

    /** PRD-09: Google 콜백(리버스 클라이언트 ID 스킴)을 검증하고 토큰으로 교환한다. */
    private fun handleGoogleCallback(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        val request = pendingGoogleAuth ?: return
        val appGraph = graph()
        // Android 유형 OAuth 클라이언트: 리버스 클라이언트 ID 스킴 콜백만 처리한다.
        val callbackScheme = GoogleOAuthClient.callbackSchemeFor(appGraph.settings.googleOAuthClientId())
        if (callbackScheme == null || data.scheme != callbackScheme || data.path != GoogleOAuthClient.CALLBACK_PATH) return
        pendingGoogleAuth = null
        if (data.getQueryParameter("error") != null) {
            // 사용자가 취소해도 앱 사용은 계속된다.
            appGraph.accountRepository.mark("gmail", ConnectionStatus.NOT_CONNECTED)
            return
        }
        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        val result = appGraph.accountRepository.validateCallback("gmail", "gmail", code, state, request.state)
        if (result !is OAuthCallbackResult.Success) {
            appGraph.accountRepository.mark("gmail", ConnectionStatus.ERROR)
    
        return
        }
        uiScope.launch {
            val redirectUri = GoogleOAuthClient.redirectUriFor(appGraph.settings.googleOAuthClientId())
            val tokens = if (redirectUri == null) {
                null
            } else {
                appGraph.googleAuth.exchangeCode(result.code, request.codeVerifier, redirectUri)
            }
            if (tokens == null) {
                appGraph.accountRepository.mark("gmail", ConnectionStatus.ERROR)
            } else {
                appGraph.accountRepository.connect("gmail", tokens, null, System.currentTimeMillis())
            }
        }
    }

    private fun openBrowser(url: String): Boolean =
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        }.getOrElse { false }

    private fun startConversationService() {
        sendServiceAction(ConversationService.ACTION_START, foreground = true)
    }

    private fun sendServiceAction(action: String, foreground: Boolean = false) {
        val intent = Intent(this, ConversationService::class.java).setAction(action)
        if (foreground) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** PRD-05: 마이크 + (Android 13+) 알림 권한을 세션 시작 시점에 함께 요청한다. */
    private fun requestMicPermission() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestMicPermissions.launch(permissions.toTypedArray())
    }

    /** PRD-06: 캘린더 조회·등록 권한. */
    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestCalendarPermission() {
        requestCalendarPermissions.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
            ),
        )
    }

    override fun onStart() {
        super.onStart()
        SecureLog.debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    companion object {
        /** Google OAuth 콜백 스킴은 Client ID(리버스)에서 유도되며 manifest에 빌드 시점 주입된다. */
        const val GOOGLE_CALLBACK_PATH = GoogleOAuthClient.CALLBACK_PATH
    }
}

private object Routes {
    const val MAIN = "main"
    const val CALL = "call"
    const val CONVERSATION = "conversation"
    const val SETTINGS = "settings"
    const val LOCAL_MODELS = "local-models"
    const val PRIVACY = "privacy"
}

@Composable
private fun AirCallUi(
    vm: MainViewModel,
    graph: AppGraph,
    calendarPermissionGranted: () -> Boolean,
    requestCalendarPermission: () -> Unit,
    connectGitHub: suspend () -> String,
    connectGoogle: () -> String,
    disconnectAccount: (String) -> Unit,
) {
    val navController = rememberNavController()
    val modelScope = rememberCoroutineScope()
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenCall = { navController.navigate(Routes.CALL) },
                onOpenConversation = { navController.navigate(Routes.CONVERSATION) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CALL) {
            // PRD-07 통화 화면: 종료 시 메인으로 돌아간다.
            CallScreen(vm, onExit = { navController.popBackStack() })
        }
        composable(Routes.CONVERSATION) {
            ConversationScreen(vm)
        }
        composable(Routes.SETTINGS) {
            // PRD-04/05: 모드 변경 시에도 다음 턴부터 갱신된 provider가 적용된다.
            val accounts by graph.accountRepository.connections.collectAsState()
            SettingsScreen(
                settings = graph.settings,
                onModeChanged = {
                    vm.engine.updateProvider(graph.providerRouter.current())
                    vm.refreshProviderReadiness()
                },
                onSaveApiKey = { key ->
                    graph.credentials.save(CloudAIProvider.KEY_SERVICE, key.toByteArray())
                    vm.engine.updateProvider(graph.providerRouter.current())
                    vm.refreshProviderReadiness()
                },
                onDeleteApiKey = {
                    graph.credentials.delete(CloudAIProvider.KEY_SERVICE)
                    vm.refreshProviderReadiness()
                },
                onOpenLocalModels = { navController.navigate(Routes.LOCAL_MODELS) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                // PRD-06: GitHub/Gmail 토큰은 Cloud Key와 동일한 CredentialManager 계층에 보관한다.
                onSaveGitHubToken = { token ->
                    graph.credentials.save(GitHubApiClient.CREDENTIAL_SERVICE, token.toByteArray())
                },
                onDeleteGitHubToken = {
                    graph.credentials.delete(GitHubApiClient.CREDENTIAL_SERVICE)
                },
                // PRD-06: 기기 캘린더 권한 요청과 상태 표시.
                calendarPermissionGranted = calendarPermissionGranted(),
                onRequestCalendarPermission = requestCalendarPermission,
                // PRD-06: Gmail OAuth 액세스 토큰 등록/삭제.
                onSaveGmailToken = { token ->
                    graph.credentials.save(GmailApiClient.CREDENTIAL_SERVICE, token.toByteArray())
                },
                onDeleteGmailToken = {
                    graph.credentials.delete(GmailApiClient.CREDENTIAL_SERVICE)
                },
                // PRD-09: 계정 연결 상태와 OAuth 연결/해제.
                githubConnectionStatus = accounts["github"]?.let { graph.accountRepository.statusMessage(it) },
                gmailConnectionStatus = accounts["gmail"]?.let { graph.accountRepository.statusMessage(it) },
         
       onConnectGitHub = connectGitHub,
                onConnectGoogle = connectGoogle,
                onDisconnectGitHub = { disconnectAccount("github") },
                onDisconnectGmail = { disconnectAccount("gmail") },
                // PRD-06 승인 증분: 승인된 WRITE 작업 목록 표시/해제.
                toolApprovals = graph.toolPermissions.approvedActions(),
                onRevokeToolApproval = { key ->
                    graph.toolPermissions.revoke(key)
                },
            )
        }
        composable(Routes.LOCAL_MODELS) {
            // 로컬 모델 갤러리: 다운로드/적용 후 통화 화면의 Offline 상태가 해소된다.
            LocalModelScreen(
                settings = graph.settings,
                adapter = graph.localModelAdapter,
                downloadManager = graph.modelDownloadManager,
                scope = modelScope,
                onModelChanged = { vm.refreshProviderReadiness() },
            )
        }
        composable(Routes.PRIVACY) {
            // PRD-08 개인정보 화면: 모드별/Tool별 데이터 흐름을 표시한다.
            PrivacyScreen()
        }
    }

    // PRD-06: WRITE Tool 작업 승인 다이얼로그 — 화면 어디에서든 대기 요청이 있으면 띄운다.
    val pendingApproval by graph.toolApproval.pending.collectAsState()
    pendingApproval?.let { request ->
        ToolApprovalDialog(
            request = request,
            onApprove = { graph.toolApproval.approve() },
            onDeny = { graph.toolApproval.deny() },
        )
    }
}

@Preview
@Composable
private fun AirCallAppPreview() {
    MainScreen(onOpenCall = {}, onOpenConversation = {}, onOpenSettings = {})
}
