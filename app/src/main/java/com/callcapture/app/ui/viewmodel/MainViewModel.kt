package com.callcapture.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callcapture.app.OmniCallApplication
import com.callcapture.app.data.db.RecordingEntity
import com.callcapture.app.data.model.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class FilterTab(val title: String) {
    ALL("All"),
    CELLULAR("Phone"),
    WHATSAPP("WhatsApp"),
    MESSENGER("Messenger"),
    TELEGRAM("Telegram"),
    STARRED("Starred")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as OmniCallApplication).database
    private val dao = db.recordingDao()

    private val _selectedTab = MutableStateFlow(FilterTab.ALL)
    val selectedTab: StateFlow<FilterTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val recordings: StateFlow<List<RecordingEntity>> = combine(_selectedTab, _searchQuery) { tab, query ->
        Pair(tab, query)
    }.flatMapLatest { (tab, query) ->
        if (query.isNotBlank()) {
            dao.searchRecordings(query)
        } else {
            when (tab) {
                FilterTab.ALL -> dao.getAllRecordings()
                FilterTab.CELLULAR -> dao.getRecordingsByType(CallType.CELLULAR)
                FilterTab.WHATSAPP -> dao.getRecordingsByType(CallType.WHATSAPP)
                FilterTab.MESSENGER -> dao.getRecordingsByType(CallType.MESSENGER)
                FilterTab.TELEGRAM -> dao.getRecordingsByType(CallType.TELEGRAM)
                FilterTab.STARRED -> dao.getStarredRecordings()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Player state
    private var mediaPlayer: MediaPlayer? = null
    private val _currentPlayingRecording = MutableStateFlow<RecordingEntity?>(null)
    val currentPlayingRecording: StateFlow<RecordingEntity?> = _currentPlayingRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    fun selectTab(tab: FilterTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun playRecording(recording: RecordingEntity) {
        if (_currentPlayingRecording.value?.id == recording.id) {
            if (_isPlaying.value) {
                pausePlayback()
            } else {
                resumePlayback()
            }
            return
        }

        stopPlayback()

        val file = File(recording.filePath)
        if (!file.exists()) {
            return
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(recording.filePath)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L
                    stopProgressTracking()
                }
            }
            mediaPlayer = player
            _currentPlayingRecording.value = recording
            applyPlaybackSpeed(_playbackSpeed.value)
            player.start()
            _isPlaying.value = true
            startProgressTracking()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopProgressTracking()
            }
        }
    }

    fun resumePlayback() {
        mediaPlayer?.let {
            it.start()
            _isPlaying.value = true
            startProgressTracking()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let {
            it.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
        }
    }

    fun replay5s() {
        mediaPlayer?.let {
            val newPos = maxOf(0, it.currentPosition - 5000)
            it.seekTo(newPos)
            _currentPositionMs.value = newPos.toLong()
        }
    }

    fun togglePlaybackSpeed() {
        val nextSpeed = when (_playbackSpeed.value) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _playbackSpeed.value = nextSpeed
        applyPlaybackSpeed(nextSpeed)
    }

    private fun applyPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let {
                if (it.isPlaying || _isPlaying.value) {
                    val params = it.playbackParams ?: PlaybackParams()
                    params.speed = speed
                    it.playbackParams = params
                }
            }
        }
    }

    fun stopPlayback() {
        stopProgressTracking()
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
                reset()
                release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        _currentPlayingRecording.value = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _currentPositionMs.value = it.currentPosition.toLong()
                        handler.postDelayed(this, 100)
                    }
                }
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressTracking() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    fun toggleStar(recording: RecordingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateStarred(recording.id, !recording.isStarred)
        }
    }

    fun updateNotes(recordingId: Long, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateNotes(recordingId, notes)
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        if (_currentPlayingRecording.value?.id == recording.id) {
            stopPlayback()
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(recording.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
            dao.deleteRecording(recording)
        }
    }

    fun deleteSelectedRecordings(recordingsToDelete: List<RecordingEntity>) {
        val playingId = _currentPlayingRecording.value?.id
        if (playingId != null && recordingsToDelete.any { it.id == playingId }) {
            stopPlayback()
        }
        viewModelScope.launch(Dispatchers.IO) {
            for (rec in recordingsToDelete) {
                try {
                    val file = File(rec.filePath)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            dao.deleteRecordings(recordingsToDelete)
        }
    }

    fun createMultipleShareIntent(recordingsToShare: List<RecordingEntity>): Intent? {
        val app = getApplication<OmniCallApplication>()
        val uris = arrayListOf<android.net.Uri>()

        for (rec in recordingsToShare) {
            val file = File(rec.filePath)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    app,
                    "${app.packageName}.fileprovider",
                    file
                )
                uris.add(uri)
            }
        }

        if (uris.isEmpty()) return null

        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = if (recordingsToShare.first().filePath.endsWith(".wav")) "audio/wav" else "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_SUBJECT, recordingsToShare.first().title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "Call Capture Audio Recordings (${uris.size})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun deleteAllEmptyRecordings() {
        stopPlayback()
        viewModelScope.launch(Dispatchers.IO) {
            val all = recordings.value
            for (rec in all) {
                if (rec.fileSizeBytes < 10000 || rec.durationMs < 4000) {
                    try {
                        val file = File(rec.filePath)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                    dao.deleteRecording(rec)
                }
            }
        }
    }

    fun createShareIntent(recording: RecordingEntity): Intent? {
        val file = File(recording.filePath)
        if (!file.exists()) return null

        val app = getApplication<OmniCallApplication>()
        val uri = FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            file
        )

        val mimeType = if (recording.filePath.endsWith(".wav")) "audio/wav" else "audio/mp4"

        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, recording.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
