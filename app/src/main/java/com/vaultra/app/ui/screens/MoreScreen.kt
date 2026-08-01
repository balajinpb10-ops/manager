package com.vaultra.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*

private enum class MoreSection { HOME, PRODUCTIVITY }

/** The MORE tab hub (bug fix #6, "Navigation Order"): Settings, Backup & Restore, Security,
 *  and the informational pages all live here now instead of Settings having its own bottom-bar slot. */
@Composable
fun MoreScreen(db: VaultDatabase, onOpenSettings: () -> Unit) {
    var section by remember { mutableStateOf(MoreSection.HOME) }
    var infoDialog by remember { mutableStateOf<String?>(null) }

    when (section) {
        MoreSection.HOME -> Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("MORE", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary)
            Spacer(Modifier.height(18.dp))

            MoreGroup {
                MoreRow("To-Do & Diary", "Tasks, categories, and your private journal", Icons.Filled.CheckCircle) { section = MoreSection.PRODUCTIVITY }
            }
            Spacer(Modifier.height(14.dp))
            MoreGroup {
                MoreRow("Settings", "Auto-lock, biometric unlock, and more", Icons.Filled.Settings, onOpenSettings)
                MoreDivider()
                MoreRow("Backup & Restore", "Export or import your encrypted vault data", Icons.Filled.SettingsBackupRestore, onOpenSettings)
                MoreDivider()
                MoreRow("Security", "Master password and unlock options", Icons.Filled.Shield, onOpenSettings)
            }
            Spacer(Modifier.height(14.dp))
            MoreGroup {
                MoreRow("About", "Version and app details", Icons.Filled.Info) { infoDialog = "about" }
                MoreDivider()
                MoreRow("Help & Support", "Get help using this app", Icons.Filled.HelpOutline) { infoDialog = "help" }
                MoreDivider()
                MoreRow("Privacy Policy", "How your data is handled", Icons.Filled.PrivacyTip) { infoDialog = "privacy" }
                MoreDivider()
                MoreRow("Terms & Conditions", "Usage terms", Icons.Filled.Gavel) { infoDialog = "terms" }
                MoreDivider()
                MoreRow("App Information", "Package and build details", Icons.Filled.Apps) { infoDialog = "appinfo" }
            }
            Spacer(Modifier.height(18.dp))
            Text("Everything in this app is stored locally in your encrypted vault — nothing is ever sent to a server.", color = TextDim, fontSize = 12.sp)
        }
        MoreSection.PRODUCTIVITY -> ProductivityScreen(db = db, onBack = { section = MoreSection.HOME })
    }

    infoDialog?.let { key ->
        InfoDialogFor(key) { infoDialog = null }
    }
}

@Composable
private fun MoreGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard)) { content() }
}

@Composable
private fun MoreDivider() = HorizontalDivider(color = Line, thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))

@Composable
private fun MoreRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Bg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Accent2, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextDim, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InfoDialogFor(key: String, onDismiss: () -> Unit) {
    val (title, body) = when (key) {
        "about" -> "About" to "Vaultra is a fully offline, serverless personal vault. All passwords, cards, documents, tasks, and journal entries are encrypted and stored only on this device."
        "help" -> "Help & Support" to "For questions about using the app, check the info button (ⓘ) available on most screens for context-specific guidance."
        "privacy" -> "Privacy Policy" to "Vaultra collects no data and has no servers. Everything you enter stays encrypted on this device and is never transmitted anywhere."
        "terms" -> "Terms & Conditions" to "Vaultra is provided as-is for personal use. You are responsible for remembering your master password and keeping backups — data cannot be recovered without them."
        else -> "App Information" to "Vaultra — a local-first, encrypted personal vault covering passwords, cards, documents, fuel logs, tasks, and a private diary."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, color = TextDim, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Accent2, fontWeight = FontWeight.Bold) } },
        containerColor = BgElev
    )
}
