package com.vaultra.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentFolderDao {
    @Query("SELECT * FROM document_folders ORDER BY isFavorite DESC, name ASC")
    fun getAll(): Flow<List<DocumentFolder>>

    @Query("SELECT * FROM document_folders")
    suspend fun all(): List<DocumentFolder>

    @Query("SELECT * FROM document_folders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentFolder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: DocumentFolder)

    @Delete
    suspend fun delete(folder: DocumentFolder)
}
