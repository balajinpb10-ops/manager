package com.vaultra.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.vaultra.app.data.Entry
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.PasswordGenerator
import com.vaultra.app.util.Totp
import com.vaultra.app.util.Validators
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val CATS = listOf("Login", "Banking", "Social", "Work", "Wifi", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEntrySheet(existing: Entry?, db: VaultDatabase, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var showPw by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(existing?.url ?: "") }
    var totp by remember { mutableStateOf(existing?.totpSecret ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Login") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    var nameErr by remember { mutableStateOf<String?>(null) }
    var urlErr by remember { mutableStateOf<String?>(null) }
    var totpErr by remember { mutableStateOf<String?>(null) }
    var usernameWarning by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            Text(if (existing == null) "New Entry" else "Edit Entry", fontWeight = FontWeight.Black, fontSize = 19.sp, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameErr = null },
                label = { Text("Name") }, placeholder = { Text("e.g. Instagram") },
                isError = nameErr != null, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            nameErr?.let { ErrorText(it) }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    usernameWarning = if (Validators.usernameLooksLikeEmailButIsnt(it)) "That doesn't look like a complete email" else null
                },
                label = { Text("Username / Email") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            usernameWarning?.let { WarningText(it) }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { password = PasswordGenerator.generate(PasswordGenerator.Options()); showPw = true }) {
                            Icon(Icons.Filled.Autorenew, contentDescription = "Generate password", tint = Accent2)
                        }
                        IconButton(onClick = { showPw = !showPw }) {
                            Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Show password", tint = TextDim)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = url, onValueChange = { url = it; urlErr = null },
                label = { Text("Website / URL") }, singleLine = true,
                isError = urlErr != null,
                modifier = Modifier.fillMaxWidth()
            )
            urlErr?.let { ErrorText(it) }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = totp, onValueChange = { totp = it; totpErr = null },
                label = { Text("2FA secret (optional, Base32)") }, singleLine = true,
                isError = totpErr != null,
                modifier = Modifier.fillMaxWidth()
            )
            totpErr?.let { ErrorText(it) }
            Spacer(Modifier.height(12.dp))

            Text("Category", fontSize = 12.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CATS.size) { i ->
                    val c = CATS[i]
                    val selected = c == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (selected) Accent else BgCard)
                            .clickable { category = c }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text(c, fontSize = 12.sp, color = if (selected) Color.White else TextDim, fontWeight = FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        // ---- validation logic before save ----
                        val nErr = Validators.entryNameError(name)
                        val uErr = Validators.urlError(url)
                        val tErr = Validators.totpSecretError(totp)
                        nameErr = nErr; urlErr = uErr; totpErr = tErr
                        if (nErr != null || uErr != null || tErr != null) return@Button

                        val entry = Entry(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(), username = username.trim(), password = password,
                            url = url.trim(), notes = notes.trim(), category = category,
                            totpSecret = totp.trim(), updatedAt = System.currentTimeMillis()
                        )
                        scope.launch {
                            db.entryDao().upsert(entry)
                            onDismiss()
                            Toast.makeText(context, if (existing == null) "Entry added" else "Entry updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, color = Accent2, fontSize = 11.5.sp)
}

@Composable
private fun WarningText(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, color = Warn, fontSize = 11.5.sp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailSheet(entry: Entry, db: VaultDatabase, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showPw by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun copy(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colorForName(entry.name)),
                    contentAlignment = Alignment.Center
                ) { Text(entry.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.name, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
                    Text(entry.category, fontSize = 12.sp, color = TextDim)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextDim) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Accent2) }
            }
            Spacer(Modifier.height(16.dp))

            if (entry.username.isNotBlank()) {
                DetailRow(label = "Username", value = entry.username, onCopy = { copy("Username", entry.username) })
                Spacer(Modifier.height(10.dp))
            }

            // ---- Password row with explicit view/hide toggle ----
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)
            ) {
                Text("PASSWORD", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showPw) entry.password else "•".repeat(entry.password.length.coerceAtLeast(8)),
                        color = TextPrimary, fontSize = 14.5.sp, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "View password", tint = Accent2)
                    }
                    IconButton(onClick = { copy("Password", entry.password) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy password", tint = Accent2)
                    }
                }
            }

            if (entry.totpSecret.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                var code by remember { mutableStateOf(Totp.currentCode(entry.totpSecret) ?: "------") }
                var secondsLeft by remember { mutableStateOf(Totp.secondsRemaining()) }
                LaunchedEffect(entry.totpSecret) {
                    while (true) {
                        code = Totp.currentCode(entry.totpSecret) ?: "------"
                        secondsLeft = Totp.secondsRemaining()
                        delay(1000)
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
                    Text("2FA CODE · refreshes in ${secondsLeft}s", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(code, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        IconButton(onClick = { copy("Code", code) }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code", tint = Accent2) }
                    }
                }
            }

            if (entry.url.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                DetailRow(label = "Website", value = entry.url, onCopy = { copy("URL", entry.url) })
            }
            if (entry.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                DetailRow(label = "Notes", value = entry.notes, onCopy = null)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        db.entryDao().delete(entry)
                        confirmDelete = false
                        onDismiss()
                    }
                }) { Text("Delete", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, onCopy: (() -> Unit)?) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
        Text(label.uppercase(), fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = TextPrimary, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
            if (onCopy != null) {
                IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Accent2) }
            }
        }
    }
}
