package com.vaultra.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isDraft = 0 ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<DocumentEntry>>

    @Query("SELECT * FROM documents WHERE isDraft = 1 ORDER BY updatedAt DESC")
    fun drafts(): Flow<List<DocumentEntry>>

    @Query("SELECT * FROM documents")
    suspend fun all(): List<DocumentEntry>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntry)

    @Delete
    suspend fun delete(document: DocumentEntry)
}
