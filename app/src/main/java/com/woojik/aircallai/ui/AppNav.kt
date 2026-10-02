package com.woojik.aircallai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.woojik.aircallai.AirCallApp
import com.woojik.aircallai.AppGraph
import com.woojik.aircallai.ai.cloud.CloudAIProvider
import com.woojik.aircallai.core.logging.SecureLog
import com.woojik.aircallai.service.ConversationService
import com.woojik.aircallai.tools.GitHubApiClient

/**
 * Single-Activity app. Navigation: main -> call / conversation -> settings -> local models / privacy.
 * PRD-03: 마이크 권한은 필요한 시점에 최소 범위로 요청한다.
 * PRD-04: ProviderRouter가 설정에 따라 Local/Cloud를 고른다.
 * PRD-05: 대화 세션은 Foreground Service가 소유하고 Activity는 상태 통로로만 접근한다.
 * PRD-07: 통화형 UI가 음성 대화의 기본 진입점이다.
 * 로컬 기능 증분: 설정에서 로컬 모델 갤러리(다운로드/적용)로 이동한다.
 * PRD-08: 설정에서 개인정보(데이터 흐름) 화면으로 이동한다.
 * PRD-06: 설정에서 GitHub 토큰(PAT)을 등록/삭제하고, WRITE 작업 승인 다이얼로그를 띄운다.
 */
class MainActivity : ComponentActivity() {

    private val requestMicPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.RECORD_AUDIO] == true) {
                startConversationService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as AirCallApp).graph

        val vm = MainViewModel(
            app = application,
            repository = graph.sessionRepository,
            isMicPermissionGranted = { hasMicPermission() },
            requestMicPermission = { requestMicPermission() },
            startSession = { startConversationService() },
            stopSession = { sendServiceAction(ConversationService.ACTION_END) },
            isProviderReady = { graph.providerRouter.current().isReady() },
        )
        setContent {
            AirCallUi(vm, graph)
        }
    }

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
private fun AirCallUi(vm: MainViewModel, graph: AppGraph) {
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
                // PRD-06: GitHub 토큰은 Cloud Key와 동일한 CredentialManager 계층에 보관한다.
                onSaveGitHubToken = { token ->
                    graph.credentials.save(GitHubApiClient.CREDENTIAL_SERVICE, token.toByteArray())
                },
                onDeleteGitHubToken = {
                    graph.credentials.delete(GitHubApiClient.CREDENTIAL_SERVICE)
                },
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
