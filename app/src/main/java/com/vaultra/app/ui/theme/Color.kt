package com.vaultra.app.ui.theme

import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0B0F)
val BgElev = Color(0xFF161619)
val BgCard = Color(0xFF1C1C20)
val Line = Color(0xFF2A2A2F)
val TextPrimary = Color(0xFFF2F1EE)
val TextDim = Color(0xFF9A9AA2)
val Accent = Color(0xFFE0223A)
val Accent2 = Color(0xFFFF5470)
val Good = Color(0xFF2ECC71)
val Warn = Color(0xFFF5A623)
val SwitchOffTrack = Color(0xFF4A4A52)
val SwitchOffBorder = Color(0xFF6E6E78)

val AvatarPalette = listOf(
    Color(0xFFE0223A), Color(0xFFE08B22), Color(0xFF22A8E0),
    Color(0xFF7D22E0), Color(0xFF22E0A0), Color(0xFFE0227A), Color(0xFF5A6EE0)
)

fun colorForName(name: String): Color {
    var hash = 0
    for (c in name) hash = (hash * 31 + c.code)
    val idx = ((hash % AvatarPalette.size) + AvatarPalette.size) % AvatarPalette.size
    return AvatarPalette[idx]
}
