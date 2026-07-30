package com.vaultra.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Attachment picker used in the task editor. Unlike [AttachmentsPicker] (photos only),
 * this accepts images, PDFs, and common document types for To-Do attachments.
 */
@Composable
fun TodoAttachmentsPicker(
    paths: List<String>,
    onAdded: (List<String>) -> Unit,
    onRemoved: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { ImageStore.importFiles(context, uris, "todos") }
                if (saved.isNotEmpty()) onAdded(saved)
            }
        }
    }

    Text("Attachments", fontSize = 12.sp, color = TextDim, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paths.forEach { path ->
            AttachmentRow(path = path, onRemove = { onRemoved(path) })
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .clickable {
                    picker.launch(arrayOf("image/*", "application/pdf", "text/plain", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, tint = Accent2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add image, PDF or document", color = Accent2, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AttachmentRow(path: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val isImage = remember(path) { ImageStore.isImage(path) }
    val fileName = remember(path) { File(path).name.substringAfter('_', File(path).name) }

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgCard).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Bg), contentAlignment = Alignment.Center) {
            if (isImage) {
                AsyncImage(model = File(path), contentDescription = "Attached image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
            } else {
                Icon(Icons.Filled.Description, contentDescription = "Attached file", tint = Accent2, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = File(path).extension.uppercase().ifBlank { "FILE" },
            color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Attachment options", tint = TextDim)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("View") }, leadingIcon = { Icon(Icons.Filled.Visibility, null) }, onClick = {
                    menuOpen = false
                    if (!ImageStore.viewExternally(context, path)) Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                })
                DropdownMenuItem(text = { Text("Download") }, leadingIcon = { Icon(Icons.Filled.Download, null) }, onClick = {
                    menuOpen = false
                    val ok = ImageStore.exportAnyToDownloads(context, path)
                    ImageStore.showDownloadResult(context, ok)
                })
                DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Accent2) }, onClick = {
                    menuOpen = false
                    onRemove()
                })
            }
        }
    }
}

/** Read-only attachments strip shown on the task detail sheet, with view/download only (no remove). */
@Composable
fun TodoAttachmentsGallery(paths: List<String>) {
    if (paths.isEmpty()) return
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
        Text("ATTACHMENTS", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            paths.forEach { path ->
                val isImage = remember(path) { ImageStore.isImage(path) }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg)
                        .clickable {
                            if (!ImageStore.viewExternally(context, path)) Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(BgElev), contentAlignment = Alignment.Center) {
                        if (isImage) {
                            AsyncImage(model = File(path), contentDescription = "Attached image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
                        } else {
                            Icon(Icons.Filled.Description, contentDescription = "Attached file", tint = Accent2, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(File(path).extension.uppercase().ifBlank { "FILE" }, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val ok = ImageStore.exportAnyToDownloads(context, path)
                        ImageStore.showDownloadResult(context, ok)
                    }) { Icon(Icons.Filled.Download, contentDescription = "Download", tint = Accent2, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}
