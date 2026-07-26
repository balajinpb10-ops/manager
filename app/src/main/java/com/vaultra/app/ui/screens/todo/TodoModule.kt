@file:OptIn(ExperimentalMaterial3Api::class)

package com.vaultra.app.ui.screens.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.ImageStore
import kotlinx.coroutines.launch

private enum class TodoView { LIST, CALENDAR, STATS }
private enum class TodoRoute { HOME, EDITOR, DETAIL, CATEGORIES, DRAFTS }

/**
 * The entire To-Do module: dashboard summary, categories, searchable/filterable/sortable
 * task list, calendar view, statistics, create/edit, task detail, and category management —
 * all wired to the existing encrypted [VaultDatabase]. This is the single entry point mounted
 * from ProductivityScreen's TASKS section.
 */
@Composable
fun TodoModule(db: VaultDatabase, onExit: () -> Unit) {
    val allTasks by db.todoDao().active().collectAsState(emptyList())
    val drafts by db.todoDao().drafts().collectAsState(emptyList())
    val categories by db.todoCategoryDao().getAll().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var route by remember { mutableStateOf(TodoRoute.HOME) }
    var view by remember { mutableStateOf(TodoView.LIST) }
    var editingTask by remember { mutableStateOf<TodoEntry?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(TodoFilters()) }
    var sort by remember { mutableStateOf(TodoSort.DUE_DATE) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    fun categoryFor(id: String?): TodoCategory? = categories.find { it.id == id }

    when (route) {
        TodoRoute.EDITOR -> {
            TodoEditorScreen(
                initial = editingTask,
                categories = categories,
                onCancel = { route = TodoRoute.HOME; editingTask = null; isCreatingNew = false },
                onSave = { entry ->
                    scope.launch {
                        db.todoDao().upsert(entry)
                        route = TodoRoute.HOME; editingTask = null; isCreatingNew = false
                    }
                },
                onSaveDraft = { entry ->
                    scope.launch {
                        db.todoDao().upsert(entry)
                        route = TodoRoute.HOME; editingTask = null; isCreatingNew = false
                    }
                },
                onDelete = if (editingTask != null) { task ->
                    scope.launch {
                        ImageStore.deleteImages(task.attachmentPaths)
                        db.todoDao().delete(task)
                        route = TodoRoute.HOME; editingTask = null
                    }
                } else null
            )
            return
        }
        TodoRoute.DETAIL -> {
            val task = allTasks.find { it.id == selectedTaskId } ?: drafts.find { it.id == selectedTaskId }
            if (task == null) { route = TodoRoute.HOME; return }
            TodoDetailScreen(
                task = task,
                category = categoryFor(task.categoryId),
                onBack = { route = TodoRoute.HOME; selectedTaskId = null },
                onEdit = { editingTask = task; route = TodoRoute.EDITOR },
                onDelete = {
                    scope.launch {
                        ImageStore.deleteImages(task.attachmentPaths)
                        db.todoDao().delete(task)
                        route = TodoRoute.HOME; selectedTaskId = null
                    }
                },
                onToggleComplete = {
                    scope.launch {
                        val nowCompleted = !task.isCompleted
                        db.todoDao().upsert(
                            task.copy(
                                isCompleted = nowCompleted,
                                status = if (nowCompleted) com.vaultra.app.data.TodoStatus.COMPLETED else com.vaultra.app.data.TodoStatus.PENDING,
                                progress = if (nowCompleted) 100 else task.progress,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                },
                onToggleChecklistItem = { itemId ->
                    scope.launch {
                        val updated = task.checklist.map { if (it.id == itemId) it.copy(isDone = !it.isDone) else it }
                        val doneCount = updated.count { it.isDone }
                        val newProgress = if (updated.isEmpty()) task.progress else (doneCount * 100 / updated.size)
                        db.todoDao().upsert(task.copy(checklist = updated, progress = newProgress, updatedAt = System.currentTimeMillis()))
                    }
                }
            )
            return
        }
        TodoRoute.CATEGORIES -> {
            TodoCategoryManagerScreen(db = db, onBack = { route = TodoRoute.HOME })
            return
        }
        TodoRoute.DRAFTS -> {
            DraftsScreen(
                drafts = drafts,
                onBack = { route = TodoRoute.HOME },
                onOpenDraft = { editingTask = it; route = TodoRoute.EDITOR },
                onDiscard = { d -> scope.launch { ImageStore.deleteImages(d.attachmentPaths); db.todoDao().delete(d) } }
            )
            return
        }
        TodoRoute.HOME -> Unit
    }

    val filteredTasks = remember(allTasks, query, filters, sort) {
        allTasks.filter { it.matchesQuery(query) && it.matchesFilters(filters) }.sortedByOption(sort)
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Search tasks\u2026", color = TextDim) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent2, unfocusedBorderColor = Line,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent2
                            )
                        )
                    } else {
                        Text("TO-DO", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (searchActive) { searchActive = false; query = "" } else onExit() }) {
                        Icon(if (searchActive) Icons.Default.Close else Icons.Default.ArrowBack, "Back", tint = Accent2)
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) { Icon(Icons.Default.Search, "Search", tint = TextDim) }
                        Box {
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(Icons.Default.FilterList, "Filter", tint = if (filters.isActive) Accent2 else TextDim)
                            }
                            if (filters.count > 0) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(7.dp).clip(CircleShape).background(Accent)
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort", tint = TextDim) }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                TodoSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label, color = if (sort == option) Accent2 else TextPrimary) },
                                        onClick = { sort = option; showSortMenu = false }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { route = TodoRoute.CATEGORIES }) { Icon(Icons.Default.Category, "Categories", tint = TextDim) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingTask = null; isCreatingNew = true; route = TodoRoute.EDITOR },
                containerColor = Accent
            ) { Icon(Icons.Default.Add, "Add task") }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = view.ordinal,
                containerColor = Color.Transparent,
                contentColor = Accent2
            ) {
                Tab(selected = view == TodoView.LIST, onClick = { view = TodoView.LIST }, text = { Text("LIST", fontSize = 11.sp) })
                Tab(selected = view == TodoView.CALENDAR, onClick = { view = TodoView.CALENDAR }, text = { Text("CALENDAR", fontSize = 11.sp) })
                Tab(selected = view == TodoView.STATS, onClick = { view = TodoView.STATS }, text = { Text("STATS", fontSize = 11.sp) })
            }

            when (view) {
                TodoView.LIST -> TodoListView(
                    tasks = filteredTasks,
                    allTasksUnfiltered = allTasks,
                    categories = categories,
                    draftsCount = drafts.size,
                    onOpenDrafts = { route = TodoRoute.DRAFTS },
                    onTaskClick = { selectedTaskId = it.id; route = TodoRoute.DETAIL },
                    onToggleComplete = { task ->
                        scope.launch {
                            val nowCompleted = !task.isCompleted
                            db.todoDao().upsert(
                                task.copy(
                                    isCompleted = nowCompleted,
                                    status = if (nowCompleted) com.vaultra.app.data.TodoStatus.COMPLETED else com.vaultra.app.data.TodoStatus.PENDING,
                                    progress = if (nowCompleted) 100 else task.progress,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    },
                    onTogglePin = { task -> scope.launch { db.todoDao().upsert(task.copy(isPinned = !task.isPinned, updatedAt = System.currentTimeMillis())) } },
                    onToggleFavorite = { task -> scope.launch { db.todoDao().upsert(task.copy(isFavorite = !task.isFavorite, updatedAt = System.currentTimeMillis())) } },
                    activeCategoryFilter = filters.categoryId,
                    onCategoryFilterChange = { filters = filters.copy(categoryId = it) }
                )
                TodoView.CALENDAR -> TodoCalendarView(tasks = allTasks, onTaskClick = { selectedTaskId = it.id; route = TodoRoute.DETAIL })
                TodoView.STATS -> TodoStatsView(tasks = allTasks, categories = categories)
            }
        }
    }

    if (showFilterSheet) {
        TodoFilterSheet(
            categories = categories, current = filters,
            onApply = { filters = it; showFilterSheet = false },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun TodoListView(
    tasks: List<TodoEntry>,
    allTasksUnfiltered: List<TodoEntry>,
    categories: List<TodoCategory>,
    draftsCount: Int,
    onOpenDrafts: () -> Unit,
    onTaskClick: (TodoEntry) -> Unit,
    onToggleComplete: (TodoEntry) -> Unit,
    onTogglePin: (TodoEntry) -> Unit,
    onToggleFavorite: (TodoEntry) -> Unit,
    activeCategoryFilter: String?,
    onCategoryFilterChange: (String?) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TodoDashboardCard(allTasksUnfiltered) }

        if (draftsCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Warn.copy(alpha = 0.12f))
                        .clickable(onClick = onOpenDrafts)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Drafts, null, tint = Warn, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("$draftsCount draft${if (draftsCount == 1) "" else "s"} waiting \u2014 tap to resume", color = Warn, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = activeCategoryFilter == null,
                        onClick = { onCategoryFilterChange(null) },
                        label = { Text("All", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.25f), selectedLabelColor = Accent2, containerColor = BgCard, labelColor = TextDim)
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = activeCategoryFilter == cat.id,
                        onClick = { onCategoryFilterChange(cat.id) },
                        leadingIcon = { Icon(iconForKey(cat.icon), null, tint = colorFromHex(cat.colorHex), modifier = Modifier.size(14.dp)) },
                        label = { Text(cat.name, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorFromHex(cat.colorHex).copy(alpha = 0.22f),
                            selectedLabelColor = colorFromHex(cat.colorHex), containerColor = BgCard, labelColor = TextDim
                        )
                    )
                }
            }
        }

        if (tasks.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.TaskAlt, null, tint = TextDim, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No tasks match. Tap + to create one.", color = TextDim, fontSize = 13.sp)
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                TodoTaskCard(
                    task = task,
                    category = categories.find { it.id == task.categoryId },
                    onClick = { onTaskClick(task) },
                    onToggleComplete = { onToggleComplete(task) },
                    onTogglePin = { onTogglePin(task) },
                    onToggleFavorite = { onToggleFavorite(task) }
                )
            }
        }
        item { Spacer(Modifier.height(70.dp)) }
    }
}

@Composable
private fun TodoDashboardCard(tasks: List<TodoEntry>) {
    val completed = tasks.count { it.isCompleted }
    val pending = tasks.size - completed
    val overdue = tasks.count { it.isOverdue() }
    val completionRate = if (tasks.isNotEmpty()) completed.toFloat() / tasks.size else 0f

    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = BgCard)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Task Summary", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    DashStat("Total", tasks.size.toString(), TextPrimary)
                    DashStat("Done", completed.toString(), Good)
                    DashStat("Pending", pending.toString(), Warn)
                    DashStat("Overdue", overdue.toString(), if (overdue > 0) Accent2 else TextDim)
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { completionRate }, modifier = Modifier.size(52.dp), color = Accent, trackColor = Line, strokeWidth = 5.dp)
                Text("${(completionRate * 100).toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DashStat(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 11.sp, color = TextDim)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun TodoTaskCard(
    task: TodoEntry,
    category: TodoCategory?,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val overdue = task.isOverdue()
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = task.isCompleted, onCheckedChange = { onToggleComplete() },
                    colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Color.White)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        category?.let { cat ->
                            Icon(iconForKey(cat.icon), null, tint = colorFromHex(cat.colorHex), modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(cat.name, color = TextDim, fontSize = 11.sp)
                            Spacer(Modifier.width(8.dp))
                        }
                        task.dueAt?.let {
                            Icon(Icons.Default.Event, null, tint = if (overdue) Accent2 else TextDim, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(formatDueDateTime(it), color = if (overdue) Accent2 else TextDim, fontSize = 11.sp)
                        }
                    }
                }
                IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.PushPin, "Pin", tint = if (task.isPinned) Accent2 else TextDim, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(if (task.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite", tint = if (task.isFavorite) Warn else TextDim, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(priorityColor(task.priority).copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(priorityLabel(task.priority), fontSize = 10.sp, color = priorityColor(task.priority), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(statusColor(task.status).copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(task.status, fontSize = 10.sp, color = statusColor(task.status), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                if (task.checklist.isNotEmpty()) {
                    Text("${task.checklist.count { it.isDone }}/${task.checklist.size}", fontSize = 10.sp, color = TextDim)
                } else if (task.progress > 0) {
                    Text("${task.progress}%", fontSize = 10.sp, color = TextDim)
                }
            }
        }
    }
}

@Composable
private fun DraftsScreen(drafts: List<TodoEntry>, onBack: () -> Unit, onOpenDraft: (TodoEntry) -> Unit, onDiscard: (TodoEntry) -> Unit) {
    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("DRAFTS", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Accent2) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (drafts.isEmpty()) {
                item { Text("No drafts saved.", color = TextDim, modifier = Modifier.padding(top = 24.dp)) }
            }
            items(drafts, key = { it.id }) { draft ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgCard)
                        .clickable { onOpenDraft(draft) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(draft.title.ifBlank { "Untitled draft" }, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Last edited " + formatFullTimestamp(draft.updatedAt), color = TextDim, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onDiscard(draft) }) { Icon(Icons.Default.Delete, "Discard draft", tint = Accent2) }
                }
            }
        }
    }
}
