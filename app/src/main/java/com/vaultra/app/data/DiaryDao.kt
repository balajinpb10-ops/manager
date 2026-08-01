package com.vaultra.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE isArchived = 0 ORDER BY createdAt DESC") fun active(): Flow<List<DiaryEntry>>
    @Query("SELECT * FROM diary_entries") suspend fun all(): List<DiaryEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: DiaryEntry)
}
