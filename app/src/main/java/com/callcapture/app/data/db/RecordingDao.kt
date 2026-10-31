package com.callcapture.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.callcapture.app.data.model.CallType
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE callType = :callType ORDER BY timestamp DESC")
    fun getRecordingsByType(callType: CallType): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE isStarred = 1 ORDER BY timestamp DESC")
    fun getStarredRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE title LIKE '%' || :query || '%' OR contactName LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchRecordings(query: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    @Delete
    suspend fun deleteRecordings(recordings: List<RecordingEntity>)

    @Query("UPDATE recordings SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarred(id: Long, isStarred: Boolean)

    @Query("UPDATE recordings SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)
}
