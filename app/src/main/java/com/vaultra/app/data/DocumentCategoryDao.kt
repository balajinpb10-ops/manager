package com.vaultra.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentCategoryDao {
    @Query("SELECT * FROM document_categories ORDER BY isBuiltIn DESC, name ASC")
    fun getAll(): Flow<List<DocumentCategory>>

    @Query("SELECT * FROM document_categories")
    suspend fun all(): List<DocumentCategory>

    @Query("SELECT * FROM document_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: DocumentCategory)

    @Delete
    suspend fun delete(category: DocumentCategory)
}
