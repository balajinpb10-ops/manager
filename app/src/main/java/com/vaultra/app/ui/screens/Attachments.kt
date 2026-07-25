package com.vaultra.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Photo picker + thumbnail strip used inside the Add/Edit Card and Add/Edit
 * Document sheets. Supports picking multiple images at once. Everything stays
 * on-device — pictures are copied into this app's private storage as soon as
 * they're picked.
 */
@Composable
fun AttachmentsPicker(
    images: List<String>,
    onImagesAdded: (List<String>) -> Unit,
    onImageRemoved: (String) -> Unit,
    subfolder: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { ImageStore.importImages(context, uris, subfolder) }
                if (saved.isNotEmpty()) onImagesAdded(saved)
            }
        }
    }

    Text("Photos", fontSize = 12.sp, color = TextDim, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(images, key = { it }) { path ->
            Box(modifier = Modifier.size(72.dp)) {
                AsyncImage(
                    model = File(path), contentDescription = "Attached photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(20.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { onImageRemoved(path) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = "Add photos", tint = Accent2)
            }
        }
    }
}

/**
 * Horizontal thumbnail strip shown on Card/Document detail sheets, with a
 * tap-to-view full-screen dialog that also lets the user download a copy to
 * the device's public Downloads/Vaultra folder.
 */
@Composable
fun AttachmentsGallery(images: List<String>) {
    if (images.isEmpty()) return
    var viewerPath by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
        Text("ATTACHMENTS", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(images, key = { it }) { path ->
                AsyncImage(
                    model = File(path), contentDescription = "Attached photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C20))
                        .clickable { viewerPath = path }
                )
            }
        }
    }

    viewerPath?.let { path -> ImageViewerDialog(path = path, onDismiss = { viewerPath = null }) }
}

@Composable
private fun ImageViewerDialog(path: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
        ) {
            AsyncImage(
                model = File(path), contentDescription = "Attached photo, full size",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(420.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
                Button(
                    onClick = {
                        val ok = ImageStore.exportToDownloads(context, path)
                        ImageStore.showDownloadResult(context, ok)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
