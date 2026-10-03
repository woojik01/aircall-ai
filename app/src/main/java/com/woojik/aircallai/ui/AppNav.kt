package com.woojik.aircallai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.IntentSenderRequest
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
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
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

    // Google AuthorizationClient 권한 승인 결과를 받는 ActivityResultLauncher.
    private val startGoogleAuthorization =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val appGraph = graph()
            try {
                val authorizationResult =
                    Identity.getAuthorizationClient(this)
                        .getAuthorizationResultFromIntent(result.data)
                val accessToken = authorizationResult.accessToken
                if (accessToken.isNullOrBlank()) {
                    appGraph.accountRepository.mark(
                        "gmail", ConnectionStatus.ERROR,
                        errorMessage = "Google이 액세스 토큰을 반환하지 않았습니다 (결과 코드 ${result.resultCode}).",
                    )
                    return@registerForActivityResult
                }
                uiScope.launch {
                    appGraph.accountRepository.connect(
                        "gmail",
                        com.woojik.aircallai.auth.OAuthTokens(
                            accessToken = accessToken,
                            refreshToken = null,
                            expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L,
                        ),
                        null,
                        System.currentTimeMillis(),
                    )
                }
            } catch (e: ApiException) {
                appGraph.accountRepository.mark(
                    "gmail", ConnectionStatus.ERROR,
                    errorMessage = "Google 인증 오류 (코드 ${e.statusCode}): ${e.message ?: "인증을 완료하지 못했습니다"}. 테스트 모드 앱이면 Google Cloud 테스트 사용자 목록에 계정을 추가해야 합니다.",
                )
            } catch (e: Exception) {
                appGraph.accountRepository.mark(
                    "gmail", ConnectionStatus.ERROR,
                    errorMessage = "Google 인증 결과를 처리하지 못했습니다: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }

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
                connectGitHub = { onCode -> connectGitHub(onCode) },
                connectGoogle = { connectGoogle() },
                disconnectAccount = { provider ->
                    uiScope.launch { appGraph.accountRepository.disconnect(provider) }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    /** PRD-09 Phase 2: Google과 동일하게 GitHub Device Flow는 기존 방식을 유지한다. */
    private suspend fun connectGitHub(onDeviceCodeReady: (String) -> Unit): String {
        val appGraph = graph()
        appGraph.accountRepository.mark("github", ConnectionStatus.CONNECTING)
        val start = appGraph.githubAuth.startDeviceFlowDetailed()
        if (start is GitHubDeviceFlowClient.StartResult.Failed) {
            appGraph.accountRepository.mark(
                "github", ConnectionStatus.ERROR, errorMessage = start.reason,
            )
            return start.reason
        }
        val session = (start as GitHubDeviceFlowClient.StartResult.Ready).session
        onDeviceCodeReady(session.userCode)
        if (!openBrowser(session.verificationUri)) {
            val reason = "GitHub 로그인 페이지를 열 수 없습니다."
            appGraph.accountRepository.mark("github", ConnectionStatus.ERROR, errorMessage = reason)
            return reason
        }
        var intervalSeconds = session.intervalSeconds
        while (System.currentTimeMillis() < session.expiresAtMs) {
            when (val result = appGraph.githubAuth.pollToken(session)) {
                is GitHubDeviceFlowClient.PollResult.Success -> {
                    appGraph.accountRepository.connect("github", result.tokens, null, System.currentTimeMillis())
                    return "GitHub 계정이 연결되었습니다."
                }
                is GitHubDeviceFlowClient.PollResult.Failed -> {
                    appGraph.accountRepository.mark("github", ConnectionStatus.ERROR, errorMessage = result.reason)
                    return result.reason
                }
                is GitHubDeviceFlowClient.PollResult.SlowDown -> {
                    // GitHub requests +5 seconds for each slow_down, cumulatively.
                    intervalSeconds = maxOf(intervalSeconds + 5, result.intervalSeconds)
                    delay(intervalSeconds * 1000L)
                }
                GitHubDeviceFlowClient.PollResult.Pending -> delay(intervalSeconds * 1000L)
            }
        }
        val reason = "기기 인증 시간이 만료되었습니다. 다시 연결해 주세요."
        appGraph.accountRepository.mark("github", ConnectionStatus.ERROR, errorMessage = reason)
        return reason
    }

    /**
     * Google Android AuthorizationClient를 사용한다.
     * 브라우저 URL, PKCE, custom URI callback, redirect URI를 앱에서 직접 처리하지 않는다.
     */
    private fun connectGoogle(): String {
        val appGraph = graph()
        if (appGraph.settings.googleOAuthClientId().isBlank()) {
            appGraph.accountRepository.mark("gmail", ConnectionStatus.ERROR)
            return "Google Android OAuth Client ID가 설정되지 않았습니다."
        }

        appGraph.accountRepository.mark("gmail", ConnectionStatus.CONNECTING)

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(GoogleOAuthClient.SCOPE_GMAIL_MODIFY),
                    Scope(GoogleOAuthClient.SCOPE_GMAIL_SEND),
                ),
            )
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    val pendingIntent = authorizationResult.pendingIntent
                    if (pendingIntent == null) {
                        appGraph.accountRepository.mark(
                            "gmail", ConnectionStatus.ERROR,
                            errorMessage = "Google 권한 승인 화면을 시작하지 못했습니다. 테스트 모드 앱이면 Google Cloud 테스트 사용자 목록에 이 계정을 추가해야 합니다.",
                        )
                    } else {
                        startGoogleAuthorization.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                        )
                    }
                } else {
                    val accessToken = authorizationResult.accessToken
                    if (accessToken.isNullOrBlank()) {
                        appGraph.accountRepository.mark(
                            "gmail", ConnectionStatus.ERROR,
                            errorMessage = "Google이 Gmail 액세스 토큰을 반환하지 않았습니다. 권한 승인을 확인해 주세요.",
                        )
                    } else {
                        uiScope.launch {
                            appGraph.accountRepository.connect(
                                "gmail",
                                com.woojik.aircallai.auth.OAuthTokens(
                                    accessToken = accessToken,
                                    refreshToken = null,
                                    expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L,
                                ),
                                null,
                                System.currentTimeMillis(),
                            )
                        }
                    }
                }
            }
            .addOnFailureListener { error ->
                val statusCode = (error as? ApiException)?.statusCode
                appGraph.accountRepository.mark(
                    "gmail", ConnectionStatus.ERROR,
                    errorMessage = "Google 로그인에 실패했습니다" +
                        (statusCode?.let { " (코드 $it)" } ?: "") +
                        ": ${error.message ?: error.javaClass.simpleName}. 테스트 모드 앱이면 Google Cloud 테스트 사용자 목록을 확인해 주세요.",
                )
            }

        return "Google 계정 권한 요청을 시작했습니다."
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
    connectGitHub: suspend ((String) -> Unit) -> String,
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
