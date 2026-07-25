package com.vaultra.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_entries WHERE isArchived = 0 AND isDraft = 0 ORDER BY isPinned DESC, isCompleted ASC, dueAt ASC, updatedAt DESC")
    fun active(): Flow<List<TodoEntry>>

    @Query("SELECT * FROM todo_entries WHERE isDraft = 1 ORDER BY updatedAt DESC")
    fun drafts(): Flow<List<TodoEntry>>

    @Query("SELECT * FROM todo_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TodoEntry?

    @Query("SELECT * FROM todo_entries")
    suspend fun all(): List<TodoEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TodoEntry)

    @Delete
    suspend fun delete(item: TodoEntry)
}
