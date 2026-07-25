package com.vaultra.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.crypto.CryptoManager
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.FullBackupManager
import com.vaultra.app.util.PasswordGenerator
import com.vaultra.app.util.Validators
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(cryptoManager: CryptoManager, onComplete: (ByteArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shake = remember { Animatable(0f) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    val strength = remember(pw) { PasswordGenerator.strengthOf(pw) }

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
                .background(Brush.linearGradient(listOf(BgCard, Color3D)))
                .graphicsLayer { cameraDistance = 12f * density },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Accent2, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("CREATE MASTER\nPASSWORD", textAlign = TextAlign.Center, fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "This is the one password you must remember. My Secret Vault can't recover it if you lose it.",
            textAlign = TextAlign.Center, fontSize = 13.sp, color = TextDim
        )
        Spacer(Modifier.height(26.dp))

        OutlinedTextField(
            value = pw, onValueChange = { pw = it; error = null },
            label = { Text("Master password") },
            singleLine = true,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Toggle password visibility", tint = TextDim)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shake.value }
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { strength.percent / 100f },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)),
            color = androidx.compose.ui.graphics.Color(strength.colorHex),
            trackColor = Line
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it; error = null },
            label = { Text("Confirm password") },
            singleLine = true,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = Accent2, fontSize = 12.5.sp)
        }

        Spacer(Modifier.height(20.dp))
        val interaction = remember { MutableInteractionSource() }
        Button(
            onClick = {
                // ---- validation logic ----
                val strengthErr = Validators.masterPasswordError(pw)
                val matchErr = Validators.confirmMismatch(pw, confirm)
                val problem = strengthErr ?: matchErr
                if (problem != null) {
                    error = problem
                    scope.launch {
                        shake.animateTo(14f, tween(60)); shake.animateTo(-14f, tween(60))
                        shake.animateTo(8f, tween(60)); shake.animateTo(0f, tween(60))
                    }
                    return@Button
                }
                val key = cryptoManager.setupNewVault(pw.toCharArray())
                onComplete(key)
            },
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .then(press3D(interaction)),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text("Create Vault", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(14.dp))
        TextButton(onClick = { backupPicker.launch(arrayOf("application/zip", "*/*")) }) {
            Text("Restore from backup instead", color = Accent2, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }

    restoreUri?.let { uri ->
        RestoreOnWelcomeDialog(
            cryptoManager = cryptoManager,
            context = context,
            uri = uri,
            onDismiss = { restoreUri = null },
            onRestored = { key -> onComplete(key) }
        )
    }
}

/** Restore flow for a fresh install: decrypt the backup, create a new vault keyed to that
 *  same password, and repopulate it — so the restored vault behaves exactly like the original. */
@Composable
private fun RestoreOnWelcomeDialog(
    cryptoManager: CryptoManager,
    context: android.content.Context,
    uri: Uri,
    onDismiss: () -> Unit,
    onRestored: (ByteArray) -> Unit
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        title = { Text("Restore from Backup") },
        text = {
            Column {
                Text("Enter the password this backup was protected with. It will also become your new master password.", color = TextDim, fontSize = 12.5.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(password, { password = it; error = null }, label = { Text("Backup password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !isRestoring, modifier = Modifier.fillMaxWidth())
                if (isRestoring) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Line)
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Accent2, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isRestoring, onClick = {
                if (password.isBlank()) { error = "Enter the backup password"; return@TextButton }
                isRestoring = true
                scope.launch {
                    val outcome = FullBackupManager.import(context, uri, password.toCharArray())
                    if (outcome == null) {
                        isRestoring = false
                        error = "Couldn't read this backup — wrong password, or not a valid Vaultra file"
                        return@launch
                    }
                    val key = cryptoManager.setupNewVault(password.toCharArray())
                    val db = VaultDatabase.getInstance(context, key)
                    outcome.entries.forEach { db.entryDao().upsert(it) }
                    outcome.cards.forEach { db.cardDao().upsert(it) }
                    outcome.documents.forEach { db.documentDao().upsert(it) }
                    outcome.fuelEntries.forEach { db.fuelDao().upsert(it) }
                    outcome.todos.forEach { db.todoDao().upsert(it) }
                    outcome.diaryEntries.forEach { db.diaryDao().upsert(it) }
                    isRestoring = false
                    val total = outcome.entries.size + outcome.cards.size + outcome.documents.size + outcome.fuelEntries.size + outcome.todos.size + outcome.diaryEntries.size
                    Toast.makeText(context, "Restored $total items", Toast.LENGTH_LONG).show()
                    onRestored(key)
                }
            }) { Text("Restore", color = Accent2, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(enabled = !isRestoring, onClick = onDismiss) { Text("Cancel") } }
    )
}

// A slightly deeper accent tone used for gradient card backgrounds
val Color3D = androidx.compose.ui.graphics.Color(0xFF3A0C14)
