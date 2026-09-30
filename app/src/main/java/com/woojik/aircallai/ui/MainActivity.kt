package com.woojik.aircallai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.woojik.aircallai.AppGraph
import com.woojik.aircallai.ai.cloud.CloudAIProvider
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.core.logging.SecureLog

/**
 * Single-Activity app. Navigation: main -> conversation -> settings (PRD-01).
 * PRD-03: 마이크 권한은 필요한 시점에 최소 범위로 요청한다.
 * PRD-04: ProviderRouter가 설정에 따라 Local/Cloud를 고른다.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph(application)

        val engine = ConversationEngine(graph.providerRouter.current())
        val session = com.woojik.aircallai.conversation.VoiceSession(
            recognizer = com.woojik.aircallai.audio.AndroidSpeechRecognizerEngine(application),
            synthesizer = com.woojik.aircallai.audio.AndroidSpeechSynthesizerEngine(application),
            engine = engine,
        )
        val vm = MainViewModel(
            app = application,
            engine = engine,
            voiceSession = session,
            isMicPermissionGranted = { hasMicPermission() },
            requestMicPermission = { requestMicPermission() },
        )
        setContent {
            AirCallApp(vm, graph)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestMicPermission() {
        requestPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onStart() {
        super.onStart()
        SecureLog.debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}

private object Routes {
    const val MAIN = "main"
    const val CONVERSATION = "conversation"
    const val SETTINGS = "settings"
}

@Composable
private fun AirCallApp(vm: MainViewModel, graph: AppGraph) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenConversation = { navController.navigate(Routes.CONVERSATION) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CONVERSATION) {
            ConversationScreen(vm)
        }
        composable(Routes.SETTINGS) {
            // PRD-04: 모드 변경 시 다음 대화부터 ProviderRouter가 반영한다.
            SettingsScreen(
                settings = graph.settings,
                onSaveApiKey = { key ->
                    graph.credentials.save(CloudAIProvider.KEY_SERVICE, key.toByteArray())
                    // 저장 후 다음 턴부터 갱신된 provider가 사용되도록 라우터 재적용
                    vm.engine.updateProvider(graph.providerRouter.current())
                },
                onDeleteApiKey = {
                    graph.credentials.delete(CloudAIProvider.KEY_SERVICE)
                },
            )
        }
    }
}

@Preview
@Composable
private fun AirCallAppPreview() {
    MainScreen(onOpenConversation = {}, onOpenSettings = {})
}
