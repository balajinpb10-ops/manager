package com.vaultra.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val mood: String,
    val weather: String,
    val tags: String,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
