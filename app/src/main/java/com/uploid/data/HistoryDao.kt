package com.uploid.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(entry: HistoryEntry): Long

    @Query("DELETE FROM history_entries WHERE id NOT IN (SELECT id FROM history_entries ORDER BY timestamp DESC LIMIT 1000)")
    suspend fun pruneHistory()

    @Transaction
    suspend fun insert(entry: HistoryEntry): Long {
        val id = insertRaw(entry)
        pruneHistory()
        return id
    }

    @Query("DELETE FROM history_entries")
    suspend fun clearHistory()
}
