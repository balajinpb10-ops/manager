package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntry(
    @PrimaryKey val id: String,
    val title: String = "",
    /** Links to DocumentCategory.id. Null means uncategorized. */
    val categoryId: String? = null,
    /** Links to DocumentFolder.id. Null means it sits at the root (no folder). */
    val folderId: String? = null,
    val docType: String,
    val holderName: String,
    val docNumber: String,
    val issueDate: Long? = null,
    val expiryDate: Long? = null,
    val issuedBy: String = "",
    val description: String = "",
    val notes: String,
    /** Comma-separated freeform tags. */
    val tags: String = "",
    val isFavorite: Boolean = false,
    /** True while this document is an auto-saved draft that hasn't been explicitly saved yet. */
    val isDraft: Boolean = false,
    val updatedAt: Long,
    val createdAt: Long = updatedAt,
    /** Absolute paths of attached images/PDFs, stored only in this app's private storage. Unlimited count. */
    val attachmentPaths: List<String> = emptyList()
)
