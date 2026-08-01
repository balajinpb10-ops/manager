package com.vaultra.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.PasswordGenerator

@Composable
fun GeneratorScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var length by remember { mutableStateOf(16f) }
    var lower by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var numbers by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }

    fun options() = PasswordGenerator.Options(length.toInt(), lower, upper, numbers, symbols)
    var password by remember { mutableStateOf(PasswordGenerator.generate(options())) }
    val strength = remember(password) { PasswordGenerator.strengthOf(password) }

    fun regenerate() {
        // ---- validation: require at least one character set enabled ----
        if (!lower && !upper && !numbers && !symbols) {
            lower = true // fall back rather than generating from an empty pool
        }
        password = PasswordGenerator.generate(options())
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("GENERATOR", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            InfoButton(
                title = "Password Generator",
                purpose = "Creates strong, random passwords so you never have to invent one yourself.",
                howToUse = "Adjust the length slider and toggle which character types to include, then tap Copy to use it, or Regenerate for a new one.",
                tips = "Aim for 16+ characters with all four toggles on for the strongest passwords.",
                securityNote = "Passwords are generated locally using your device's secure random number generator — nothing is sent anywhere."
            )
        }
        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BgCard).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(password, color = TextPrimary, fontSize = 19.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { strength.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                color = Color(strength.colorHex), trackColor = Line
            )
            Spacer(Modifier.height(6.dp))
            Text("${strength.label} password", color = TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val regenInteraction = remember { MutableInteractionSource() }
            OutlinedButton(
                onClick = { regenerate() },
                interactionSource = regenInteraction,
                modifier = Modifier.weight(1f).then(press3D(regenInteraction))
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Regenerate")
            }
            val copyInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(password))
                    Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show()
                },
                interactionSource = copyInteraction,
                modifier = Modifier.weight(1f).then(press3D(copyInteraction)),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Copy", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Length", color = TextDim, fontSize = 13.sp)
            Text(length.toInt().toString(), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Slider(
            value = length, onValueChange = { length = it; regenerate() },
            valueRange = 6f..32f, steps = 25,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Line)
        )

        ToggleRow("Lowercase (a-z)", lower) { lower = it; regenerate() }
        ToggleRow("Uppercase (A-Z)", upper) { upper = it; regenerate() }
        ToggleRow("Numbers (0-9)", numbers) { numbers = it; regenerate() }
        ToggleRow("Symbols (!@#\$)", symbols) { symbols = it; regenerate() }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp)
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = vaultraSwitchColors()
        )
    }
}
