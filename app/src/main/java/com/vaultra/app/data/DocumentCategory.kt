package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_categories")
data class DocumentCategory(
    @PrimaryKey val id: String,
    val name: String,
    /** Hex color, e.g. "#4C6FFF", used for the category chip/dot. */
    val colorHex: String,
    /** Icon key mapped to a Material icon in the UI layer. */
    val icon: String,
    /** True for the built-in categories shipped with the app; false for user-created ones. */
    val isBuiltIn: Boolean = false,
    val updatedAt: Long
)
