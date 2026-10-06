package com.woojik.aircallai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal enum class UiSymbol { Menu, Back, Send, Phone, Plus, More, Chevron, Model, Account, Theme, Shield, Info }

@Composable
internal fun AirCallIcon(symbol: UiSymbol, description: String? = null, modifier: Modifier = Modifier) {
    val vector = remember(symbol) {
        ImageVector.Builder(symbol.name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                when (symbol) {
                    UiSymbol.Menu -> { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f) }
                    UiSymbol.Back -> { moveTo(14f, 5f); lineTo(7f, 12f); lineTo(14f, 19f) }
                    UiSymbol.Send -> { moveTo(12f, 20f); lineTo(12f, 4f); moveTo(5f, 11f); lineTo(12f, 4f); lineTo(19f, 11f) }
                    UiSymbol.Phone -> { moveTo(6f, 3f); lineTo(10f, 3f); lineTo(11f, 8f); lineTo(8f, 10f); curveTo(9f, 13f, 11f, 15f, 14f, 16f); lineTo(16f, 13f); lineTo(21f, 14f); lineTo(21f, 18f); curveTo(21f, 23f, 12f, 20f, 7f, 15f); curveTo(2f, 10f, 1f, 3f, 6f, 3f); close() }
                    UiSymbol.Plus -> { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
                    UiSymbol.More -> { moveTo(12f, 5f); lineTo(12f, 5.1f); moveTo(12f, 12f); lineTo(12f, 12.1f); moveTo(12f, 19f); lineTo(12f, 19.1f) }
                    UiSymbol.Chevron -> { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
                    UiSymbol.Model -> { moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 17f); lineTo(7f, 17f); close(); moveTo(10f, 3f); lineTo(10f, 7f); moveTo(14f, 3f); lineTo(14f, 7f); moveTo(10f, 17f); lineTo(10f, 21f); moveTo(14f, 17f); lineTo(14f, 21f); moveTo(3f, 10f); lineTo(7f, 10f); moveTo(3f, 14f); lineTo(7f, 14f); moveTo(17f, 10f); lineTo(21f, 10f); moveTo(17f, 14f); lineTo(21f, 14f) }
                    UiSymbol.Account -> { moveTo(16f, 7f); curveTo(16f, 12.3f, 8f, 12.3f, 8f, 7f); curveTo(8f, 1.7f, 16f, 1.7f, 16f, 7f); close(); moveTo(4f, 21f); curveTo(4f, 12f, 20f, 12f, 20f, 21f) }
                    UiSymbol.Theme -> { moveTo(12f, 3f); curveTo(24f, 3f, 24f, 21f, 12f, 21f); curveTo(0f, 21f, 0f, 3f, 12f, 3f); close(); moveTo(12f, 3f); lineTo(12f, 21f); moveTo(12f, 8f); lineTo(19f, 8f); moveTo(12f, 12f); lineTo(21f, 12f); moveTo(12f, 16f); lineTo(19f, 16f) }
                    UiSymbol.Shield -> { moveTo(12f, 3f); lineTo(20f, 6f); lineTo(20f, 12f); curveTo(20f, 17f, 15f, 20f, 12f, 22f); curveTo(9f, 20f, 4f, 17f, 4f, 12f); lineTo(4f, 6f); close(); moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f) }
                    UiSymbol.Info -> { moveTo(12f, 3f); curveTo(24f, 3f, 24f, 21f, 12f, 21f); curveTo(0f, 21f, 0f, 3f, 12f, 3f); close(); moveTo(12f, 11f); lineTo(12f, 17f); moveTo(12f, 7f); lineTo(12f, 7.1f) }
                }
            }
        }.build()
    }
    Icon(vector, description, modifier.size(24.dp))
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Settings · light", widthDp = 360, heightDp = 800)
@Composable
private fun SettingsLightPreview() {
    AirCallTheme(themeMode = com.woojik.aircallai.settings.SettingsRepository.THEME_LIGHT) {
        AuroraBackground { SettingsCategories {} }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Settings · dark", widthDp = 360, heightDp = 800)
@Composable
private fun SettingsDarkPreview() {
    AirCallTheme(themeMode = com.woojik.aircallai.settings.SettingsRepository.THEME_DARK) {
        AuroraBackground { SettingsCategories {} }
    }
}
