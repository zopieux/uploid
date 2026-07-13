package com.uploid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_entries")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val thumbnailBytes: ByteArray, // Small JPEG thumbnail
    val link: String,
    val timestamp: Long // Unix timestamp in milliseconds
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HistoryEntry
        if (id != other.id) return false
        if (!thumbnailBytes.contentEquals(other.thumbnailBytes)) return false
        if (link != other.link) return false
        if (timestamp != other.timestamp) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + thumbnailBytes.contentHashCode()
        result = 31 * result + link.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
