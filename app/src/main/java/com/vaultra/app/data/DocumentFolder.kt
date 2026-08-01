package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_folders")
data class DocumentFolder(
    @PrimaryKey val id: String,
    val name: String,
    /** Links to another DocumentFolder.id for unlimited nesting. Null means a root-level folder. */
    val parentFolderId: String? = null,
    val isFavorite: Boolean = false,
    val updatedAt: Long
)
