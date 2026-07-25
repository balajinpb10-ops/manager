package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val id: String,
    val name: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val category: String,
    val totpSecret: String,
    val updatedAt: Long
)
