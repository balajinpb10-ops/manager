package com.vaultra.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
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

/**
 * Shared Switch colors: OFF now shows a clearly visible grey track with a white thumb
 * and a distinct border (previously it used the near-invisible `outline`/Line color),
 * while ON keeps the existing red accent look.
 */
@Composable
fun vaultraSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = androidx.compose.ui.graphics.Color.White,
    checkedTrackColor = Accent,
    checkedBorderColor = Accent,
    checkedIconColor = Accent,
    uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
    uncheckedTrackColor = SwitchOffTrack,
    uncheckedBorderColor = SwitchOffBorder,
    uncheckedIconColor = SwitchOffTrack
)
