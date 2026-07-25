package com.vaultra.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.DiaryEntry
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.data.TodoStatus
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.*
import java.util.UUID

private enum class ProductivitySection { HOME, TASKS, DIARY }

@Composable
fun ProductivityScreen(db: VaultDatabase) {
    var section by remember { mutableStateOf(ProductivitySection.HOME) }
    val tasks by db.todoDao().active().collectAsState(emptyList())
    val diary by db.diaryDao().active().collectAsState(emptyList())
    var addTask by remember { mutableStateOf(false) }
    var addDiary by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().padding(20.dp)) {
        when (section) {
            ProductivitySection.HOME -> Column {
                Text("PRODUCTIVITY", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                ModuleCard("To-Do Manager", "${tasks.count { !it.isCompleted }} open tasks · projects, priorities and progress", Icons.Default.CheckCircle) { section = ProductivitySection.TASKS }
                Spacer(Modifier.height(12.dp))
                ModuleCard("Private Diary", "${diary.size} encrypted journal entries", Icons.Default.MenuBook) { section = ProductivitySection.DIARY }
                Spacer(Modifier.height(18.dp))
                Text("Everything in these modules is stored locally in your encrypted vault.", color = TextDim, fontSize = 12.sp)
            }
            ProductivitySection.TASKS -> TaskList(tasks, db, { section = ProductivitySection.HOME }) { addTask = true; }
            ProductivitySection.DIARY -> DiaryList(diary, { section = ProductivitySection.HOME }) { addDiary = true }
        }
        if (addTask) TaskEditor(onDismiss = { addTask = false }) { title, project, priority ->
            scope.launch {
                val now = System.currentTimeMillis()
                db.todoDao().upsert(
                    TodoEntry(
                        id = UUID.randomUUID().toString(), title = title, description = "", categoryId = null,
                        tags = project, notes = "", priority = priority, dueAt = null, status = TodoStatus.PENDING,
                        isCompleted = false, isPinned = false, isFavorite = false, isArchived = false, isDraft = false,
                        progress = 0, checklist = emptyList(), attachmentPaths = emptyList(), createdAt = now, updatedAt = now
                    )
                )
                addTask = false
            }
        }
        if (addDiary) DiaryEditor(onDismiss = { addDiary = false }) { title, body, mood ->
            scope.launch { db.diaryDao().upsert(DiaryEntry(UUID.randomUUID().toString(), title, body, mood, "", "", false, false, System.currentTimeMillis(), System.currentTimeMillis())); addDiary = false }
        }
    }
}

@Composable private fun ModuleCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) =
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(BgCard).clickable(onClick = click).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent2); Spacer(Modifier.width(16.dp)); Column { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold); Text(subtitle, color = TextDim, fontSize = 12.sp) }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TaskList(tasks: List<TodoEntry>, db: VaultDatabase, back: () -> Unit, add: () -> Unit) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TO-DO", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back", tint = Accent2) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = add, containerColor = Accent) { Icon(Icons.Default.Add, "Add task") }
        },
        containerColor = Bg
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { TasksDashboard(tasks) }
            item { Spacer(Modifier.height(8.dp)) }

            if (tasks.isEmpty()) {
                item {
                    Text(
                        "Plan your next task with the + button.",
                        color = TextDim,
                        modifier = Modifier.padding(top = 28.dp)
                    )
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(task = task, onToggleComplete = {
                        scope.launch { db.todoDao().upsert(task.copy(isCompleted = !task.isCompleted)) }
                    })
                }
            }
        }
    }
}

@Composable
private fun TasksDashboard(tasks: List<TodoEntry>) {
    val completed = tasks.count { it.isCompleted }
    val pending = tasks.size - completed
    val overdue = tasks.count { it.dueAt != null && it.dueAt!! < System.currentTimeMillis() && !it.isCompleted }
    val completionRate = if (tasks.isNotEmpty()) completed.toFloat() / tasks.size else 0f

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Task Summary", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatItem("Total", tasks.size.toString())
                    StatItem("Completed", completed.toString())
                    StatItem("Pending", pending.toString())
                    StatItem("Overdue", overdue.toString(), if (overdue > 0) Accent2 else TextDim)
                }
            }
            Spacer(Modifier.width(16.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { completionRate },
                    modifier = Modifier.size(50.dp),
                    color = Accent,
                    trackColor = Line,
                    strokeWidth = 5.dp
                )
                Text("${(completionRate * 100).toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color = TextDim) {
    Column {
        Text(label, fontSize = 12.sp, color = TextDim)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (label == "Overdue") color else TextPrimary)
    }
}

@Composable
private fun TaskCard(task: TodoEntry, onToggleComplete: () -> Unit) {
    Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = BgCard)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleComplete() }, colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Color.White))
            Column(Modifier.weight(1f)) {
                Text(task.title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                task.tags.takeIf { it.isNotBlank() }?.let { Text(it, color = TextDim, fontSize = 11.sp) }
            }
            // Placeholder for priority badge, pin icon etc.
        }
    }
}

@Composable private fun DiaryList(entries: List<DiaryEntry>, back: () -> Unit, add: () -> Unit) {
    Column { TextButton(onClick = back) { Text("‹ Back", color = Accent2) }; Text("PRIVATE DIARY", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary, modifier = Modifier.padding(start = 4.dp))
        if (entries.isEmpty()) Text("Your journal is private and encrypted. Add your first entry.", color = TextDim, modifier = Modifier.padding(top = 28.dp))
        else LazyColumn(Modifier.weight(1f).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(entries, key = { it.id }) { e -> Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(BgCard).padding(14.dp)) { Text(e.title, color = TextPrimary, fontWeight = FontWeight.Bold); Text(e.mood, color = TextDim, fontSize = 11.sp) } } }
        FloatingActionButton(onClick = add, containerColor = Accent, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Add, "Add diary entry") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TaskEditor(onDismiss: () -> Unit, save: (String, String, Int) -> Unit) { var title by remember { mutableStateOf("") }; var project by remember { mutableStateOf("") }; var priority by remember { mutableStateOf(0) }; ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BgElev) { Column(Modifier.fillMaxWidth().padding(20.dp)) { Text("New Task", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary); Spacer(Modifier.height(16.dp)); OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Title") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)); OutlinedTextField(value = project, onValueChange = { project = it }, label = { Text("Project (optional)") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }; Button(onClick = { if (title.isNotBlank()) save(title, project, priority); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Save") } } } } }
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DiaryEditor(onDismiss: () -> Unit, save: (String, String, String) -> Unit) { var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var mood by remember { mutableStateOf("Happy") }; ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BgElev) { Column(Modifier.fillMaxWidth().padding(20.dp)) { Text("New Diary Entry", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary); Spacer(Modifier.height(16.dp)); OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)); OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Entry") }, modifier = Modifier.fillMaxWidth(), minLines = 4); Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }; Button(onClick = { if (title.isNotBlank()) save(title, body, mood); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Save") } } } } }
