package com.vaultra.app.ui.screens

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.vaultra.app.crypto.CryptoManager
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.BiometricHelper
import com.vaultra.app.util.FullBackupManager
import com.vaultra.app.util.Validators
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    activity: FragmentActivity,
    cryptoManager: CryptoManager,
    db: VaultDatabase,
    sessionKey: ByteArray,
    onSessionKeyChanged: (ByteArray) -> Unit,
    onDbChanged: (VaultDatabase) -> Unit,
    onLock: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var autoLock by remember { mutableStateOf(cryptoManager.getAutoLockMinutes()) }
    var biometricOn by remember { mutableStateOf(cryptoManager.isBiometricEnabled()) }
    var showChangePw by remember { mutableStateOf(false) }
    var autoLockMenu by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    androidx.activity.compose.BackHandler(enabled = true) { onBack() }

    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingImportUri = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Accent2) }
            Text("SETTINGS", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            InfoButton(
                title = "Settings",
                purpose = "Control how your vault locks, backs up, and unlocks.",
                howToUse = "Set an auto-lock time, turn on biometric unlock, back up your vault, or change your master password from here.",
                tips = "Export a full backup regularly — it's the only way to recover your vault if you lose this device.",
                securityNote = "Biometric unlock stores your vault key inside your device's hardware-backed Keystore, never in plain form."
            )
        }
        Spacer(Modifier.height(20.dp))

        SettingsGroup {
            SettingsRow(
                title = "Auto-lock", subtitle = "Lock vault after inactivity",
                icon = Icons.Filled.Timer,
                trailing = {
                    Box {
                        TextButton(onClick = { autoLockMenu = true }) {
                            Text(if (autoLock == 0) "Never" else "$autoLock min", color = Accent2, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = autoLockMenu, onDismissRequest = { autoLockMenu = false }) {
                            listOf(1, 2, 5, 0).forEach { mins ->
                                DropdownMenuItem(text = { Text(if (mins == 0) "Never" else "$mins min") }, onClick = {
                                    autoLock = mins
                                    cryptoManager.setAutoLockMinutes(mins)
                                    autoLockMenu = false
                                })
                            }
                        }
                    }
                }
            )
            HorizontalDivider(color = Line, thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
            SettingsRow(
                title = "Biometric unlock", subtitle = "Use fingerprint or face instead of typing",
                icon = Icons.Filled.Fingerprint,
                trailing = {
                    Switch(
                        checked = biometricOn,
                        onCheckedChange = { enable ->
                            // ---- validation: only enable if hardware/enrollment actually supports it ----
                            if (enable) {
                                if (!BiometricHelper.isAvailable(activity)) {
                                    Toast.makeText(context, "No biometric hardware enrolled on this device", Toast.LENGTH_SHORT).show()
                                    return@Switch
                                }
                                BiometricHelper.prompt(
                                    activity,
                                    onSuccess = {
                                        cryptoManager.storeKeyForBiometric(sessionKey)
                                        cryptoManager.setBiometricEnabled(true)
                                        biometricOn = true
                                    },
                                    onError = { code, message ->
                                        if (code != 10 && code != 13) {
                                            Toast.makeText(context, "Biometric setup failed: $message", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            } else {
                                cryptoManager.clearBiometricKey()
                                cryptoManager.setBiometricEnabled(false)
                                biometricOn = false
                            }
                        },
                        colors = vaultraSwitchColors()
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsGroup {
            SettingsRow(title = "Change master password", subtitle = "Re-encrypts your entire vault", icon = Icons.Filled.Key, clickable = { showChangePw = true })
            HorizontalDivider(color = Line, thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
            SettingsRow(title = "Backup & Restore", subtitle = "Export or import your vault data", icon = Icons.Filled.SettingsBackupRestore, clickable = { showBackupDialog = true })
            HorizontalDivider(color = Line, thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
            SettingsRow(title = "Lock now", subtitle = "Return to the unlock screen", icon = Icons.Filled.Lock, clickable = { onLock() })
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "My Secret Vault stores everything only on this device, encrypted with AES-256 (SQLCipher). Nothing is ever sent to a server.",
            color = TextDim, fontSize = 12.sp
        )
    }

    if (showChangePw) {
        ChangePasswordDialog(
            cryptoManager = cryptoManager,
            activity = activity,
            db = db,
            sessionKey = sessionKey,
            onDismiss = { showChangePw = false },
            onSuccess = { newKey, newDb ->
                onSessionKeyChanged(newKey)
                onDbChanged(newDb)
                if (biometricOn) cryptoManager.storeKeyForBiometric(newKey)
                showChangePw = false
            }
        )
    }

    if (showBackupDialog) {
        BackupRestoreDialog(
            cryptoManager = cryptoManager,
            db = db,
            sessionKey = sessionKey,
            onDismiss = { showBackupDialog = false },
            onRestore = { backupPicker.launch(arrayOf("application/zip", "*/*")) },
            onSessionKeyChanged = onSessionKeyChanged,
            onDbChanged = onDbChanged,
            activity = activity
        )
    }

    pendingImportUri?.let { uri ->
        JsonImportDialog(
            db = db,
            uri = uri,
            onDismiss = { pendingImportUri = null },
            cryptoManager = cryptoManager,
            sessionKey = sessionKey,
            onSessionKeyChanged = onSessionKeyChanged,
            onDbChanged = onDbChanged,
            activity = activity
        )
    }
}

@Composable
private fun BackupRestoreDialog(
    cryptoManager: CryptoManager,
    db: VaultDatabase,
    sessionKey: ByteArray,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onSessionKeyChanged: (ByteArray) -> Unit,
    onDbChanged: (VaultDatabase) -> Unit,
    activity: FragmentActivity
) {
    var showExportDialog by remember { mutableStateOf(false) }

    if (showExportDialog) {
        JsonExportDialog(cryptoManager = cryptoManager, db = db, sessionKey = sessionKey, onDismiss = { showExportDialog = false; onDismiss() })
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup & Restore") },
        text = {
            Column {
                Text("Export a full backup (including photos) as an encrypted .zip file, or restore from a previous backup.")
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Button(onClick = { showExportDialog = true }) { Text("Export") }
                    Button(onClick = { onRestore(); onDismiss() }) { Text("Restore") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** Prompts for the password to encrypt the export with, shows progress, then result. */
@Composable
private fun JsonExportDialog(
    cryptoManager: CryptoManager,
    db: VaultDatabase,
    sessionKey: ByteArray,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // val activity = context as? FragmentActivity // Not needed here
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = { Text("Export Full Backup") },
        text = {
            Column {
                Text("Choose a password to protect this backup. It covers passwords, cards, documents, and their attached photos. You'll need this password to restore.", color = TextDim, fontSize = 12.5.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    password, { password = it; error = null }, label = { Text("Backup password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        autoCorrect = false
                    ),
                    singleLine = true, enabled = !isExporting, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    confirm, { confirm = it; error = null }, label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        autoCorrect = false
                    ),
                    singleLine = true, enabled = !isExporting, modifier = Modifier.fillMaxWidth()
                )
                if (isExporting) {
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
            TextButton(enabled = !isExporting, onClick = {
                val strengthErr = Validators.masterPasswordError(password)
                val matchErr = Validators.confirmMismatch(password, confirm)
                val problem = strengthErr ?: matchErr
                if (problem != null) { error = problem; return@TextButton }
                isExporting = true
                scope.launch {
                    val uri = FullBackupManager.export(
                        context = context,
                        db = db,
                        password = password.toCharArray(),
                        masterKey = sessionKey,
                        cryptoManager = cryptoManager
                    )
                    isExporting = false
                    if (uri != null) {
                        Toast.makeText(
                            context,
                            "Backup saved to Downloads folder.",
                            Toast.LENGTH_LONG
                        ).show()
                        onDismiss()
                    } else {
                        error = "Export failed — please try again"
                    }
                }
            }) { Text("Export", color = Accent2, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(enabled = !isExporting, onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Prompts for the backup's password, decrypts + validates it, then merges everything in, skipping duplicates. */
@Composable
private fun JsonImportDialog(
    db: VaultDatabase,
    uri: Uri,
    onDismiss: () -> Unit,
    cryptoManager: CryptoManager,
    sessionKey: ByteArray,
    onSessionKeyChanged: (ByteArray) -> Unit,
    onDbChanged: (VaultDatabase) -> Unit,
    activity: FragmentActivity
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = { Text("Restore from Backup") },
        text = {
            Column {
                Text("Enter the password this backup file was protected with.", color = TextDim, fontSize = 12.5.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    password, { password = it; error = null }, label = { Text("Backup password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        autoCorrect = false
                    ),
                    singleLine = true, enabled = !isImporting, modifier = Modifier.fillMaxWidth()
                )
                if (isImporting) {
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
            TextButton(enabled = !isImporting, onClick = {
                if (password.isBlank()) { error = "Enter the backup password"; return@TextButton }
                isImporting = true
                scope.launch {
                    try {
                        val outcome = FullBackupManager.import(
                            context = context,
                            uri = uri,
                            password = password.toCharArray()
                        )
                        outcome.entries.forEach { db.entryDao().upsert(it) }
                        outcome.cards.forEach { db.cardDao().upsert(it) }
                        outcome.documents.forEach { db.documentDao().upsert(it) }
                        outcome.documentCategories.forEach { db.documentCategoryDao().upsert(it) }
                        outcome.documentFolders.forEach { db.documentFolderDao().upsert(it) }
                        outcome.fuelEntries.forEach { db.fuelDao().upsert(it) }
                        outcome.todos.forEach { db.todoDao().upsert(it) }
                        outcome.todoCategories.forEach { db.todoCategoryDao().upsert(it) }
                        outcome.diaryEntries.forEach { db.diaryDao().upsert(it) }

                        // Critical: the backup's password becomes this device's new master password,
                        // then the vault DB is re-encrypted (and reloaded) under that same key.
                        val newKey = cryptoManager.reKeyFromRestore(password.toCharArray())
                        VaultDatabase.reencrypt(activity, sessionKey, newKey)
                        val newDb = VaultDatabase.getInstance(activity, newKey)
                        onSessionKeyChanged(newKey)
                        onDbChanged(newDb)

                        isImporting = false
                        Toast.makeText(context, "Restore complete. Your vault has been re-encrypted with the backup's password.", Toast.LENGTH_LONG).show()
                        onDismiss()
                    } catch (e: FullBackupManager.IncorrectBackupPasswordException) {
                        isImporting = false
                        error = "Incorrect Backup Password"
                    } catch (e: Exception) {
                        isImporting = false
                        error = "Couldn't read this backup — the file may be corrupted or invalid"
                    }
                }
            }) { Text("Restore", color = Accent2, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(enabled = !isImporting, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ChangePasswordDialog(
    cryptoManager: CryptoManager,
    activity: FragmentActivity,
    db: VaultDatabase,
    sessionKey: ByteArray,
    onDismiss: () -> Unit,
    onSuccess: (ByteArray, VaultDatabase) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change master password") },
        text = {
            Column {
                OutlinedTextField(
                    current, { current = it; error = null }, label = { Text("Current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrect = false),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    newPw, { newPw = it; error = null }, label = { Text("New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrect = false),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    confirmPw, { confirmPw = it; error = null }, label = { Text("Confirm new password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrect = false),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Accent2, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // ---- validation logic ----
                val verifyKey = cryptoManager.unlock(current.toCharArray())
                if (verifyKey == null) { error = "Current password is incorrect"; return@TextButton }
                val strengthErr = Validators.masterPasswordError(newPw)
                if (strengthErr != null) { error = strengthErr; return@TextButton }
                val matchErr = Validators.confirmMismatch(newPw, confirmPw)
                if (matchErr != null) { error = matchErr; return@TextButton }
                if (newPw == current) { error = "New password must be different"; return@TextButton }

                scope.launch {
                    val newKey = cryptoManager.changeMasterPassword(newPw.toCharArray())
                    VaultDatabase.reencrypt(activity, sessionKey, newKey)
                    val newDb = VaultDatabase.getInstance(activity, newKey)
                    onSuccess(newKey, newDb)
                }
            }) { Text("Update", color = Accent2, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard)) { content() }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    clickable: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable != null) Modifier.clickable { clickable() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Accent2, modifier = Modifier.size(19.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        trailing?.invoke()
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }
