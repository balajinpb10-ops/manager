package com.vaultra.app.ui.screens.todo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.data.TodoPriority
import com.vaultra.app.data.TodoStatus
import com.vaultra.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ---------------------------------------------------------------------- */
/*  Category icon catalogue                                               */
/* ---------------------------------------------------------------------- */

/** Every icon a category (built-in or custom) can be assigned, keyed by a stable string
 *  stored in [com.vaultra.app.data.TodoCategory.icon]. */
val TodoCategoryIcons: Map<String, ImageVector> = linkedMapOf(
    "personal" to Icons.Filled.Person,
    "work" to Icons.Filled.Work,
    "shopping" to Icons.Filled.ShoppingCart,
    "study" to Icons.Filled.School,
    "health" to Icons.Filled.Favorite,
    "finance" to Icons.Filled.AttachMoney,
    "travel" to Icons.Filled.Flight,
    "custom" to Icons.Filled.Label,
    "home" to Icons.Filled.Home,
    "food" to Icons.Filled.Restaurant,
    "fitness" to Icons.Filled.FitnessCenter,
    "car" to Icons.Filled.DirectionsCar,
    "pets" to Icons.Filled.Pets,
    "music" to Icons.Filled.MusicNote,
    "movie" to Icons.Filled.Movie,
    "book" to Icons.Filled.MenuBook,
    "code" to Icons.Filled.Code,
    "camera" to Icons.Filled.CameraAlt,
    "call" to Icons.Filled.Call,
    "gift" to Icons.Filled.CardGiftcard,
    "folder" to Icons.Filled.Folder,
    "bookmark" to Icons.Filled.Bookmark,
    "flag" to Icons.Filled.Flag,
    "alarm" to Icons.Filled.Alarm,
    "event" to Icons.Filled.Event,
    "game" to Icons.Filled.SportsEsports,
    "cart" to Icons.Filled.LocalGroceryStore,
    "star" to Icons.Filled.Star
)

fun iconForKey(key: String?): ImageVector = TodoCategoryIcons[key] ?: Icons.Filled.Label

/** Swatches offered when assigning a color to a category. */
val TodoCategoryColors: List<String> = listOf(
    "#E63950", "#4C6FFF", "#FF9F45", "#7C5CFC", "#2ECC71",
    "#FFD24C", "#22C1C3", "#FF5470", "#5A6EE0", "#22A8E0",
    "#E0227A", "#9AA2B1"
)

fun colorFromHex(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Accent
}

/* ---------------------------------------------------------------------- */
/*  Priority                                                              */
/* ---------------------------------------------------------------------- */

fun priorityColor(priority: Int): Color = when (priority) {
    TodoPriority.HIGH -> Color(0xFFFF5252)
    TodoPriority.MEDIUM -> Color(0xFFFFC107)
    else -> Color(0xFF4CAF50)
}

fun priorityLabel(priority: Int): String = when (priority) {
    TodoPriority.HIGH -> "High"
    TodoPriority.MEDIUM -> "Medium"
    else -> "Low"
}

fun priorityEmoji(priority: Int): String = when (priority) {
    TodoPriority.HIGH -> "\uD83D\uDD34"
    TodoPriority.MEDIUM -> "\uD83D\uDFE1"
    else -> "\uD83D\uDFE2"
}

val AllPriorities = listOf(TodoPriority.HIGH, TodoPriority.MEDIUM, TodoPriority.LOW)

/* ---------------------------------------------------------------------- */
/*  Status                                                                */
/* ---------------------------------------------------------------------- */

val AllStatuses = listOf(TodoStatus.PENDING, TodoStatus.IN_PROGRESS, TodoStatus.COMPLETED, TodoStatus.CANCELLED)

fun statusColor(status: String): Color = when (status) {
    TodoStatus.COMPLETED -> Good
    TodoStatus.IN_PROGRESS -> Color(0xFF4C9DFF)
    TodoStatus.CANCELLED -> TextDim
    else -> Warn
}

/* ---------------------------------------------------------------------- */
/*  Sorting                                                                */
/* ---------------------------------------------------------------------- */

enum class TodoSort(val label: String) {
    DUE_DATE("Due Date"),
    CREATED_DATE("Created Date"),
    PRIORITY("Priority"),
    ALPHABETICAL("Alphabetical"),
    COMPLETED_FIRST("Completed"),
    PENDING_FIRST("Pending")
}

fun List<TodoEntry>.sortedByOption(sort: TodoSort): List<TodoEntry> = when (sort) {
    TodoSort.DUE_DATE -> sortedWith(compareBy(nullsLast()) { it.dueAt })
    TodoSort.CREATED_DATE -> sortedByDescending { it.createdAt }
    TodoSort.PRIORITY -> sortedByDescending { it.priority }
    TodoSort.ALPHABETICAL -> sortedBy { it.title.lowercase(Locale.getDefault()) }
    TodoSort.COMPLETED_FIRST -> sortedByDescending { it.isCompleted }
    TodoSort.PENDING_FIRST -> sortedBy { it.isCompleted }
}

/* ---------------------------------------------------------------------- */
/*  Filtering                                                              */
/* ---------------------------------------------------------------------- */

data class TodoFilters(
    val categoryId: String? = null,
    val priority: Int? = null,
    val status: String? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null
) {
    val isActive: Boolean get() = categoryId != null || priority != null || status != null || dateFrom != null || dateTo != null
    val count: Int get() = listOfNotNull(categoryId, priority, status, dateFrom).size
}

fun TodoEntry.matchesFilters(filters: TodoFilters): Boolean {
    if (filters.categoryId != null && categoryId != filters.categoryId) return false
    if (filters.priority != null && priority != filters.priority) return false
    if (filters.status != null && status != filters.status) return false
    if (filters.dateFrom != null) {
        val due = dueAt ?: return false
        if (due < filters.dateFrom) return false
        if (filters.dateTo != null && due > filters.dateTo) return false
    }
    return true
}

fun TodoEntry.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase(Locale.getDefault())
    return title.lowercase(Locale.getDefault()).contains(q) ||
        description.lowercase(Locale.getDefault()).contains(q) ||
        tags.lowercase(Locale.getDefault()).contains(q) ||
        notes.lowercase(Locale.getDefault()).contains(q)
}

fun TodoEntry.isOverdue(): Boolean =
    dueAt != null && dueAt < System.currentTimeMillis() && !isCompleted && status != TodoStatus.CANCELLED

/* ---------------------------------------------------------------------- */
/*  Date / time formatting                                                */
/* ---------------------------------------------------------------------- */

fun formatDueDate(millis: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
fun formatDueTime(millis: Long): String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))
fun formatDueDateTime(millis: Long): String = "${formatDueDate(millis)} \u00B7 ${formatDueTime(millis)}"
fun formatFullTimestamp(millis: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))

fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
}.timeInMillis

fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
