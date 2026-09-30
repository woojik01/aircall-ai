package com.woojik.aircallai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Single-Activity app. Navigation: main -> conversation -> settings (PRD-01).
 * No network is required to reach any screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AirCallApp()
        }
    }

    override fun onStart() {
        super.onStart()
        com.woojik.aircallai.core.logging.SecureLog.debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}

private object Routes {
    const val MAIN = "main"
    const val CONVERSATION = "conversation"
    const val SETTINGS = "settings"
}

@Composable
private fun AirCallApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenConversation = { navController.navigate(Routes.CONVERSATION) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CONVERSATION) {
            val vm: MainViewModel = viewModel(factory = MainViewModel.Factory)
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
