package com.vaultra.app.data

/**
 * A single subtask line on a TodoEntry's checklist. Stored inline as a JSON array
 * on the parent task (via Converters) rather than as its own Room table, since
 * checklist items are never queried independently of their parent task.
 */
data class ChecklistItem(
    val id: String,
    val text: String,
    val isDone: Boolean = false
)
