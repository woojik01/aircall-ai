package com.woojik.aircallai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun AirCallTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFA7B9FF), onPrimary = Color(0xFF152B6D),
        primaryContainer = Color(0xFF253F8C), onPrimaryContainer = Color(0xFFE0E6FF),
        background = Color(0xFF0D1426), surface = Color(0xFF121C30),
        surfaceVariant = Color(0xFF23304A), onSurface = Color(0xFFE8EDFA),
        onSurfaceVariant = Color(0xFFBCC7DF), secondary = Color(0xFF8ED4D9),
    ) else lightColorScheme(
        primary = Color(0xFF3B55AE), onPrimary = Color.White,
        primaryContainer = Color(0xFFDFE6FF), onPrimaryContainer = Color(0xFF172B65),
        background = Color(0xFFF7F9FF), surface = Color(0xFFFDFBFF),
        surfaceVariant = Color(0xFFEBEEF8), onSurface = Color(0xFF18213A),
        onSurfaceVariant = Color(0xFF505D78), secondary = Color(0xFF356C78),
    )
    MaterialTheme(colorScheme = colors, content = content)
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
