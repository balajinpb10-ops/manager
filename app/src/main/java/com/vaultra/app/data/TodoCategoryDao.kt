package com.vaultra.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoCategoryDao {
    @Query("SELECT * FROM todo_categories ORDER BY isBuiltIn DESC, name ASC")
    fun getAll(): Flow<List<TodoCategory>>

    @Query("SELECT * FROM todo_categories")
    suspend fun all(): List<TodoCategory>

    @Query("SELECT * FROM todo_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TodoCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: TodoCategory)

    @Delete
    suspend fun delete(category: TodoCategory)
}
