package com.woojik.aircallai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.woojik.aircallai.settings.SettingsRepository

private val LightColors = lightColorScheme(
    primary = Color(0xFF344DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE4FF),
    onPrimaryContainer = Color(0xFF111C44),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C28),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C28),
    surfaceVariant = Color(0xFFE3E5F0),
    onSurfaceVariant = Color(0xFF444754),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF15265E),
    primaryContainer = Color(0xFF2D418F),
    onPrimaryContainer = Color(0xFFDFE4FF),
    background = Color(0xFF11131D),
    onBackground = Color(0xFFE3E5F0),
    surface = Color(0xFF11131D),
    onSurface = Color(0xFFE3E5F0),
    surfaceVariant = Color(0xFF444754),
    onSurfaceVariant = Color(0xFFC5C7D5),
)

/** All screens and dialogs inherit semantic text colors from this root surface. */
@Composable
fun AirCallTheme(
    themeMode: String = SettingsRepository.THEME_SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        SettingsRepository.THEME_LIGHT -> false
        SettingsRepository.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
