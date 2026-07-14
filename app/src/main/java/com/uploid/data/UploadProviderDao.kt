package com.uploid.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadProviderDao {
    @Query("SELECT * FROM upload_providers ORDER BY name COLLATE NOCASE ASC")
    fun getAllProviders(): Flow<List<UploadProvider>>

    @Query("SELECT * FROM upload_providers ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllProvidersSync(): List<UploadProvider>

    @Query("SELECT * FROM upload_providers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProvider(): UploadProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: UploadProvider): Long

    @Update
    suspend fun update(provider: UploadProvider)

    @Delete
    suspend fun delete(provider: UploadProvider)

    @Transaction
    suspend fun setDefaultProvider(providerId: Long) {
        // Clear old default(s)
        clearDefaultProviders()
        // Set new default
        setProviderAsDefault(providerId)
    }

    @Query("UPDATE upload_providers SET isDefault = 0")
    suspend fun clearDefaultProviders()

    @Query("UPDATE upload_providers SET isDefault = 1 WHERE id = :providerId")
    suspend fun setProviderAsDefault(providerId: Long)
}
