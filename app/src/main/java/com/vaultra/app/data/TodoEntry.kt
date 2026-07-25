package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object TodoStatus {
    const val PENDING = "Pending"
    const val IN_PROGRESS = "In Progress"
    const val COMPLETED = "Completed"
    const val CANCELLED = "Cancelled"
}

object TodoPriority {
    const val LOW = 0
    const val MEDIUM = 1
    const val HIGH = 2
}

@Entity(tableName = "todo_entries")
data class TodoEntry(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    /** Links to TodoCategory.id. Null means uncategorized. */
    val categoryId: String?,
    /** Comma-separated freeform tags. */
    val tags: String,
    val notes: String = "",
    val priority: Int,
    /** Combined due date + time as a single instant; the UI splits it into date/time pickers. */
    val dueAt: Long?,
    val status: String = TodoStatus.PENDING,
    val isCompleted: Boolean,
    val isPinned: Boolean,
    val isFavorite: Boolean = false,
    val isArchived: Boolean,
    /** True while the task is an auto-saved draft that hasn't been explicitly saved yet. */
    val isDraft: Boolean = false,
    val progress: Int,
    val checklist: List<ChecklistItem> = emptyList(),
    /** Absolute paths of attached images/PDFs/documents, stored only in this app's private storage. */
    val attachmentPaths: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
)
