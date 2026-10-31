package com.callcapture.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.callcapture.app.data.model.CallDirection
import com.callcapture.app.data.model.CallType

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val contactName: String?,
    val phoneNumber: String?,
    val callType: CallType,
    val direction: CallDirection,
    val filePath: String,
    val timestamp: Long,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val audioFormat: String,
    val isStarred: Boolean = false,
    val notes: String = ""
)
