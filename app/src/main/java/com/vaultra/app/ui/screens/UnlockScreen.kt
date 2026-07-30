package com.vaultra.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.vaultra.app.crypto.CryptoManager
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.BiometricHelper
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(activity: FragmentActivity, cryptoManager: CryptoManager, onUnlocked: (ByteArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val biometricReady = remember {
        cryptoManager.isBiometricEnabled() && cryptoManager.getKeyForBiometric() != null && BiometricHelper.isAvailable(activity)
    }

    fun attemptUnlock() {
        if (pw.isBlank()) {
            error = "Enter your master password"
            return
        }
        val key = cryptoManager.unlock(pw.toCharArray())
        if (key == null) {
            error = "Incorrect password. Try again."
            scope.launch {
                shake.animateTo(14f, tween(60)); shake.animateTo(-14f, tween(60))
                shake.animateTo(8f, tween(60)); shake.animateTo(0f, tween(60))
            }
        } else {
            onUnlocked(key)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(BgElev, Bg)))
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(BgCard, Color3D))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Accent2, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("WELCOME BACK", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Enter your master password to unlock your vault.", textAlign = TextAlign.Center, fontSize = 13.sp, color = TextDim)
        Spacer(Modifier.height(26.dp))

        OutlinedTextField(
            value = pw, onValueChange = { pw = it; error = null },
            label = { Text("Master password") },
            singleLine = true,
            isError = error != null,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                autoCorrect = false
            ),
            trailingIcon = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Toggle password visibility", tint = TextDim)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shake.value }
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = Accent2, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(20.dp))

        val interaction = remember { MutableInteractionSource() }
        Button(
            onClick = { attemptUnlock() },
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().height(52.dp).then(press3D(interaction)),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text("Unlock", fontWeight = FontWeight.Bold)
        }

        if (biometricReady) {
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = {
                BiometricHelper.prompt(
                    activity,
                    onSuccess = {
                        val key = cryptoManager.getKeyForBiometric()
                        if (key != null) {
                            onUnlocked(key)
                        } else {
                            error = "Biometric key unavailable — please use your password"
                        }
                    },
                    onError = { code, message ->
                        // Error codes 10 (user canceled) and 13 (tapped negative button) mean the
                        // person deliberately backed out — no need to show a scary error for those.
                        if (code != 10 && code != 13) {
                            error = "Biometric unlock failed: $message"
                            scope.launch {
                                shake.animateTo(14f, tween(60)); shake.animateTo(-14f, tween(60))
                                shake.animateTo(8f, tween(60)); shake.animateTo(0f, tween(60))
                            }
                        }
                    }
                )
            }) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Accent2)
                Spacer(Modifier.width(8.dp))
                Text("Unlock with biometrics", color = Accent2, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
