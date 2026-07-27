package com.vaultra.app.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val DOC_ATTACHMENT_MIME_TYPES = arrayOf(
    "image/*", "application/pdf", "text/plain",
    "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
)

/** Unlimited-attachment picker for the Documents module: camera capture, gallery, or file manager. */
@Composable
fun DocumentAttachmentsEditor(
    paths: List<String>,
    onAdded: (List<String>) -> Unit,
    onRemoved: (String) -> Unit,
    onReplaced: (old: String, new: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var replaceTarget by remember { mutableStateOf<String?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val saved = withContext(Dispatchers.IO) { ImageStore.importFiles(context, uris, "documents") }
            if (saved.isNotEmpty()) onAdded(saved)
        }
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val saved = withContext(Dispatchers.IO) { ImageStore.importFiles(context, uris, "documents") }
            if (saved.isNotEmpty()) onAdded(saved)
        }
    }
    val replaceGalleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val old = replaceTarget
        if (uri != null && old != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { ImageStore.importFiles(context, listOf(uri), "documents") }
            saved.firstOrNull()?.let { onReplaced(old, it) }
            replaceTarget = null
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        if (success && file != null) onAdded(listOf(file.absolutePath))
        pendingCameraFile = null
    }

    fun launchCamera() {
        val dir = File(context.filesDir, "attachments/documents").apply { if (!exists()) mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }

    Text("ATTACHMENTS", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AttachActionButton("Camera", Icons.Filled.PhotoCamera, Modifier.weight(1f)) { launchCamera() }
        AttachActionButton("Gallery", Icons.Filled.Image, Modifier.weight(1f)) {
            galleryPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        AttachActionButton("Files", Icons.Filled.AttachFile, Modifier.weight(1f)) { filePicker.launch(DOC_ATTACHMENT_MIME_TYPES) }
    }

    if (paths.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.heightIn(max = 320.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(paths.size) { index ->
                val path = paths[index]
                AttachmentThumb(
                    path = path,
                    onClick = { previewIndex = index },
                    onRemove = { onRemoved(path) },
                    onReplace = { replaceTarget = path; replaceGalleryPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onDownload = {
                        val ok = ImageStore.exportAnyToDownloads(context, path)
                        ImageStore.showDownloadResult(context, ok)
                    }
                )
            }
        }
    }

    previewIndex?.let { idx ->
        AttachmentPreviewDialog(paths = paths, startIndex = idx, onDismiss = { previewIndex = null })
    }
}

@Composable
private fun AttachActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(BgCard).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Accent2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Accent2, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AttachmentThumb(path: String, onClick: () -> Unit, onRemove: () -> Unit, onReplace: () -> Unit, onDownload: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val isImage = remember(path) { ImageStore.isImage(path) }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .clickable(onClick = onClick)
    ) {
        if (isImage) {
            AsyncImage(model = File(path), contentDescription = "Attachment", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF", tint = Accent2, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(4.dp))
                Text(File(path).extension.uppercase(), color = TextDim, fontSize = 10.sp)
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("View") }, leadingIcon = { Icon(Icons.Filled.Visibility, null) }, onClick = { menuOpen = false; onClick() })
                DropdownMenuItem(text = { Text("Download") }, leadingIcon = { Icon(Icons.Filled.Download, null) }, onClick = { menuOpen = false; onDownload() })
                if (isImage) DropdownMenuItem(text = { Text("Replace") }, leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) }, onClick = { menuOpen = false; onReplace() })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Accent2) }, onClick = { menuOpen = false; onRemove() })
            }
        }
    }
}

/** Read-only attachments grid used on the document detail sheet. */
@Composable
fun DocumentAttachmentsGallery(paths: List<String>) {
    if (paths.isEmpty()) return
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
        Text("ATTACHMENTS (${paths.size})", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 260.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(paths.size) { index ->
                val path = paths[index]
                val isImage = remember(path) { ImageStore.isImage(path) }
                Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Bg).clickable { previewIndex = index }) {
                    if (isImage) AsyncImage(model = File(path), contentDescription = "Attachment", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF", tint = Accent2, modifier = Modifier.size(24.dp))
                        Text(File(path).extension.uppercase(), color = TextDim, fontSize = 9.sp)
                    }
                }
            }
        }
    }
    previewIndex?.let { idx -> AttachmentPreviewDialog(paths = paths, startIndex = idx, onDismiss = { previewIndex = null }) }
}

/** Full-screen preview: swipe between attachments, pinch-zoom + rotate for images, page navigation for PDFs. */
@Composable
private fun AttachmentPreviewDialog(paths: List<String>, startIndex: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = startIndex) { paths.size }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val path = paths[page]
                if (ImageStore.isImage(path)) ImagePreviewPage(path) else PdfPreviewPage(path)
            }
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 36.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
                Text("${pagerState.currentPage + 1} / ${paths.size}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    val ok = ImageStore.exportAnyToDownloads(context, paths[pagerState.currentPage])
                    ImageStore.showDownloadResult(context, ok)
                }) { Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun ImagePreviewPage(path: String) {
    var scale by remember(path) { mutableStateOf(1f) }
    var offsetX by remember(path) { mutableStateOf(0f) }
    var offsetY by remember(path) { mutableStateOf(0f) }
    var rotation by remember(path) { mutableStateOf(0f) }

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = File(path),
            contentDescription = "Image attachment",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY, rotationZ = rotation)
                .pointerInput(path) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        )
        IconButton(
            onClick = { rotation = (rotation + 90f) % 360f },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) { Icon(Icons.Filled.RotateRight, contentDescription = "Rotate", tint = Color.White) }
    }
}

@Composable
private fun PdfPreviewPage(path: String) {
    var pageIndex by remember(path) { mutableStateOf(0) }
    var pageCount by remember(path) { mutableStateOf(0) }
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(path) { mutableStateOf(1f) }

    LaunchedEffect(path, pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                        if (pageIndex >= renderer.pageCount) return@withContext null
                        renderer.openPage(pageIndex).use { page ->
                            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            } catch (e: Exception) { null }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .pointerInput(path, pageIndex) {
                        detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f) }
                    }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Couldn't render this PDF", color = Color.White)
            }
        }
        if (pageCount > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (pageIndex > 0) { pageIndex--; scale = 1f } }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page", tint = Color.White)
                }
                Text("${pageIndex + 1} / $pageCount", color = Color.White, fontSize = 12.sp)
                IconButton(onClick = { if (pageIndex < pageCount - 1) { pageIndex++; scale = 1f } }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next page", tint = Color.White)
                }
            }
        }
    }
}
