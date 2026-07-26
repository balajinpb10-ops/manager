package com.vaultra.app.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.TodoCategory
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.data.TodoStatus
import com.vaultra.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailScreen(
    task: TodoEntry,
    category: TodoCategory?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleComplete: () -> Unit,
    onToggleChecklistItem: (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("TASK DETAILS", fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Accent2) } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = TextDim) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = Accent2) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onToggleComplete,
                containerColor = if (task.isCompleted) BgCard else Accent,
                contentColor = if (task.isCompleted) TextPrimary else Color.White
            ) {
                Icon(if (task.isCompleted) Icons.Default.Replay else Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(if (task.isCompleted) "Mark Pending" else "Mark Complete", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        task.title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(task.status)
                        PriorityPill(task.priority)
                        if (task.isPinned) IconPill(Icons.Default.PushPin, Accent2)
                        if (task.isFavorite) IconPill(Icons.Default.Star, Warn)
                    }
                }
            }

            category?.let { cat ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(colorFromHex(cat.colorHex).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Icon(iconForKey(cat.icon), null, tint = colorFromHex(cat.colorHex), modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(cat.name, color = TextDim, fontSize = 13.sp)
                    }
                }
            }

            if (task.dueAt != null) {
                item {
                    InfoRow(icon = Icons.Default.Event, label = "Due", value = formatDueDateTime(task.dueAt))
                }
            }

            if (task.description.isNotBlank()) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
                        Text("DESCRIPTION", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(task.description, color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }

            if (task.checklist.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
                        val done = task.checklist.count { it.isDone }
                        Text("CHECKLIST \u00B7 $done/${task.checklist.size}", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        task.checklist.forEach { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.isDone,
                                    onCheckedChange = { onToggleChecklistItem(item.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Color.White)
                                )
                                Text(
                                    item.text, fontSize = 13.sp,
                                    color = if (item.isDone) TextDim else TextPrimary,
                                    textDecoration = if (item.isDone) TextDecoration.LineThrough else null
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
                        Text("PROGRESS", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { task.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Accent, trackColor = Line
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("${task.progress}%", color = TextDim, fontSize = 12.sp)
                    }
                }
            }

            if (task.tags.isNotBlank()) {
                item {
                    val tagList = task.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tagList) { tag ->
                            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(BgCard).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Text("#$tag", fontSize = 11.sp, color = Accent2)
                            }
                        }
                    }
                }
            }

            if (task.notes.isNotBlank()) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
                        Text("NOTES", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(task.notes, color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }

            if (task.attachmentPaths.isNotEmpty()) {
                item { TodoAttachmentsGallery(task.attachmentPaths) }
            }

            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
                    Text("Created " + formatFullTimestamp(task.createdAt), color = TextDim, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Last updated " + formatFullTimestamp(task.updatedAt), color = TextDim, fontSize = 11.sp)
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = BgElev,
            title = { Text("Delete task?", color = TextPrimary) },
            text = { Text("\"${task.title}\" and its attachments will be permanently deleted.", color = TextDim) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Accent) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextDim) } }
        )
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TextDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label: ", color = TextDim, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = statusColor(status)
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(status, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PriorityPill(priority: Int) {
    val color = priorityColor(priority)
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(priorityLabel(priority), fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconPill(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
    }
}
