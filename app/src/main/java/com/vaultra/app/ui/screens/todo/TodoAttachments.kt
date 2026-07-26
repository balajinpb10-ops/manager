package com.vaultra.app.ui.screens.todo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val AttachmentMimeTypes = arrayOf(
    "image/*",
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain"
)

/**
 * Attachment picker + thumbnail strip for the task editor. Accepts images, PDFs, and
 * documents — everything is copied into this app's private storage as soon as it's
 * picked, same as the rest of the app's attachment handling.
 */
@Composable
fun TodoAttachmentsPicker(
    paths: List<String>,
    onAdded: (List<String>) -> Unit,
    onRemoved: (String) -> Unit,
    onReplace: (old: String, added: List<String>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var replacingPath by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { ImageStore.importFiles(context, uris, "todo") }
                if (saved.isNotEmpty()) {
                    val toReplace = replacingPath
                    if (toReplace != null) onReplace(toReplace, saved) else onAdded(saved)
                }
                replacingPath = null
            }
        } else {
            replacingPath = null
        }
    }

    Text("ATTACHMENTS", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(paths, key = { it }) { path ->
            AttachmentThumb(
                path = path,
                onRemove = { onRemoved(path) },
                onReplace = { replacingPath = path; picker.launch(AttachmentMimeTypes) }
            )
        }
        item {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .clickable { replacingPath = null; picker.launch(AttachmentMimeTypes) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Add attachment", tint = Accent2)
            }
        }
    }
}

@Composable
private fun AttachmentThumb(path: String, onRemove: () -> Unit, onReplace: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.size(72.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .clickable { showMenu = true }
        ) {
            if (ImageStore.isImagePath(path)) {
                AsyncImage(
                    model = File(path), contentDescription = "Attached image",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (ImageStore.isPdfPath(path)) Icons.Filled.PictureAsPdf else Icons.Filled.Description,
                        contentDescription = "Document", tint = Accent2, modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        File(path).extension.uppercase(), fontSize = 9.sp, color = TextDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Remove attachment", tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
    if (showMenu) {
        AttachmentActionsDialog(
            path = path,
            onDismiss = { showMenu = false },
            onReplace = { showMenu = false; onReplace() },
            onRemove = { showMenu = false; onRemove() }
        )
    }
}

/** View / Download / Replace / Delete sheet for a single attachment, opened from the
 *  thumbnail strip above or the task detail gallery below. */
@Composable
private fun AttachmentActionsDialog(path: String, onDismiss: () -> Unit, onReplace: (() -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    val context = LocalContext.current
    val isImage = ImageStore.isImagePath(path)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BgElev).padding(16.dp)
        ) {
            if (isImage) {
                AsyncImage(
                    model = File(path), contentDescription = "Attachment preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(Bg),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (ImageStore.isPdfPath(path)) Icons.Filled.PictureAsPdf else Icons.Filled.Description,
                        contentDescription = null, tint = Accent2, modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(File(path).name, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ImageStore.readableSize(path), color = TextDim, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val ok = ImageStore.exportFileToDownloads(context, path)
                    ImageStore.showDownloadResult(context, ok)
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("Download", fontSize = 12.sp)
                }
                if (onReplace != null) {
                    OutlinedButton(onClick = onReplace, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("Replace", fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close", color = TextDim) }
                if (onRemove != null) {
                    Button(
                        onClick = onRemove, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("Delete", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Read-only attachments gallery shown on the task detail page. */
@Composable
fun TodoAttachmentsGallery(paths: List<String>) {
    if (paths.isEmpty()) return
    var viewingPath by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
        Text("ATTACHMENTS", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(paths, key = { it }) { path ->
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg)
                        .clickable { viewingPath = path }
                ) {
                    if (ImageStore.isImagePath(path)) {
                        AsyncImage(
                            model = File(path), contentDescription = "Attachment",
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (ImageStore.isPdfPath(path)) Icons.Filled.PictureAsPdf else Icons.Filled.Description,
                                contentDescription = null, tint = Accent2, modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(File(path).extension.uppercase(), fontSize = 9.sp, color = TextDim)
                        }
                    }
                }
            }
        }
    }

    viewingPath?.let { path ->
        AttachmentActionsDialog(path = path, onDismiss = { viewingPath = null })
    }
}
