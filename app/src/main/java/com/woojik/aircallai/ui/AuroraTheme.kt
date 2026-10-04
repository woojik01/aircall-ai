package com.woojik.aircallai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// All screen text shares readable Korean sizing and line spacing; sp preserves OS font scaling.
internal val AirCallTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
)

internal fun airCallColors(dark: Boolean) = if (dark) darkColorScheme(
        primary = Color(0xFFA7B9FF), onPrimary = Color(0xFF152B6D),
        primaryContainer = Color(0xFF253F8C), onPrimaryContainer = Color(0xFFE0E6FF),
        background = Color(0xFF0D1426), surface = Color(0xFF121C30),
        surfaceVariant = Color(0xFF23304A), onSurface = Color(0xFFF0F3FC), onBackground = Color(0xFFF0F3FC),
        onSurfaceVariant = Color(0xFFCFD8EC), secondary = Color(0xFFA5DBE0),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    ) else lightColorScheme(
        primary = Color(0xFF3B55AE), onPrimary = Color.White,
        primaryContainer = Color(0xFFDFE6FF), onPrimaryContainer = Color(0xFF172B65),
        background = Color(0xFFF7F9FF), surface = Color(0xFFFDFBFF),
        surfaceVariant = Color(0xFFEBEEF8), onSurface = Color(0xFF18213A), onBackground = Color(0xFF18213A),
        onSurfaceVariant = Color(0xFF3E4A63), secondary = Color(0xFF356C78),
        error = Color(0xFFBA1A1A), onError = Color.White,
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )

@Composable
fun AirCallTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = airCallColors(dark), typography = AirCallTypography, content = content)
}

@Composable
fun AuroraBackground(content: @Composable BoxScope.() -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().background(background).drawBehind {
        drawRect(Brush.radialGradient(
            listOf(Color(0xFF5CD8CB).copy(alpha = 0.12f), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.12f), radius = size.width * 0.95f,
        ))
        drawRect(Brush.radialGradient(
            listOf(Color(0xFF8C8FE8).copy(alpha = 0.10f), Color.Transparent),
            center = Offset(0f, size.height * 0.65f), radius = size.width * 0.9f,
        ))
    }, content = content)
}
