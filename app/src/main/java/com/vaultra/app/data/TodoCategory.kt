package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_categories")
data class TodoCategory(
    @PrimaryKey val id: String,
    val name: String,
    /** Hex color, e.g. "#E63950", used for the category chip/dot and stat charts. */
    val colorHex: String,
    /** Icon key mapped to a Material icon in the UI layer (e.g. "work", "shopping", "custom"). */
    val icon: String,
    /** True for the seven default categories shipped with the app; false for user-created ones. */
    val isBuiltIn: Boolean = false,
    val updatedAt: Long
)
