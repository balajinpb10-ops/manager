package com.vaultra.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.ChecklistItem
import com.vaultra.app.data.TodoCategory
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.data.TodoStatus
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.ImageStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(db: VaultDatabase, categories: List<TodoCategory>, existing: TodoEntry?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val drafts by db.todoDao().drafts().collectAsState(initial = emptyList())

    // Bug fix #4 (Auto Draft Save): offer to restore the most recent draft when opening
    // "add new", instead of silently discarding it or silently continuing an old one.
    var restorePromptDraft by remember { mutableStateOf<TodoEntry?>(null) }
    var effectiveExisting by remember { mutableStateOf(existing) }
    var checkedForDraft by remember { mutableStateOf(existing != null) }
    LaunchedEffect(drafts, existing) {
        if (!checkedForDraft) {
            checkedForDraft = true
            drafts.maxByOrNull { it.updatedAt }?.let { restorePromptDraft = it }
        }
    }

    if (restorePromptDraft != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Restore Draft?") },
            text = { Text("You have an unsaved task (\"${restorePromptDraft?.title?.ifBlank { "Untitled" }}\"). Continue editing it, or discard it and start fresh?") },
            confirmButton = { TextButton(onClick = { effectiveExisting = restorePromptDraft; restorePromptDraft = null }) { Text("Continue Editing", color = Accent2, fontWeight = FontWeight.Bold) } },
            dismissButton = {
                TextButton(onClick = {
                    val d = restorePromptDraft
                    restorePromptDraft = null
                    if (d != null) scope.launch { db.todoDao().delete(d); if (d.attachmentPaths.isNotEmpty()) ImageStore.deleteImages(d.attachmentPaths) }
                }) { Text("Discard Draft") }
            },
            containerColor = BgElev
        )
        return
    }

    TaskEditorForm(db = db, categories = categories, existing = effectiveExisting, onDismiss = onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorForm(db: VaultDatabase, categories: List<TodoCategory>, existing: TodoEntry?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: 1) }
    var status by remember { mutableStateOf(existing?.status ?: TodoStatus.PENDING) }
    var dueAt by remember { mutableStateOf(existing?.dueAt) }
    var tagsText by remember { mutableStateOf(existing?.tags ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var checklist by remember { mutableStateOf(existing?.checklist ?: emptyList()) }
    var attachments by remember { mutableStateOf(existing?.attachmentPaths ?: emptyList()) }
    val originalAttachments = remember { existing?.attachmentPaths ?: emptyList() }

    var categoryMenuOpen by remember { mutableStateOf(false) }
    var statusMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    fun cleanupAbandoned(keep: List<String>) {
        val abandoned = attachments - keep.toSet()
        if (abandoned.isNotEmpty()) ImageStore.deleteImages(abandoned)
    }

    fun dismissWithoutSaving() {
        if (existing == null && (title.isNotBlank() || description.isNotBlank())) {
            // Auto-save an abandoned new task as a draft so nothing is lost.
            scope.launch {
                val now = System.currentTimeMillis()
                db.todoDao().upsert(
                    TodoEntry(
                        id = UUID.randomUUID().toString(), title = title.trim(), description = description.trim(),
                        categoryId = categoryId, tags = tagsText.trim(), notes = notes.trim(), priority = priority,
                        dueAt = dueAt, status = status, isCompleted = false, isPinned = false, isFavorite = false,
                        isArchived = false, isDraft = true, progress = 0, checklist = checklist.filter { it.text.isNotBlank() },
                        attachmentPaths = attachments, createdAt = now, updatedAt = now
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
        if (!asDraft && title.isBlank()) {
            Toast.makeText(context, "Title is required", Toast.LENGTH_SHORT).show()
            return
        }
        val now = System.currentTimeMillis()
        val finalChecklist = checklist.filter { it.text.isNotBlank() }
        val entry = TodoEntry(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Untitled draft" },
            description = description.trim(),
            categoryId = categoryId,
            tags = tagsText.trim(),
            notes = notes.trim(),
            priority = priority,
            dueAt = dueAt,
            status = if (asDraft) (existing?.status ?: TodoStatus.PENDING) else status,
            isCompleted = if (asDraft) (existing?.isCompleted ?: false) else status == TodoStatus.COMPLETED,
            isPinned = existing?.isPinned ?: false,
            isFavorite = existing?.isFavorite ?: false,
            isArchived = existing?.isArchived ?: false,
            isDraft = asDraft,
            progress = if (!asDraft && status == TodoStatus.COMPLETED) 100 else (existing?.progress ?: 0),
            checklist = finalChecklist,
            attachmentPaths = attachments,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        scope.launch {
            db.todoDao().upsert(entry)
            cleanupAbandoned(originalAttachments)
            onDismiss()
            Toast.makeText(context, if (asDraft) "Saved as draft" else if (existing == null) "Task added" else "Task updated", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(onDismissRequest = { dismissWithoutSaving() }, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Text(if (existing == null) "New Task" else "Edit Task", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(title, { title = it }, label = { Text("Task Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            // Category dropdown
            Box {
                OutlinedTextField(
                    value = categories.find { it.id == categoryId }?.name ?: "Uncategorized",
                    onValueChange = {}, readOnly = true, enabled = false, label = { Text("Category") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = TextPrimary, disabledBorderColor = Line, disabledLabelColor = TextDim, disabledTrailingIconColor = TextDim),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { categoryMenuOpen = true })
                DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Uncategorized") }, onClick = { categoryId = null; categoryMenuOpen = false })
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            leadingIcon = { CategoryBadge(cat, size = 22.dp) },
                            onClick = { categoryId = cat.id; categoryMenuOpen = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Text("PRIORITY", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Low", 1 to "Medium", 2 to "High").forEach { (p, label) ->
                    val selected = priority == p
                    val color = priorityColor(p)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) color.copy(alpha = 0.22f) else BgCard)
                            .clickable { priority = p }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selected) color else TextDim, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                    }
                }
            }

            if (existing != null) {
                Spacer(Modifier.height(14.dp))
                Box {
                    OutlinedTextField(
                        value = status, onValueChange = {}, readOnly = true, enabled = false, label = { Text("Status") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextDim) },
                        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = TextPrimary, disabledBorderColor = Line, disabledLabelColor = TextDim, disabledTrailingIconColor = TextDim),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { statusMenuOpen = true })
                    DropdownMenu(expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                        listOf(TodoStatus.PENDING, TodoStatus.IN_PROGRESS, TodoStatus.COMPLETED, TodoStatus.CANCELLED).forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { status = s; statusMenuOpen = false })
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("DUE DATE & TIME", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = Accent2)
                    Spacer(Modifier.width(6.dp))
                    Text(dueAt?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(it)) } ?: "Set date")
                }
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = Accent2)
                    Spacer(Modifier.width(6.dp))
                    Text(dueAt?.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date(it)) } ?: "Set time")
                }
                if (dueAt != null) {
                    IconButton(onClick = { dueAt = null }) { Icon(Icons.Filled.Close, contentDescription = "Clear due date", tint = TextDim) }
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                tagsText, { tagsText = it }, label = { Text("Tags (comma separated)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            ChecklistEditor(checklist = checklist, onChange = { checklist = it })

            Spacer(Modifier.height(16.dp))
            TodoAttachmentsPicker(paths = attachments, onAdded = { attachments = attachments + it }, onRemoved = { attachments = attachments - it })

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

    if (showDatePicker) {
        val initialUtcMillis = dueAt?.let { localMidnightToUtcMillis(it) } ?: localMidnightToUtcMillis(System.currentTimeMillis())
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dueAt = applyDatePart(dueAt, it) }
                    showDatePicker = false
                }) { Text("OK", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { dueAt?.let { timeInMillis = it } }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Due time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    dueAt = applyTimePart(dueAt, timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            containerColor = BgElev
        )
    }
}

@Composable
private fun ChecklistEditor(checklist: List<ChecklistItem>, onChange: (List<ChecklistItem>) -> Unit) {
    Text("CHECKLIST (SUBTASKS)", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        checklist.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.isDone,
                    onCheckedChange = { checked -> onChange(checklist.toMutableList().also { it[index] = item.copy(isDone = checked) }) },
                    colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Color.White)
                )
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { text -> onChange(checklist.toMutableList().also { it[index] = item.copy(text = text) }) },
                    placeholder = { Text("Subtask") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(textDecoration = if (item.isDone) TextDecoration.LineThrough else null),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onChange(checklist.toMutableList().also { it.removeAt(index) }) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove subtask", tint = TextDim, modifier = Modifier.size(18.dp))
                }
            }
        }
        TextButton(onClick = { onChange(checklist + ChecklistItem(id = UUID.randomUUID().toString(), text = "", isDone = false)) }) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Accent2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add subtask", color = Accent2)
        }
    }
}

private fun localMidnightToUtcMillis(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    utc.set(Calendar.MILLISECOND, 0)
    return utc.timeInMillis
}

private fun applyDatePart(existingDueAt: Long?, utcMidnightMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnightMillis }
    val local = Calendar.getInstance()
    if (existingDueAt != null) {
        local.timeInMillis = existingDueAt
    } else {
        local.set(Calendar.HOUR_OF_DAY, 9)
        local.set(Calendar.MINUTE, 0)
    }
    local.set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
    local.set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
    local.set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
    local.set(Calendar.SECOND, 0)
    local.set(Calendar.MILLISECOND, 0)
    return local.timeInMillis
}

private fun applyTimePart(existingDueAt: Long?, hour: Int, minute: Int): Long {
    val local = Calendar.getInstance()
    if (existingDueAt != null) local.timeInMillis = existingDueAt
    local.set(Calendar.HOUR_OF_DAY, hour)
    local.set(Calendar.MINUTE, minute)
    local.set(Calendar.SECOND, 0)
    local.set(Calendar.MILLISECOND, 0)
    return local.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(task: TodoEntry, category: TodoCategory?, db: VaultDatabase, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var checklist by remember(task.id) { mutableStateOf(task.checklist) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Black, fontSize = 17.sp, color = TextPrimary, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PriorityBadge(task.priority)
                        StatusChip(task.status)
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextDim) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Accent2) }
            }

            if (task.dueAt != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Accent2, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Due " + formatDueDateTime(task.dueAt), color = TextDim, fontSize = 12.5.sp)
                }
            }

            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                SectionCard("DESCRIPTION") { Text(task.description, color = TextPrimary, fontSize = 14.sp) }
            }

            if (checklist.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SectionCard("CHECKLIST") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        checklist.forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.isDone,
                                    onCheckedChange = { checked ->
                                        val updated = checklist.toMutableList().also { it[index] = item.copy(isDone = checked) }
                                        checklist = updated
                                        scope.launch { db.todoDao().upsert(task.copy(checklist = updated, updatedAt = System.currentTimeMillis())) }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Color.White)
                                )
                                Text(item.text, color = TextPrimary, fontSize = 13.5.sp, textDecoration = if (item.isDone) TextDecoration.LineThrough else null)
                            }
                        }
                    }
                }
            }

            if (task.tags.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                SectionCard("TAGS") { Text(task.tags.split(",").joinToString("  •  ") { it.trim() }.trimEnd(), color = TextPrimary, fontSize = 13.sp) }
            }

            if (task.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                SectionCard("NOTES") { Text(task.notes, color = TextPrimary, fontSize = 14.sp) }
            }

            if (task.attachmentPaths.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                TodoAttachmentsGallery(task.attachmentPaths)
            }

            Spacer(Modifier.height(10.dp))
            SectionCard("DATES") {
                Text("Created " + SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(java.util.Date(task.createdAt)), color = TextDim, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text("Last updated " + SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(java.util.Date(task.updatedAt)), color = TextDim, fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    scope.launch {
                        val nowCompleted = !task.isCompleted
                        db.todoDao().upsert(
                            task.copy(
                                isCompleted = nowCompleted,
                                status = if (nowCompleted) TodoStatus.COMPLETED else TodoStatus.PENDING,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (task.isCompleted) BgCard else Accent)
            ) {
                Icon(if (task.isCompleted) Icons.Filled.Undo else Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (task.isCompleted) "Mark as Pending" else "Mark Complete", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this task?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        db.todoDao().delete(task)
                        if (task.attachmentPaths.isNotEmpty()) ImageStore.deleteImages(task.attachmentPaths)
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
