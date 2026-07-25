package com.vaultra.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val VaultraColorScheme = darkColorScheme(
    primary = Accent,
    secondary = Accent2,
    background = Bg,
    surface = BgCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = Line
)

@Composable
fun VaultraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VaultraColorScheme,
        content = content
    )
}
