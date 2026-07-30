package com.vaultra.app.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small "i" icon meant for the top-right corner of a screen. Tapping it
 * opens a short help dialog explaining the page's purpose, how to use it,
 * and any relevant tips or security notes.
 */
@Composable
fun InfoButton(title: String, purpose: String, howToUse: String, tips: String? = null, securityNote: String? = null) {
    var show by remember { mutableStateOf(false) }

    IconButton(onClick = { show = true }) {
        Icon(Icons.Filled.Info, contentDescription = "About this screen", tint = TextDim)
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(title) },
            text = {
                Column {
                    Text(purpose, fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Text("HOW TO USE", fontSize = 11.sp, color = TextDim)
                    Text(howToUse, fontSize = 13.sp, color = TextPrimary)
                    if (tips != null) {
                        Spacer(Modifier.height(10.dp))
                        Text("TIPS", fontSize = 11.sp, color = TextDim)
                        Text(tips, fontSize = 13.sp, color = TextPrimary)
                    }
                    if (securityNote != null) {
                        Spacer(Modifier.height(10.dp))
                        Text("SECURITY", fontSize = 11.sp, color = TextDim)
                        Text(securityNote, fontSize = 13.sp, color = Accent2)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text("Got it", color = Accent2) }
            }
        )
    }
}
