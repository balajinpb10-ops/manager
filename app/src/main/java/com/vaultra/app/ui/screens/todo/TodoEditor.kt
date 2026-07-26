package com.vaultra.app.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.ChecklistItem
import com.vaultra.app.data.TodoCategory
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.data.TodoPriority
import com.vaultra.app.data.TodoStatus
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.ImageStore
import java.util.Calendar
import java.util.UUID

/**
 * Create/edit screen for a single task. Handles both a brand-new task (initial == null)
 * and editing an existing one, including one that's currently a draft.
 *
 * onSave persists with isDraft = false (a fully committed task); onSaveDraft persists with
 * isDraft = true so it shows up under "Drafts" and can be resumed later — including after the
 * app is fully closed and reopened, since drafts live in the same encrypted database as
 * everything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorScreen(
    initial: TodoEntry?,
    categories: List<TodoCategory>,
    onCancel: () -> Unit,
    onSave: (TodoEntry) -> Unit,
    onSaveDraft: (TodoEntry) -> Unit,
    onDelete: ((TodoEntry) -> Unit)? = null
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(initial?.categoryId) }
    var priority by rememberSaveable { mutableStateOf(initial?.priority ?: TodoPriority.MEDIUM) }
    var status by rememberSaveable { mutableStateOf(initial?.status ?: TodoStatus.PENDING) }
    var dueAt by rememberSaveable { mutableStateOf(initial?.dueAt) }
    var tags by rememberSaveable { mutableStateOf(initial?.tags ?: "") }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var progress by rememberSaveable { mutableStateOf(initial?.progress ?: 0) }
    var checklist by remember { mutableStateOf(initial?.checklist ?: emptyList()) }
    var attachments by remember { mutableStateOf(initial?.attachmentPaths ?: emptyList()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var newChecklistText by remember { mutableStateOf("") }

    val hasContent = title.isNotBlank() || description.isNotBlank() || notes.isNotBlank() || checklist.isNotEmpty() || attachments.isNotEmpty()

    fun buildEntry(draft: Boolean): TodoEntry {
        val now = System.currentTimeMillis()
        return TodoEntry(
            id = initial?.id ?: UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Untitled task" },
            description = description.trim(),
            categoryId = categoryId,
            tags = tags.trim(),
            notes = notes.trim(),
            priority = priority,
            dueAt = dueAt,
            status = if (draft) TodoStatus.PENDING else status,
            isCompleted = !draft && status == TodoStatus.COMPLETED,
            isPinned = initial?.isPinned ?: false,
            isFavorite = initial?.isFavorite ?: false,
            isArchived = false,
            isDraft = draft,
            progress = if (!draft && status == TodoStatus.COMPLETED) 100 else progress,
            checklist = checklist,
            attachmentPaths = attachments,
            createdAt = initial?.createdAt ?: now,
            updatedAt = now
        )
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initial == null) "NEW TASK" else if (initial.isDraft) "EDIT DRAFT" else "EDIT TASK",
                        fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (hasContent) showDiscardConfirm = true else onCancel() }) {
                        Icon(Icons.Default.Close, "Cancel", tint = Accent2)
                    }
                },
                actions = {
                    if (initial != null && onDelete != null) {
                        IconButton(onClick = { onDelete(initial) }) { Icon(Icons.Default.Delete, "Delete", tint = Accent2) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Task Title") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent2, cursorColor = Accent2)
                )
            }
            item {
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description") }, minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent2, cursorColor = Accent2)
                )
            }
            item { SectionLabel("Category") }
            item { CategoryPickerRow(categories, categoryId) { categoryId = it } }

            item { SectionLabel("Priority") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AllPriorities.forEach { p ->
                        PriorityChip(priority = p, selected = priority == p) { priority = p }
                    }
                }
            }

            item { SectionLabel("Status") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AllStatuses.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = statusColor(s).copy(alpha = 0.25f),
                                selectedLabelColor = statusColor(s),
                                containerColor = BgCard, labelColor = TextDim
                            )
                        )
                    }
                }
            }

            item { SectionLabel("Due Date & Time") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dueAt?.let { formatDueDate(it) } ?: "Set date", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f), enabled = dueAt != null) {
                        Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dueAt?.let { formatDueTime(it) } ?: "Set time", fontSize = 12.sp)
                    }
                    if (dueAt != null) {
                        IconButton(onClick = { dueAt = null }) { Icon(Icons.Default.Clear, "Clear due date", tint = TextDim) }
                    }
                }
            }

            item { SectionLabel("Tags") }
            item {
                OutlinedTextField(
                    value = tags, onValueChange = { tags = it },
                    label = { Text("Comma-separated tags") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent2, cursorColor = Accent2)
                )
            }

            item { SectionLabel("Notes") }
            item {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Additional notes") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent2, cursorColor = Accent2)
                )
            }

            item { SectionLabel("Checklist / Subtasks") }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(12.dp)) {
                    checklist.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.isDone,
                                onCheckedChange = { checked ->
                                    checklist = checklist.map { if (it.id == item.id) it.copy(isDone = checked) else it }
                                    progress = checklistProgress(checklist)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Color.White)
                            )
                            Text(
                                item.text, color = if (item.isDone) TextDim else TextPrimary, modifier = Modifier.weight(1f),
                                fontSize = 13.sp
                            )
                            IconButton(onClick = {
                                checklist = checklist.filterNot { it.id == item.id }
                                progress = checklistProgress(checklist)
                            }) { Icon(Icons.Default.Close, "Remove subtask", tint = TextDim, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newChecklistText, onValueChange = { newChecklistText = it },
                            placeholder = { Text("Add subtask", fontSize = 12.sp) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent2, cursorColor = Accent2)
                        )
                        IconButton(onClick = {
                            if (newChecklistText.isNotBlank()) {
                                checklist = checklist + ChecklistItem(UUID.randomUUID().toString(), newChecklistText.trim(), false)
                                newChecklistText = ""
                                progress = checklistProgress(checklist)
                            }
                        }) { Icon(Icons.Default.Add, "Add subtask", tint = Accent2) }
                    }
                }
            }

            if (checklist.isEmpty()) {
                item { SectionLabel("Progress") }
                item {
                    Column {
                        Slider(
                            value = progress.toFloat(), onValueChange = { progress = it.toInt() },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                        )
                        Text("$progress%", color = TextDim, fontSize = 12.sp)
                    }
                }
            }

            item {
                TodoAttachmentsPicker(
                    paths = attachments,
                    onAdded = { attachments = attachments + it },
                    onRemoved = { path -> ImageStore.deleteImages(listOf(path)); attachments = attachments - path },
                    onReplace = { old, added ->
                        ImageStore.deleteImages(listOf(old))
                        attachments = (attachments - old) + added
                    }
                )
            }

            item { Spacer(Modifier.height(4.dp)) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { if (hasContent) showDiscardConfirm = true else onCancel() }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    OutlinedButton(onClick = { onSaveDraft(buildEntry(draft = true)) }, modifier = Modifier.weight(1f)) {
                        Text("Save as Draft")
                    }
                }
            }
            item {
                Button(
                    onClick = { if (title.isNotBlank()) onSave(buildEntry(draft = false)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Save Task", fontWeight = FontWeight.Bold) }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueAt ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val cal = Calendar.getInstance().apply { timeInMillis = picked }
                        val existing = dueAt
                        if (existing != null) {
                            val old = Calendar.getInstance().apply { timeInMillis = existing }
                            cal.set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY))
                            cal.set(Calendar.MINUTE, old.get(Calendar.MINUTE))
                        } else {
                            cal.set(Calendar.HOUR_OF_DAY, 9); cal.set(Calendar.MINUTE, 0)
                        }
                        dueAt = cal.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextDim) } }
        ) { DatePicker(state = state) }
    }

    if (showTimePicker && dueAt != null) {
        val cal = Calendar.getInstance().apply { timeInMillis = dueAt!! }
        val state = rememberTimePickerState(initialHour = cal.get(Calendar.HOUR_OF_DAY), initialMinute = cal.get(Calendar.MINUTE))
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = BgElev,
            title = { Text("Due time", color = TextPrimary) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply { timeInMillis = dueAt!! }
                    newCal.set(Calendar.HOUR_OF_DAY, state.hour); newCal.set(Calendar.MINUTE, state.minute)
                    dueAt = newCal.timeInMillis
                    showTimePicker = false
                }) { Text("OK", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = TextDim) } }
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            containerColor = BgElev,
            title = { Text("Save as draft?", color = TextPrimary) },
            text = { Text("You have unsaved changes. Save this task as a draft so you can finish it later, or discard it.", color = TextDim) },
            confirmButton = {
                TextButton(onClick = { onSaveDraft(buildEntry(draft = true)); showDiscardConfirm = false }) { Text("Save as Draft", color = Accent2) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDiscardConfirm = false; onCancel() }) { Text("Discard", color = Accent) }
                    TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep Editing", color = TextDim) }
                }
            }
        )
    }
}

private fun checklistProgress(items: List<ChecklistItem>): Int =
    if (items.isEmpty()) 0 else ((items.count { it.isDone }.toFloat() / items.size) * 100).toInt()

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDim)
}

@Composable
private fun CategoryPickerRow(categories: List<TodoCategory>, selectedId: String?, onSelect: (String?) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                CategoryChip(name = "None", color = TextDim, icon = Icons.Default.Block, selected = selectedId == null) { onSelect(null) }
            }
            items(categories, key = { it.id }) { cat ->
                CategoryChip(
                    name = cat.name, color = colorFromHex(cat.colorHex), icon = iconForKey(cat.icon),
                    selected = selectedId == cat.id
                ) { onSelect(cat.id) }
            }
        }
    }
}

@Composable
private fun CategoryChip(name: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color.copy(alpha = 0.25f) else BgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) color else TextDim, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(name, fontSize = 12.sp, color = if (selected) color else TextDim, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PriorityChip(priority: Int, selected: Boolean, onClick: () -> Unit) {
    val color = priorityColor(priority)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else BgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(priorityLabel(priority), fontSize = 12.sp, color = if (selected) color else TextDim, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
