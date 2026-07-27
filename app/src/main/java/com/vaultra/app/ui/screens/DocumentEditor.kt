package com.vaultra.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.DocumentCategory
import com.vaultra.app.data.DocumentEntry
import com.vaultra.app.data.DocumentFolder
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.DocumentValidators
import com.vaultra.app.util.ImageStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorSheet(
    db: VaultDatabase,
    categories: List<DocumentCategory>,
    folders: List<DocumentFolder>,
    defaultFolderId: String?,
    existing: DocumentEntry?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drafts by db.documentDao().drafts().collectAsState(initial = emptyList())

    // Bug fix #4 (Auto Draft Save): offer to restore the most recent draft when opening
    // "add new", instead of silently discarding it or silently continuing an old one.
    var restorePromptDraft by remember { mutableStateOf<DocumentEntry?>(null) }
    var effectiveExisting by remember { mutableStateOf(existing) }
    var checkedForDraft by remember { mutableStateOf(existing != null) }
    LaunchedEffect(drafts, existing) {
        if (!checkedForDraft) {
            checkedForDraft = true
            val latestDraft = drafts.maxByOrNull { it.updatedAt }
            if (latestDraft != null) restorePromptDraft = latestDraft
        }
    }

    if (restorePromptDraft != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Restore Draft?") },
            text = { Text("You have an unsaved document (\"${restorePromptDraft?.title?.ifBlank { "Untitled" }}\"). Continue editing it, or discard it and start fresh?") },
            confirmButton = { TextButton(onClick = { effectiveExisting = restorePromptDraft; restorePromptDraft = null }) { Text("Continue Editing", color = Accent2, fontWeight = FontWeight.Bold) } },
            dismissButton = {
                TextButton(onClick = {
                    val d = restorePromptDraft
                    restorePromptDraft = null
                    if (d != null) scope.launch { db.documentDao().delete(d); if (d.attachmentPaths.isNotEmpty()) ImageStore.deleteImages(d.attachmentPaths) }
                }) { Text("Discard Draft") }
            },
            containerColor = BgElev
        )
        return
    }

    DocumentEditorForm(db = db, categories = categories, folders = folders, defaultFolderId = defaultFolderId, existing = effectiveExisting, onDismiss = onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentEditorForm(
    db: VaultDatabase,
    categories: List<DocumentCategory>,
    folders: List<DocumentFolder>,
    defaultFolderId: String?,
    existing: DocumentEntry?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var folderId by remember { mutableStateOf(existing?.folderId ?: defaultFolderId) }
    var docType by remember { mutableStateOf(existing?.docType ?: DocumentValidators.DOC_TYPES.first()) }
    var holderName by remember { mutableStateOf(existing?.holderName ?: "") }
    var docNumber by remember { mutableStateOf(existing?.docNumber ?: "") }
    var issueDate by remember { mutableStateOf(existing?.issueDate) }
    var expiryDate by remember { mutableStateOf(existing?.expiryDate) }
    var issuedBy by remember { mutableStateOf(existing?.issuedBy ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var tagsText by remember { mutableStateOf(existing?.tags ?: "") }
    var attachments by remember { mutableStateOf(existing?.attachmentPaths ?: emptyList()) }
    val originalAttachments = remember { existing?.attachmentPaths ?: emptyList() }

    var categoryMenuOpen by remember { mutableStateOf(false) }
    var folderMenuOpen by remember { mutableStateOf(false) }
    var docTypeMenuOpen by remember { mutableStateOf(false) }
    var showIssueDatePicker by remember { mutableStateOf(false) }
    var showExpiryDatePicker by remember { mutableStateOf(false) }

    fun cleanupAbandoned(keep: List<String>) {
        val abandoned = attachments - keep.toSet()
        if (abandoned.isNotEmpty()) ImageStore.deleteImages(abandoned)
    }

    fun dismissWithoutSaving() {
        if (existing == null && (title.isNotBlank() || holderName.isNotBlank() || docNumber.isNotBlank())) {
            scope.launch {
                val now = System.currentTimeMillis()
                db.documentDao().upsert(
                    DocumentEntry(
                        id = UUID.randomUUID().toString(), title = title.trim(), categoryId = categoryId, folderId = folderId,
                        docType = docType, holderName = holderName.trim(), docNumber = docNumber.trim(),
                        issueDate = issueDate, expiryDate = expiryDate, issuedBy = issuedBy.trim(),
                        description = description.trim(), notes = notes.trim(), tags = tagsText.trim(),
                        isFavorite = false, isDraft = true, updatedAt = now, createdAt = now, attachmentPaths = attachments
                    )
                )
                onDismiss()
            }
        } else {
            cleanupAbandoned(originalAttachments)
            onDismiss()
        }
    }

    fun save(asDraft: Boolean) {
        if (!asDraft && title.isBlank()) { Toast.makeText(context, "Document title is required", Toast.LENGTH_SHORT).show(); return }
        val now = System.currentTimeMillis()
        val entry = DocumentEntry(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Untitled draft" }, categoryId = categoryId, folderId = folderId,
            docType = docType, holderName = holderName.trim(), docNumber = docNumber.trim(),
            issueDate = issueDate, expiryDate = expiryDate, issuedBy = issuedBy.trim(),
            description = description.trim(), notes = notes.trim(), tags = tagsText.trim(),
            isFavorite = existing?.isFavorite ?: false, isDraft = asDraft,
            updatedAt = now, createdAt = existing?.createdAt ?: now, attachmentPaths = attachments
        )
        scope.launch {
            db.documentDao().upsert(entry)
            cleanupAbandoned(originalAttachments)
            onDismiss()
            Toast.makeText(context, if (asDraft) "Saved as draft" else if (existing == null) "Document added" else "Document updated", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(onDismissRequest = { dismissWithoutSaving() }, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Text(if (existing == null) "New Document" else "Edit Document", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(title, { title = it }, label = { Text("Document Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            Box {
                OutlinedTextField(
                    value = categories.find { it.id == categoryId }?.name ?: "Uncategorized", onValueChange = {}, readOnly = true, enabled = false,
                    label = { Text("Category") }, trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = TextPrimary, disabledBorderColor = Line, disabledLabelColor = TextDim, disabledTrailingIconColor = TextDim),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { categoryMenuOpen = true })
                DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Uncategorized") }, onClick = { categoryId = null; categoryMenuOpen = false })
                    categories.forEach { cat -> DropdownMenuItem(text = { Text(cat.name) }, leadingIcon = { DocCategoryBadge(cat, size = 22.dp) }, onClick = { categoryId = cat.id; categoryMenuOpen = false }) }
                }
            }
            Spacer(Modifier.height(12.dp))

            Box {
                OutlinedTextField(
                    value = folders.find { it.id == folderId }?.name ?: "Root (no folder)", onValueChange = {}, readOnly = true, enabled = false,
                    label = { Text("Folder") }, trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = TextPrimary, disabledBorderColor = Line, disabledLabelColor = TextDim, disabledTrailingIconColor = TextDim),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { folderMenuOpen = true })
                DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Root (no folder)") }, onClick = { folderId = null; folderMenuOpen = false })
                    folders.forEach { f -> DropdownMenuItem(text = { Text(f.name) }, onClick = { folderId = f.id; folderMenuOpen = false }) }
                }
            }
            Spacer(Modifier.height(12.dp))

            Box {
                OutlinedTextField(
                    value = docType, onValueChange = {}, readOnly = true, enabled = false,
                    label = { Text("Document Type") }, trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = TextPrimary, disabledBorderColor = Line, disabledLabelColor = TextDim, disabledTrailingIconColor = TextDim),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { docTypeMenuOpen = true })
                DropdownMenu(expanded = docTypeMenuOpen, onDismissRequest = { docTypeMenuOpen = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                    DocumentValidators.DOC_TYPES.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { docType = t; docTypeMenuOpen = false }) }
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(holderName, { holderName = it }, label = { Text("Holder Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(docNumber, { docNumber = it }, label = { Text("Document Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showIssueDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = Accent2)
                    Spacer(Modifier.width(6.dp))
                    Text(issueDate?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(it)) } ?: "Issue date")
                }
                OutlinedButton(onClick = { showExpiryDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.EventBusy, contentDescription = null, modifier = Modifier.size(16.dp), tint = Accent2)
                    Spacer(Modifier.width(6.dp))
                    Text(expiryDate?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(it)) } ?: "Expiry (optional)")
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(issuedBy, { issuedBy = it }, label = { Text("Issued By") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(tagsText, { tagsText = it }, label = { Text("Tags (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            DocumentAttachmentsEditor(
                paths = attachments,
                onAdded = { attachments = attachments + it },
                onRemoved = { attachments = attachments - it },
                onReplaced = { old, new -> attachments = attachments.map { if (it == old) new else it }; ImageStore.deleteImages(listOf(old)) }
            )

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { dismissWithoutSaving() }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                OutlinedButton(onClick = { save(asDraft = true) }, modifier = Modifier.weight(1f)) { Text("Save as Draft") }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { save(asDraft = false) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showIssueDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = issueDate?.let { localMidnightToUtc(it) } ?: localMidnightToUtc(System.currentTimeMillis()))
        DatePickerDialog(
            onDismissRequest = { showIssueDatePicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { issueDate = utcMidnightToLocal(it) }; showIssueDatePicker = false }) { Text("OK", color = Accent2) } },
            dismissButton = { TextButton(onClick = { showIssueDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
    if (showExpiryDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = expiryDate?.let { localMidnightToUtc(it) } ?: localMidnightToUtc(System.currentTimeMillis()))
        DatePickerDialog(
            onDismissRequest = { showExpiryDatePicker = false },
            confirmButton = {
                Row {
                    TextButton(onClick = { expiryDate = null; showExpiryDatePicker = false }) { Text("Clear") }
                    TextButton(onClick = { state.selectedDateMillis?.let { expiryDate = utcMidnightToLocal(it) }; showExpiryDatePicker = false }) { Text("OK", color = Accent2) }
                }
            },
            dismissButton = { TextButton(onClick = { showExpiryDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

private fun localMidnightToUtc(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    utc.set(Calendar.MILLISECOND, 0)
    return utc.timeInMillis
}

private fun utcMidnightToLocal(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val local = Calendar.getInstance()
    local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    local.set(Calendar.MILLISECOND, 0)
    return local.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailSheet(doc: DocumentEntry, category: DocumentCategory?, folder: DocumentFolder?, db: VaultDatabase, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DocCategoryBadge(category, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.title.ifBlank { doc.docType }, fontWeight = FontWeight.Black, fontSize = 17.sp, color = TextPrimary)
                    Text(doc.docType, color = TextDim, fontSize = 12.sp)
                }
                IconButton(onClick = { scope.launch { db.documentDao().upsert(doc.copy(isFavorite = !doc.isFavorite, updatedAt = System.currentTimeMillis())) } }) {
                    Icon(if (doc.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = "Favorite", tint = if (doc.isFavorite) Warn else TextDim)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextDim) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Accent2) }
            }

            Spacer(Modifier.height(14.dp))
            SectionCard("DETAILS") {
                DetailLine("Holder Name", doc.holderName.ifBlank { "—" })
                DetailLine("Document Number", DocumentValidators.mask(doc.docNumber).ifBlank { "—" })
                if (folder != null) DetailLine("Folder", folder.name)
                if (doc.issueDate != null) DetailLine("Issue Date", SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(doc.issueDate)))
                if (doc.expiryDate != null) DetailLine("Expiry Date", SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(doc.expiryDate)))
                if (doc.issuedBy.isNotBlank()) DetailLine("Issued By", doc.issuedBy)
            }

            if (doc.description.isNotBlank()) { Spacer(Modifier.height(10.dp)); SectionCard("DESCRIPTION") { Text(doc.description, color = TextPrimary, fontSize = 14.sp) } }
            if (doc.tags.isNotBlank()) { Spacer(Modifier.height(10.dp)); SectionCard("TAGS") { Text(doc.tags.split(",").joinToString("  •  ") { it.trim() }, color = TextPrimary, fontSize = 13.sp) } }
            if (doc.notes.isNotBlank()) { Spacer(Modifier.height(10.dp)); SectionCard("NOTES") { Text(doc.notes, color = TextPrimary, fontSize = 14.sp) } }
            if (doc.attachmentPaths.isNotEmpty()) { Spacer(Modifier.height(10.dp)); DocumentAttachmentsGallery(doc.attachmentPaths) }

            Spacer(Modifier.height(10.dp))
            SectionCard("FILE INFORMATION") {
                doc.attachmentPaths.forEach { path ->
                    val f = java.io.File(path)
                    if (f.exists()) DetailLine(f.name, "${f.extension.uppercase()} · ${(f.length() / 1024).coerceAtLeast(1)} KB")
                }
                if (doc.attachmentPaths.isEmpty()) Text("No attachments", color = TextDim, fontSize = 12.sp)
            }

            Spacer(Modifier.height(10.dp))
            SectionCard("DATES") {
                Text("Created " + SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(java.util.Date(doc.createdAt)), color = TextDim, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text("Last updated " + SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(java.util.Date(doc.updatedAt)), color = TextDim, fontSize = 12.sp)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this document?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        db.documentDao().delete(doc)
                        if (doc.attachmentPaths.isNotEmpty()) ImageStore.deleteImages(doc.attachmentPaths)
                        confirmDelete = false
                        onDismiss()
                    }
                }) { Text("Delete", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            containerColor = BgElev
        )
    }
}

@Composable
private fun SectionCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
        Text(label, fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = TextDim, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}
