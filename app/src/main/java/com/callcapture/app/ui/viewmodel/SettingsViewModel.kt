package com.callcapture.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callcapture.app.OmniCallApplication
import com.callcapture.app.data.model.AudioFormatOption
import com.callcapture.app.data.model.AudioSourceOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateCheckResult {
    data object Idle : UpdateCheckResult()
    data object Checking : UpdateCheckResult()
    data class UpdateAvailable(val latestVersion: String, val releaseNotes: String, val releaseUrl: String) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

data class SettingsState(
    val isCellularEnabled: Boolean,
    val isWhatsAppEnabled: Boolean,
    val isMessengerEnabled: Boolean,
    val isTelegramEnabled: Boolean,
    val audioSource: AudioSourceOption,
    val audioFormat: AudioFormatOption,
    val isSpeakerphoneAssistantEnabled: Boolean,
    val softwareGainMultiplier: Float
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as OmniCallApplication).preferenceManager

    private val _state = MutableStateFlow(
        SettingsState(
            isCellularEnabled = prefs.isCellularRecordEnabled,
            isWhatsAppEnabled = prefs.isWhatsAppRecordEnabled,
            isMessengerEnabled = prefs.isMessengerRecordEnabled,
            isTelegramEnabled = prefs.isTelegramRecordEnabled,
            audioSource = prefs.audioSourceOption,
            audioFormat = prefs.audioFormatOption,
            isSpeakerphoneAssistantEnabled = prefs.isSpeakerphoneAssistantEnabled,
            softwareGainMultiplier = prefs.softwareGainMultiplier
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun setCellularEnabled(enabled: Boolean) {
        prefs.isCellularRecordEnabled = enabled
        _state.value = _state.value.copy(isCellularEnabled = enabled)
    }

    fun setWhatsAppEnabled(enabled: Boolean) {
        prefs.isWhatsAppRecordEnabled = enabled
        _state.value = _state.value.copy(isWhatsAppEnabled = enabled)
    }

    fun setMessengerEnabled(enabled: Boolean) {
        prefs.isMessengerRecordEnabled = enabled
        _state.value = _state.value.copy(isMessengerEnabled = enabled)
    }

    fun setTelegramEnabled(enabled: Boolean) {
        prefs.isTelegramRecordEnabled = enabled
        _state.value = _state.value.copy(isTelegramEnabled = enabled)
    }

    fun setAudioSource(source: AudioSourceOption) {
        prefs.audioSourceOption = source
        _state.value = _state.value.copy(audioSource = source)
    }

    fun setAudioFormat(format: AudioFormatOption) {
        prefs.audioFormatOption = format
        _state.value = _state.value.copy(audioFormat = format)
    }

    fun setSpeakerphoneAssistantEnabled(enabled: Boolean) {
        prefs.isSpeakerphoneAssistantEnabled = enabled
        _state.value = _state.value.copy(isSpeakerphoneAssistantEnabled = enabled)
    }

    fun setSoftwareGainMultiplier(gain: Float) {
        prefs.softwareGainMultiplier = gain
        _state.value = _state.value.copy(softwareGainMultiplier = gain)
    }

    private val _updateStatus = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    val updateStatus: StateFlow<UpdateCheckResult> = _updateStatus.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _updateStatus.value = UpdateCheckResult.Checking
            try {
                val currentVersion = com.callcapture.app.BuildConfig.VERSION_NAME

                // Use GitHub web redirect (avoids api.github.com unauthenticated 60 req/hr rate-limiting / 403 errors)
                val url = URL("https://github.com/abzgif/android-call-capture/releases/latest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val code = conn.responseCode
                var latestTag: String? = null
                var releaseUrl = "https://github.com/abzgif/android-call-capture/releases"

                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: ""
                    if (location.isNotBlank()) {
                        releaseUrl = location
                        latestTag = location.substringAfterLast("/").trim()
                    }
                }
                conn.disconnect()

                // Fallback to REST API if web redirect didn't resolve a tag
                if (latestTag.isNullOrBlank()) {
                    try {
                        val apiUrl = URL("https://api.github.com/repos/abzgif/android-call-capture/releases/latest")
                        val apiConn = (apiUrl.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            setRequestProperty("Accept", "application/vnd.github.v3+json")
                            setRequestProperty("User-Agent", "CallCapture-Android")
                            connectTimeout = 5000
                            readTimeout = 5000
                        }
                        if (apiConn.responseCode == 200) {
                            val jsonStr = apiConn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(jsonStr)
                            latestTag = json.optString("tag_name", "").trim()
                            releaseUrl = json.optString("html_url", releaseUrl)
                        }
                        apiConn.disconnect()
                    } catch (_: Exception) {
                        // ignore API fallback error
                    }
                }

                if (!latestTag.isNullOrBlank()) {
                    val cleanLatest = latestTag.removePrefix("v").trim()
                    if (isNewerVersion(cleanLatest, currentVersion)) {
                        _updateStatus.value = UpdateCheckResult.UpdateAvailable(
                            latestVersion = latestTag,
                            releaseNotes = "A new version of Call Capture ($latestTag) is available on GitHub with bug fixes and improvements.",
                            releaseUrl = releaseUrl
                        )
                    } else {
                        _updateStatus.value = UpdateCheckResult.UpToDate(currentVersion)
                    }
                } else {
                    _updateStatus.value = UpdateCheckResult.UpToDate(currentVersion)
                }
            } catch (e: Exception) {
                _updateStatus.value = UpdateCheckResult.Error(e.localizedMessage ?: "Failed to connect to GitHub")
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateStatus.value = UpdateCheckResult.Idle
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
