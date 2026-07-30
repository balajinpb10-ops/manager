package com.vaultra.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.TodoCategory
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.data.TodoStatus
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private enum class TodoSection { LIST, CALENDAR, STATS }
enum class TodoSort { DUE_DATE, CREATED_DATE, PRIORITY, ALPHABETICAL, COMPLETED_FIRST, PENDING_FIRST }
private enum class DateFilter { ALL, TODAY, THIS_WEEK, OVERDUE }

private data class TodoFilters(
    val categoryIds: Set<String> = emptySet(),
    val priorities: Set<Int> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val dateFilter: DateFilter = DateFilter.ALL
) {
    fun isEmpty() = categoryIds.isEmpty() && priorities.isEmpty() && statuses.isEmpty() && dateFilter == DateFilter.ALL
}

@Composable
fun TodoScreen(db: VaultDatabase, onBack: () -> Unit) {
    val tasks by db.todoDao().active().collectAsState(initial = emptyList())
    val drafts by db.todoDao().drafts().collectAsState(initial = emptyList())
    val categories by db.todoCategoryDao().getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var section by remember { mutableStateOf(TodoSection.LIST) }
    var search by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(TodoFilters()) }
    var sort by remember { mutableStateOf(TodoSort.DUE_DATE) }
    var selectedCategoryChip by remember { mutableStateOf<String?>(null) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showDrafts by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<TodoEntry?>(null) }
    var detailTarget by remember { mutableStateOf<TodoEntry?>(null) }

    // Close whichever sheet/dialog is open on back, before letting the press bubble up
    // to the parent screen (which would otherwise navigate away from To-Do entirely).
    BackHandler(enabled = showEditor || detailTarget != null || showFilterSheet || showCategoryManager || showDrafts) {
        when {
            showEditor -> showEditor = false
            detailTarget != null -> detailTarget = null
            showFilterSheet -> showFilterSheet = false
            showCategoryManager -> showCategoryManager = false
            showDrafts -> showDrafts = false
        }
    }

    val now = System.currentTimeMillis()
    val filtered = remember(tasks, search, filters, selectedCategoryChip, sort) {
        var list = tasks.filter { t ->
            val matchesSearch = search.isBlank() ||
                t.title.contains(search, true) ||
                t.description.contains(search, true) ||
                t.tags.contains(search, true)
            val matchesCategory = filters.categoryIds.isEmpty() || filters.categoryIds.contains(t.categoryId)
            val matchesChip = selectedCategoryChip == null || t.categoryId == selectedCategoryChip
            val matchesPriority = filters.priorities.isEmpty() || filters.priorities.contains(t.priority)
            val matchesStatus = filters.statuses.isEmpty() || filters.statuses.contains(t.status)
            val matchesDate = when (filters.dateFilter) {
                DateFilter.ALL -> true
                DateFilter.OVERDUE -> t.dueAt != null && t.dueAt < now && !t.isCompleted
                DateFilter.TODAY -> t.dueAt != null && isSameDay(t.dueAt, now)
                DateFilter.THIS_WEEK -> t.dueAt != null && isWithinDays(t.dueAt, now, 7)
            }
            matchesSearch && matchesCategory && matchesChip && matchesPriority && matchesStatus && matchesDate
        }
        list = when (sort) {
            TodoSort.DUE_DATE -> list.sortedWith(compareBy(nullsLast<Long>()) { it.dueAt })
            TodoSort.CREATED_DATE -> list.sortedByDescending { it.createdAt }
            TodoSort.PRIORITY -> list.sortedByDescending { it.priority }
            TodoSort.ALPHABETICAL -> list.sortedBy { it.title.lowercase() }
            TodoSort.COMPLETED_FIRST -> list.sortedByDescending { it.isCompleted }
            TodoSort.PENDING_FIRST -> list.sortedBy { it.isCompleted }
        }
        // Pinned always float to top regardless of sort, matching the DAO's own ordering.
        list.sortedByDescending { it.isPinned }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Accent2) }
                Text("TO-DO", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                if (drafts.isNotEmpty()) {
                    TextButton(onClick = { showDrafts = true }) {
                        Icon(Icons.Filled.Drafts, contentDescription = null, tint = Warn, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${drafts.size}", color = Warn)
                    }
                }
                IconButton(onClick = { showCategoryManager = true }) {
                    Icon(Icons.Filled.Category, contentDescription = "Manage categories", tint = TextDim)
                }
            }

            TodoDashboard(tasks)
            Spacer(Modifier.height(14.dp))

            // Section switcher
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionTab("List", section == TodoSection.LIST, Modifier.weight(1f)) { section = TodoSection.LIST }
                SectionTab("Calendar", section == TodoSection.CALENDAR, Modifier.weight(1f)) { section = TodoSection.CALENDAR }
                SectionTab("Stats", section == TodoSection.STATS, Modifier.weight(1f)) { section = TodoSection.STATS }
            }
            Spacer(Modifier.height(14.dp))

            when (section) {
                TodoSection.LIST -> Column(Modifier.weight(1f)) {
                    // Search + filter + sort
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            placeholder = { Text("Search tasks", fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextDim) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions.Default,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Box {
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Filter", tint = if (!filters.isEmpty()) Accent2 else TextDim)
                            }
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = TextDim)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                for ((opt, label) in listOf(
                                    TodoSort.DUE_DATE to "Due Date",
                                    TodoSort.CREATED_DATE to "Created Date",
                                    TodoSort.PRIORITY to "Priority",
                                    TodoSort.ALPHABETICAL to "Alphabetical",
                                    TodoSort.COMPLETED_FIRST to "Completed first",
                                    TodoSort.PENDING_FIRST to "Pending first"
                                )) {
                                    DropdownMenuItem(text = { Text(label) }, onClick = { sort = opt; showSortMenu = false })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // Category chip filter row
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            CategoryChip("All", selectedCategoryChip == null, null) { selectedCategoryChip = null }
                        }
                        items(categories, key = { it.id }) { cat ->
                            CategoryChip(cat.name, selectedCategoryChip == cat.id, cat) { selectedCategoryChip = if (selectedCategoryChip == cat.id) null else cat.id }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (filtered.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.TaskAlt, contentDescription = null, tint = TextDim, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(if (tasks.isEmpty()) "No tasks yet — tap + to add one." else "No tasks match your filters.", color = TextDim, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                            items(filtered, key = { it.id }) { task ->
                                TaskCard(
                                    task = task,
                                    category = categories.find { it.id == task.categoryId },
                                    onClick = { detailTarget = task },
                                    onToggleComplete = {
                                        scope.launch {
                                            val nowCompleted = !task.isCompleted
                                            db.todoDao().upsert(
                                                task.copy(
                                                    isCompleted = nowCompleted,
                                                    status = if (nowCompleted) TodoStatus.COMPLETED else TodoStatus.PENDING,
                                                    progress = if (nowCompleted) 100 else task.progress,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                    },
                                    onTogglePin = { scope.launch { db.todoDao().upsert(task.copy(isPinned = !task.isPinned, updatedAt = System.currentTimeMillis())) } },
                                    onToggleFavorite = { scope.launch { db.todoDao().upsert(task.copy(isFavorite = !task.isFavorite, updatedAt = System.currentTimeMillis())) } }
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
                TodoSection.CALENDAR -> Box(Modifier.weight(1f)) {
                    TodoCalendarView(tasks = tasks, categories = categories, onTaskClick = { detailTarget = it })
                }
                TodoSection.STATS -> Box(Modifier.weight(1f)) {
                    TodoStatsView(tasks = tasks, categories = categories)
                }
            }
        }

        FloatingActionButton(
            onClick = { editTarget = null; showEditor = true },
            containerColor = Accent, contentColor = Color.White, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "Add task") }
    }

    if (showFilterSheet) {
        TodoFilterSheetImpl(
            categories = categories,
            current = filters,
            onApply = { filters = it; showFilterSheet = false },
            onDismiss = { showFilterSheet = false }
        )
    }

    if (showCategoryManager) {
        CategoryManagerSheet(db = db, onDismiss = { showCategoryManager = false })
    }

    if (showDrafts) {
        TodoDraftsSheet(
            drafts = drafts,
            onDismiss = { showDrafts = false },
            onRestore = { draft -> showDrafts = false; editTarget = draft; showEditor = true },
            onDiscard = { draft -> scope.launch { db.todoDao().delete(draft) } }
        )
    }

    if (showEditor) {
        TaskEditorSheet(
            db = db,
            categories = categories,
            existing = editTarget,
            onDismiss = { showEditor = false }
        )
    }

    detailTarget?.let { task ->
        TaskDetailSheet(
            task = task,
            category = categories.find { it.id == task.categoryId },
            db = db,
            onDismiss = { detailTarget = null },
            onEdit = { detailTarget = null; editTarget = task; showEditor = true }
        )
    }
}

@Composable
private fun SectionTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, category: TodoCategory?, onClick: () -> Unit) {
    val color = category?.let { parseHexColor(it.colorHex) } ?: Accent2
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else BgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (category != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) color else TextDim, fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun TodoDashboard(tasks: List<TodoEntry>) {
    val completed = tasks.count { it.isCompleted }
    val pending = tasks.size - completed
    val overdue = tasks.count { it.dueAt != null && it.dueAt < System.currentTimeMillis() && !it.isCompleted }
    val rate = if (tasks.isNotEmpty()) completed.toFloat() / tasks.size else 0f

    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = BgCard)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Task Summary", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    DashboardStat("Total", tasks.size.toString(), TextPrimary)
                    DashboardStat("Done", completed.toString(), Good)
                    DashboardStat("Pending", pending.toString(), Warn)
                    DashboardStat("Overdue", overdue.toString(), if (overdue > 0) Accent2 else TextDim)
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { rate }, modifier = Modifier.size(54.dp), color = Accent, trackColor = Line, strokeWidth = 6.dp)
                Text("${(rate * 100).toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DashboardStat(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 11.sp, color = TextDim)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun TaskCard(
    task: TodoEntry,
    category: TodoCategory?,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val overdue = task.dueAt != null && task.dueAt < System.currentTimeMillis() && !task.isCompleted
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleComplete() }, colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Color.White))
                CategoryBadge(category, size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PriorityBadge(task.priority)
                        StatusChip(task.status)
                        if (task.dueAt != null) {
                            Text(
                                formatDueDateTime(task.dueAt),
                                fontSize = 11.sp,
                                color = if (overdue) Accent2 else TextDim,
                                fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                    Icon(if (task.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = "Pin", tint = if (task.isPinned) Accent2 else TextDim, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(if (task.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Favorite", tint = if (task.isFavorite) Accent2 else TextDim, modifier = Modifier.size(18.dp))
                }
            }
            val progress = effectiveProgress(task)
            if (progress > 0 && !task.isCompleted) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                    color = Accent, trackColor = Line
                )
            }
        }
    }
}

@Composable
fun PriorityBadge(priority: Int) {
    val color = priorityColor(priority)
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(priorityLabel(priority), color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusChip(status: String) {
    val color = when (status) {
        TodoStatus.COMPLETED -> Good
        TodoStatus.IN_PROGRESS -> Accent2
        TodoStatus.CANCELLED -> TextDim
        else -> Warn
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(status, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** When a task has a checklist, progress is derived from how many subtasks are done; otherwise the stored manual value is used. */
fun effectiveProgress(task: TodoEntry): Int {
    if (task.checklist.isNotEmpty()) {
        val done = task.checklist.count { it.isDone }
        return (done * 100) / task.checklist.size
    }
    return task.progress
}

fun formatDueDateTime(dueAt: Long): String {
    val sameYear = isSameYear(dueAt, System.currentTimeMillis())
    val datePattern = if (sameYear) "MMM d" else "MMM d, yyyy"
    val date = SimpleDateFormat(datePattern, Locale.getDefault()).format(java.util.Date(dueAt))
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date(dueAt))
    return "$date · $time"
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) && ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun isSameYear(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR)
}

private fun isWithinDays(a: Long, now: Long, days: Int): Boolean {
    val diff = a - now
    return diff >= 0 && diff <= days.toLong() * 24 * 60 * 60 * 1000
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoFilterSheetImpl(categories: List<TodoCategory>, current: TodoFilters, onApply: (TodoFilters) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var categoryIds by remember { mutableStateOf(current.categoryIds) }
    var priorities by remember { mutableStateOf(current.priorities) }
    var statuses by remember { mutableStateOf(current.statuses) }
    var dateFilter by remember { mutableStateOf(current.dateFilter) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Text("Filter Tasks", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            Text("CATEGORY", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRowWrap {
                categories.forEach { cat ->
                    FilterToggle(cat.name, categoryIds.contains(cat.id)) {
                        categoryIds = if (categoryIds.contains(cat.id)) categoryIds - cat.id else categoryIds + cat.id
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("PRIORITY", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRowWrap {
                listOf(2 to "High", 1 to "Medium", 0 to "Low").forEach { (p, label) ->
                    FilterToggle(label, priorities.contains(p)) { priorities = if (priorities.contains(p)) priorities - p else priorities + p }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("STATUS", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRowWrap {
                listOf(TodoStatus.PENDING, TodoStatus.IN_PROGRESS, TodoStatus.COMPLETED, TodoStatus.CANCELLED).forEach { s ->
                    FilterToggle(s, statuses.contains(s)) { statuses = if (statuses.contains(s)) statuses - s else statuses + s }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("DUE DATE", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRowWrap {
                listOf(DateFilter.ALL to "All", DateFilter.TODAY to "Today", DateFilter.THIS_WEEK to "This Week", DateFilter.OVERDUE to "Overdue").forEach { (df, label) ->
                    FilterToggle(label, dateFilter == df) { dateFilter = df }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { categoryIds = emptySet(); priorities = emptySet(); statuses = emptySet(); dateFilter = DateFilter.ALL },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
                Button(
                    onClick = { onApply(TodoFilters(categoryIds, priorities, statuses, dateFilter)) },
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Apply", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowWrap(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun FilterToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accent.copy(alpha = 0.22f) else BgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Accent2 else TextDim, fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoDraftsSheet(drafts: List<TodoEntry>, onDismiss: () -> Unit, onRestore: (TodoEntry) -> Unit, onDiscard: (TodoEntry) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Text("Drafts", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text("Unsaved tasks are kept here automatically.", color = TextDim, fontSize = 12.5.sp)
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                items(drafts, key = { it.id }) { draft ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(draft.title.ifBlank { "Untitled draft" }, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Last edited " + SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(java.util.Date(draft.updatedAt)), color = TextDim, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onRestore(draft) }) { Text("Restore", color = Accent2) }
                        IconButton(onClick = { onDiscard(draft) }) { Icon(Icons.Filled.Delete, contentDescription = "Discard draft", tint = TextDim) }
                    }
                }
            }
        }
    }
}
