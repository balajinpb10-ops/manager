package com.vaultra.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.DocumentEntry
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.DocumentValidators
import com.vaultra.app.util.ImageStore
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun DocumentsScreen(db: VaultDatabase) {
    val documents by db.documentDao().getAll().collectAsState(initial = emptyList())
    var editTarget by remember { mutableStateOf<DocumentEntry?>(null) }
    var showAddEdit by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<DocumentEntry?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MY DOCUMENTS", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                InfoButton(
                    title = "Your Documents",
                    purpose = "Keep ID document numbers — Aadhaar, PAN, passport, and more — safe and handy.",
                    howToUse = "Tap + to add a document. Numbers stay masked until you tap the eye icon to reveal them.",
                    tips = "Use the Notes field for extra context, like where the physical copy is stored.",
                    securityNote = "Document numbers are encrypted at rest the same as your passwords — AES-256 via SQLCipher."
                )
            }

            if (documents.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.size(70.dp).clip(RoundedCornerShape(20.dp)).background(BgCard), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Badge, contentDescription = null, tint = TextDim, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("No documents yet", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Tap the + button to add your first document.", color = TextDim, fontSize = 12.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(documents, key = { it.id }) { doc ->
                        DocumentCard(doc = doc, onClick = { detailTarget = doc })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = { editTarget = null; showAddEdit = true },
            containerColor = Accent, contentColor = Color.White, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "Add document") }
    }

    if (showAddEdit) {
        DocumentFormSheet(existing = editTarget, db = db, onDismiss = { showAddEdit = false })
    }
    detailTarget?.let { doc ->
        DocumentDetailSheet(
            doc = doc, db = db,
            onDismiss = { detailTarget = null },
            onEdit = { detailTarget = null; editTarget = doc; showAddEdit = true }
        )
    }
}

@Composable
private fun DocumentCard(doc: DocumentEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth().then(press3D(interaction))
            .clip(RoundedCornerShape(16.dp)).background(BgCard)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colorForName(doc.docType)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Badge, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(doc.docType, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = TextPrimary)
            Text(DocumentValidators.mask(doc.docNumber), fontSize = 12.5.sp, color = TextDim)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextDim)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentFormSheet(existing: DocumentEntry?, db: VaultDatabase, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var docType by remember { mutableStateOf(existing?.docType?.let { d -> if (d in DocumentValidators.DOC_TYPES) d else "Other (Custom)" } ?: DocumentValidators.DOC_TYPES.first()) }
    var customType by remember { mutableStateOf(existing?.docType?.let { d -> if (d !in DocumentValidators.DOC_TYPES) d else "" } ?: "") }
    var holder by remember { mutableStateOf(existing?.holderName ?: "") }
    var number by remember { mutableStateOf(existing?.docNumber ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var images by remember { mutableStateOf(existing?.images ?: emptyList()) }
    val originalImages = remember { existing?.images ?: emptyList() }
    var numberErr by remember { mutableStateOf<String?>(null) }
    var typeMenuOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Text(if (existing == null) "Add Document" else "Edit Document", fontWeight = FontWeight.Black, fontSize = 19.sp, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            Box {
                OutlinedTextField(
                    value = docType, onValueChange = {}, enabled = false, label = { Text("Document type") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = TextPrimary, disabledBorderColor = Line,
                        disabledLabelColor = TextDim, disabledTrailingIconColor = TextDim
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                // Disabled fields don't consume touches, so this transparent overlay reliably
                // catches the tap and opens the menu — fixes the dropdown not responding.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { typeMenuOpen = true }
                )
                DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                    DocumentValidators.DOC_TYPES.forEach { t ->
                        DropdownMenuItem(text = { Text(t) }, onClick = { docType = t; typeMenuOpen = false; numberErr = null })
                    }
                }
            }
            if (docType == "Other (Custom)") {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(customType, { customType = it }, label = { Text("Custom document type name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(holder, { holder = it }, label = { Text("Holder name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(number, { number = it; numberErr = null }, label = { Text("Document number") }, singleLine = true, isError = numberErr != null, modifier = Modifier.fillMaxWidth())
            numberErr?.let { Spacer(Modifier.height(4.dp)); Text(it, color = Accent2, fontSize = 11.5.sp) }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(14.dp))
            AttachmentsPicker(
                images = images,
                onImagesAdded = { added -> images = images + added },
                onImageRemoved = { path -> images = images - path },
                subfolder = "documents"
            )

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val abandoned = images - originalImages.toSet()
                        if (abandoned.isNotEmpty()) ImageStore.deleteImages(abandoned)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        val err = DocumentValidators.docNumberError(docType, number)
                        numberErr = err
                        if (err != null) return@Button
                        val finalDocType = if (docType == "Other (Custom)") customType.trim().ifBlank { "Other" } else docType
                        val doc = DocumentEntry(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            docType = finalDocType, holderName = holder.trim(), docNumber = number.trim(),
                            notes = notes.trim(), updatedAt = System.currentTimeMillis(),
                            images = images
                        )
                        val removed = originalImages - images.toSet()
                        scope.launch {
                            db.documentDao().upsert(doc)
                            if (removed.isNotEmpty()) ImageStore.deleteImages(removed)
                            onDismiss()
                            Toast.makeText(context, if (existing == null) "Document added" else "Document updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentDetailSheet(doc: DocumentEntry, db: VaultDatabase, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showNumber by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun copy(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(colorForName(doc.docType)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Badge, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(doc.docType, fontWeight = FontWeight.Black, fontSize = 17.sp, color = TextPrimary)
                    if (doc.holderName.isNotBlank()) Text(doc.holderName, fontSize = 12.5.sp, color = TextDim)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextDim) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Accent2) }
            }
            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
                Text("DOCUMENT NUMBER", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showNumber) doc.docNumber else DocumentValidators.mask(doc.docNumber), color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showNumber = !showNumber }) { Icon(if (showNumber) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Toggle number", tint = Accent2) }
                    IconButton(onClick = { copy("Document number", doc.docNumber) }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Accent2) }
                }
            }
            if (doc.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
                    Text("NOTES", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(doc.notes, color = TextPrimary, fontSize = 14.sp)
                }
            }
            if (doc.images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                AttachmentsGallery(doc.images)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this document?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { db.documentDao().delete(doc); ImageStore.deleteImages(doc.images); confirmDelete = false; onDismiss() } }) { Text("Delete", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
