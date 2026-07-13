package com.uploid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_providers")
data class UploadProvider(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetUrl: String,
    val fileParam: String,
    val authUser: String? = null,
    val authPass: String? = null,
    val responseRegex: String? = null,
    val isDefault: Boolean = false
)
