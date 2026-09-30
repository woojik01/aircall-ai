package com.woojik.aircallai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.woojik.aircallai.ai.provider.NoopAIProvider
import com.woojik.aircallai.audio.AndroidSpeechRecognizerEngine
import com.woojik.aircallai.audio.AndroidSpeechSynthesizerEngine
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.conversation.VoiceSession
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single-Activity app. Navigation: main -> conversation -> settings (PRD-01).
 * PRD-03: 마이크 권한은 필요한 시점에 최소 범위로 요청한다.
 */
class MainActivity : ComponentActivity() {

    private val permissionDenied = MutableStateFlow(false)
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionDenied.value = !granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application
        val engine = ConversationEngine(NoopAIProvider())
        val session = VoiceSession(
            recognizer = AndroidSpeechRecognizerEngine(app),
            synthesizer = AndroidSpeechSynthesizerEngine(app),
            engine = engine,
        )
        val vm = MainViewModel(
            app = app,
            engine = engine,
            voiceSession = session,
            isMicPermissionGranted = { hasMicPermission() },
            requestMicPermission = { requestMicPermission() },
        )
        setContent {
            AirCallApp(vm)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        requestPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onStart() {
        super.onStart()
        com.woojik.aircallai.core.logging.SecureLog.debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}

private object Routes {
    const val MAIN = "main"
    const val CONVERSATION = "conversation"
    const val SETTINGS = "settings"
}

@Composable
private fun AirCallApp(vm: MainViewModel) {
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
            SettingsScreen()
        }
    }
}

@Preview
@Composable
private fun AirCallAppPreview() {
    MainScreen(onOpenConversation = {}, onOpenSettings = {})
}
