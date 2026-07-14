package com.uploid.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [UploadProvider::class, HistoryEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadProviderDao(): UploadProviderDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "uploid_database"
                )
                    .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Prepopulate default providers on a background scope
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getDatabase(context)
                            database.uploadProviderDao().insert(
                                UploadProvider(
                                    name = "x0.at",
                                    targetUrl = "https://x0.at/",
                                    fileParam = "file",
                                    isDefault = true
                                )
                            )
                            database.uploadProviderDao().insert(
                                UploadProvider(
                                    name = "0x0.st",
                                    targetUrl = "https://0x0.st/",
                                    fileParam = "file",
                                    isDefault = false
                                )
                            )
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
