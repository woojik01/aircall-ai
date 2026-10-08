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
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.woojik.aircallai.AirCallApp
import com.woojik.aircallai.AppGraph
import com.woojik.aircallai.BuildConfig
import com.woojik.aircallai.diagnostics.CrashDiagnostics
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
import kotlinx.coroutines.cancel
import com.woojik.aircallai.service.ModelDownloadService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** Activity-owned permission and OAuth launchers; chat/settings navigation is in ChatNavigation. */
class MainActivity : ComponentActivity() {

    // 캘린더 권한 상태. 런타임 요청 결과가 설정 화면에 즉시 반영되도록 compose 상태로 관리한다.
    private var openTarget by mutableStateOf("chat")
    private var openVersion by mutableStateOf(0)
    private var pendingDownload: String? = null
    private var vm: MainViewModel? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        CrashDiagnostics.onNotificationsAvailable(this)
        pendingDownload?.let { startModelDownload(it) }
        pendingDownload = null
    }

    // Google AuthorizationClient 권한 승인 결과를 받는 ActivityResultLauncher.
    private val startGoogleAuthorization =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            uiScope.launch {
                // A restored OAuth result may arrive before asynchronous startup has finished.
                val appGraph = try { withContext(Dispatchers.IO) { graph() } }
                catch (failure: CancellationException) { throw failure }
                catch (failure: Exception) { showStartupFailure(failure); return@launch }
                catch (failure: LinkageError) { showStartupFailure(failure); return@launch }
                try {
                    val authorizationResult =
                        Identity.getAuthorizationClient(this@MainActivity)
                            .getAuthorizationResultFromIntent(result.data)
                    val accessToken = authorizationResult.accessToken
                    if (accessToken.isNullOrBlank()) {
                        appGraph.accountRepository.mark(
                            "gmail", ConnectionStatus.ERROR,
                            errorMessage = "Google이 액세스 토큰을 반환하지 않았습니다 (결과 코드 ${result.resultCode}).",
                        )
                        return@launch
                    }
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
                } catch (e: ApiException) {
                    appGraph.accountRepository.mark(
                        "gmail", ConnectionStatus.ERROR,
                        errorMessage = "Google 인증 오류 (코드 ${e.statusCode}): 계정 권한을 승인하지 못했습니다. 테스트 모드 앱이면 Google Cloud 테스트 사용자 목록에 계정을 추가해야 합니다.",
                    )
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    appGraph.accountRepository.mark(
                        "gmail", ConnectionStatus.ERROR,
                        errorMessage = "Google 인증 결과를 처리하지 못했습니다. 다시 연결해 주세요.",
                    )
                }
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

    private fun graph(): AppGraph = (application as AirCallApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashDiagnostics.markStage(if (savedInstanceState == null) "activity-create" else "activity-restored")
        super.onCreate(savedInstanceState)
        pendingDownload = savedInstanceState?.getString("pendingDownload")
        // Draw a lightweight first frame before any settings/directory initialization.
        setContentView(android.widget.TextView(this).apply {
            text = "AirCall AI 시작 중…"
            gravity = android.view.Gravity.CENTER
            textSize = 18f
        })
        uiScope.launch {
            try {
                CrashDiagnostics.markStage("graph-initialize")
                val appGraph = withContext(Dispatchers.IO) { graph() }
                if (!isFinishing && !isDestroyed) showMainUi(appGraph, savedInstanceState)
            } catch (failure: CancellationException) { throw failure
            } catch (failure: Exception) { showStartupFailure(failure)
            } catch (failure: LinkageError) { showStartupFailure(failure) }
        }
    }

    private fun showMainUi(appGraph: AppGraph, savedInstanceState: Bundle?) {
        CrashDiagnostics.markStage("screen-initialize")
        uiScope.launch { appGraph.accountRepository.refresh(System.currentTimeMillis()) }

        val model = MainViewModel(
            app = application,
            repository = appGraph.sessionRepository,
            isMicPermissionGranted = { hasMicPermission() },
            requestMicPermission = { requestMicPermission() },
            startSession = { startConversationService() },
            stopSession = { sendServiceAction(ConversationService.ACTION_END) },
            isProviderReady = { withContext(Dispatchers.IO) { appGraph.providerRouter.current().isReady() } },
        )
        vm = model
        openTarget = if (savedInstanceState == null) intent.getStringExtra(EXTRA_SCREEN) ?: "chat" else ""
        uiScope.launch {
            CrashDiagnostics.markStage("chat-restore")
            appGraph.chatRooms.initialize()
            CrashDiagnostics.markStage("screen-ready")
            val taskRoom = intent.getStringExtra(EXTRA_ROOM_ID)
            if (appGraph.chatRooms.ready.value && taskRoom != null) {
                if (appGraph.chatRooms.activeId.value != taskRoom && appGraph.chatRooms.rooms.value.any { it.id == taskRoom }) {
                    model.prepareForRoomChange()
                    appGraph.toolApproval.deny()
                    appGraph.chatRooms.select(taskRoom)?.let { model.engine.restore(it.messages) }
                } else if (appGraph.chatRooms.activeId.value == null) {
                    model.engine.restore(appGraph.chatRooms.newRoom().messages)
                }
            } else if (appGraph.chatRooms.ready.value && appGraph.chatRooms.activeId.value == null) {
                model.engine.restore(appGraph.chatRooms.newRoom().messages)
            } else if (appGraph.chatRooms.ready.value && savedInstanceState == null && intent.action == Intent.ACTION_MAIN && !appGraph.sessionController.isRunning) {
                model.prepareForRoomChange()
                model.engine.restore(appGraph.chatRooms.newRoom().messages)
            }
        }
        setContent {
            androidx.compose.runtime.LaunchedEffect(Unit) { CrashDiagnostics.markStage("first-composition") }
            AirCallUi(model, appGraph, openTarget, openVersion,
                connectGitHub = { onCode -> connectGitHub(onCode) },
                connectGoogle = { connectGoogle() },
                disconnectAccount = { provider -> uiScope.launch { appGraph.accountRepository.disconnect(provider) } },
                startDownload = { id ->
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        pendingDownload = id
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else startModelDownload(id)
                },
                requestTaskNotifications = { requestTaskNotifications() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (vm == null) {
            recreate()
            return
        }
        openTarget = intent.getStringExtra(EXTRA_SCREEN) ?: "chat"
        openVersion++
        val taskRoom = intent.getStringExtra(EXTRA_ROOM_ID)
        if (taskRoom != null) {
            uiScope.launch {
                graph().chatRooms.initialize()
                if (graph().chatRooms.ready.value && graph().chatRooms.activeId.value != taskRoom) {
                    val exists = graph().chatRooms.rooms.value.any { it.id == taskRoom }
                    if (exists) {
                        vm?.prepareForRoomChange()
                        graph().toolApproval.deny()
                        graph().chatRooms.select(taskRoom)?.let { graph().engine.restore(it.messages) }
                    }
                }
            }
            return
        }
        if (intent.action == Intent.ACTION_MAIN && !graph().sessionController.isRunning) uiScope.launch {
            graph().chatRooms.initialize()
            if (graph().chatRooms.ready.value) {
                vm?.prepareForRoomChange()
                graph().engine.restore(graph().chatRooms.newRoom().messages)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingDownload", pendingDownload)
        super.onSaveInstanceState(outState)
    }

    /** Keep a recoverable startup failure on screen instead of repeatedly closing the app. */
    private fun showStartupFailure(failure: Throwable) {
        CrashDiagnostics.recordStartupFailure(failure)
        // Class and stack location only: exception messages may contain private data.
        val diagnostic = buildString {
            append("AirCall AI ").append(com.woojik.aircallai.BuildConfig.VERSION_NAME).append('\n')
            var cause: Throwable? = failure
            repeat(4) {
                val current = cause ?: return@repeat
                append(current.javaClass.name).append('\n')
                current.stackTrace.take(8).forEach { append(it.toString()).append('\n') }
                cause = current.cause
            }
        }
        val content = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        content.addView(android.widget.TextView(this).apply {
            text = "앱을 시작하지 못했습니다. 다시 시도하거나 오류 정보를 문의 메일로 전달해 주세요."
            textSize = 18f
        })
        content.addView(android.widget.Button(this).apply {
            text = "다시 시도"
            setOnClickListener { recreate() }
        })
        content.addView(android.widget.Button(this).apply {
            text = "오류 정보 문의"
            setOnClickListener {
                val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:woojik1220@gmail.com"))
                    .putExtra(Intent.EXTRA_SUBJECT, "AirCall AI 시작 오류")
                    .putExtra(Intent.EXTRA_TEXT, diagnostic)
                try { startActivity(email) }
                catch (_: android.content.ActivityNotFoundException) {
                    android.widget.Toast.makeText(this@MainActivity, "woojik1220@gmail.com", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        })
        setContentView(android.widget.ScrollView(this).apply { addView(content) })
    }

    private fun startModelDownload(id: String) {
        ContextCompat.startForegroundService(this, Intent(this, ModelDownloadService::class.java)
            .setAction(ModelDownloadService.ACTION_START).putExtra(ModelDownloadService.EXTRA_MODEL, id))
    }

    private fun requestTaskNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("notification_permission", MODE_PRIVATE)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("task_requested", false)) {
            prefs.edit().putBoolean("task_requested", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Diagnostic notifications must also work before the consent/chat screen can open.
        if (BuildConfig.DEBUG) requestTaskNotifications()
        CrashDiagnostics.onNotificationsAvailable(this)
    }

    override fun onDestroy() {
        vm?.dispose()
        uiScope.cancel()
        super.onDestroy()
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
        var intervalSeconds = session.intervalSeconds.coerceAtLeast(1)
        var consecutiveNetworkFailures = 0
        while (System.currentTimeMillis() < session.expiresAtMs) {
            // Opening the browser backgrounds this Activity. Background data restrictions
            // must not turn browser authorization into a permanent login failure.
            val remainingMs = session.expiresAtMs - System.currentTimeMillis()
            if (remainingMs <= 0) break
            val ready = withTimeoutOrNull(remainingMs) {
                delay(intervalSeconds * 1000L)
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                true
            } ?: false
            if (!ready || System.currentTimeMillis() >= session.expiresAtMs) break
            when (val result = appGraph.githubAuth.pollToken(session)) {
                is GitHubDeviceFlowClient.PollResult.Success -> {
                    appGraph.accountRepository.connect("github", result.tokens, null, System.currentTimeMillis())
                    return "GitHub 계정이 연결되었습니다."
                }
                is GitHubDeviceFlowClient.PollResult.Failed -> {
                    if (result.retryable && consecutiveNetworkFailures < 3) {
                        consecutiveNetworkFailures += 1
                        // Retry after 5, 10, then 20 seconds, without shortening
                        // any interval already required by GitHub.
                        intervalSeconds = maxOf(intervalSeconds, 5 shl (consecutiveNetworkFailures - 1))
                        continue
                    }
                    appGraph.accountRepository.mark("github", ConnectionStatus.ERROR, errorMessage = result.reason)
                    return result.reason
                }
                is GitHubDeviceFlowClient.PollResult.SlowDown -> {
                    // GitHub requests +5 seconds for each slow_down, cumulatively.
                    intervalSeconds = maxOf(intervalSeconds + 5, result.intervalSeconds)
                    consecutiveNetworkFailures = 0
                }
                GitHubDeviceFlowClient.PollResult.Pending -> consecutiveNetworkFailures = 0
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
        if (com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this) != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            val reason = "Google 연결에는 사용 가능한 Google Play 서비스가 필요합니다. 기기의 Google Play 서비스를 확인해 주세요."
            appGraph.accountRepository.mark("gmail", ConnectionStatus.ERROR, errorMessage = reason)
            return reason
        }
        if (appGraph.settings.googleOAuthClientId().isBlank()) {
            appGraph.accountRepository.mark("gmail", ConnectionStatus.ERROR)
            return "Google Android OAuth Client ID가 설정되지 않았습니다."
        }

        appGraph.accountRepository.mark("gmail", ConnectionStatus.CONNECTING)

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                GoogleOAuthClient.REQUIRED_SCOPES.map { Scope(it) },
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
                        ". 다시 연결해 주세요. 테스트 모드 앱이면 Google Cloud 테스트 사용자 목록을 확인해 주세요.",
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

    override fun onStart() {
        super.onStart()
        SecureLog.debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    companion object {
        const val EXTRA_SCREEN = "aircall.screen"
        const val EXTRA_ROOM_ID = "aircall.room"
        /** Google OAuth 콜백 스킴은 Client ID(리버스)에서 유도되며 manifest에 빌드 시점 주입된다. */
        const val GOOGLE_CALLBACK_PATH = GoogleOAuthClient.CALLBACK_PATH
    }
}
