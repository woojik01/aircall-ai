package com.woojik.aircallai

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.woojik.aircallai.ui.airCallColors
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadablePaletteTest {
    @Test fun normalTextHasSufficientContrastInBothSystemThemes() {
        for (dark in listOf(false, true)) {
            val colors = airCallColors(dark)
            val pairs = listOf(
                "body" to (colors.onSurface to colors.surface),
                "background" to (colors.onBackground to colors.background),
                "description" to (colors.onSurfaceVariant to colors.surface),
                "secondary label" to (colors.onSurfaceVariant to colors.surfaceVariant),
                "button" to (colors.onPrimary to colors.primary),
                "user bubble" to (colors.onPrimaryContainer to colors.primaryContainer),
                "link" to (colors.primary to colors.surface),
                "error text" to (colors.error to colors.background),
                "error button" to (colors.onError to colors.error),
            )
            for ((name, pair) in pairs) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue("$name in dark=$dark has contrast $ratio", ratio >= 4.5f)
            }
        }
    }

    private fun contrast(text: Color, background: Color): Float {
        val a = text.luminance(); val b = background.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
