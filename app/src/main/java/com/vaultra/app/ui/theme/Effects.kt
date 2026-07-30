package com.vaultra.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Gives a card/button a subtle 3D "press into the screen" feel: it scales down
 * slightly and tilts on the X axis while pressed, then springs back on release.
 */
@Composable
fun press3D(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "scale3d"
    )
    val tilt by animateFloatAsState(
        targetValue = if (pressed) 3.2f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "tilt3d"
    )
    return Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationX = tilt
        cameraDistance = 10f * density
    }
}
