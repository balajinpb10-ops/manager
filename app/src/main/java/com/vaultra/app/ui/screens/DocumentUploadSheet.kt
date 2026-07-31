package com.vaultra.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.ui.theme.*

/** The full upload-options sheet shown when the Documents FAB is tapped (DOC-002 / DOC-003). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentUploadSheet(
    onDismiss: () -> Unit,
    onPickPdf: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickAnyFile: () -> Unit,
    onScan: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateNote: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Text("Add to Documents", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            UploadOptionRow("Upload PDF", Icons.Filled.PictureAsPdf) { onDismiss(); onPickPdf() }
            UploadOptionRow("Upload Image", Icons.Filled.Image) { onDismiss(); onPickImage() }
            UploadOptionRow("Upload Video", Icons.Filled.VideoFile) { onDismiss(); onPickVideo() }
            UploadOptionRow("Upload Audio", Icons.Filled.AudioFile) { onDismiss(); onPickAudio() }
            UploadOptionRow("Upload Any File", Icons.Filled.AttachFile) { onDismiss(); onPickAnyFile() }
            UploadOptionRow("Scan Document", Icons.Filled.DocumentScanner) { onDismiss(); onScan() }
            HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 8.dp))
            UploadOptionRow("Create Folder", Icons.Filled.CreateNewFolder) { onDismiss(); onCreateFolder() }
            UploadOptionRow("Create Secure Note", Icons.Filled.NoteAlt) { onDismiss(); onCreateNote() }
        }
    }
}

/** Compact subset shown on long-press of the FAB - the "Quick Upload Menu" (DOC-003). */
@Composable
fun DocumentQuickUploadMenu(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickPdf: () -> Unit,
    onPickAnyFile: () -> Unit,
    onScan: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Upload") },
        text = {
            Column {
                UploadOptionRow("Upload Image", Icons.Filled.Image) { onDismiss(); onPickImage() }
                UploadOptionRow("Upload PDF", Icons.Filled.PictureAsPdf) { onDismiss(); onPickPdf() }
                UploadOptionRow("Upload Any File", Icons.Filled.AttachFile) { onDismiss(); onPickAnyFile() }
                UploadOptionRow("Scan Document", Icons.Filled.DocumentScanner) { onDismiss(); onScan() }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = BgElev
    )
}

@Composable
private fun UploadOptionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(BgCard), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Accent2, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
    }
}
