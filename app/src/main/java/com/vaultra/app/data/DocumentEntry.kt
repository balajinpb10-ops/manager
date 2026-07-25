package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntry(
    @PrimaryKey val id: String,
    val docType: String,
    val holderName: String,
    val docNumber: String,
    val notes: String,
    val updatedAt: Long,
    /** Absolute paths of attached photos, stored only in this app's private storage. */
    val images: List<String> = emptyList()
)
