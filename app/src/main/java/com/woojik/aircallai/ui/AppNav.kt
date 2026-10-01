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

/**
 * Single-Activity app. Navigation: main -> call / conversation -> settings.
 * PRD-03: 마이크 권한은 필요한 시점에 최소 범위로 요청한다.
 * PRD-04: ProviderRouter가 설정에 따라 Local/Cloud를 고른다.
 * PRD-05: 대화 세션은 Foreground Service가 소유하고 Activity는 상태 통로로만 접근한다.
 * PRD-07: 통화형 UI가 음성 대화의 기본 진입점이다.
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
}

@Composable
private fun AirCallUi(vm: MainViewModel, graph: AppGraph) {
    val navController = rememberNavController()
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
            )
        }
    }
}

@Preview
@Composable
private fun AirCallAppPreview() {
    MainScreen(onOpenCall = {}, onOpenConversation = {}, onOpenSettings = {})
}
