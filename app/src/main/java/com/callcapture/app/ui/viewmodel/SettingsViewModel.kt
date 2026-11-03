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
                val url = URL("https://api.github.com/repos/abzgif/android-call-capture/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "CallCapture-Android")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val code = conn.responseCode
                if (code == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonStr)
                    val tagName = json.optString("tag_name", "").trim()
                    val htmlUrl = json.optString("html_url", "https://github.com/abzgif/android-call-capture/releases")
                    val body = json.optString("body", "No release notes provided.")
                    val cleanLatest = tagName.removePrefix("v").trim()
                    val currentVersion = com.callcapture.app.BuildConfig.VERSION_NAME

                    if (isNewerVersion(cleanLatest, currentVersion)) {
                        _updateStatus.value = UpdateCheckResult.UpdateAvailable(
                            latestVersion = tagName,
                            releaseNotes = body,
                            releaseUrl = htmlUrl
                        )
                    } else {
                        _updateStatus.value = UpdateCheckResult.UpToDate(currentVersion)
                    }
                } else if (code == 404) {
                    _updateStatus.value = UpdateCheckResult.UpToDate(com.callcapture.app.BuildConfig.VERSION_NAME)
                } else {
                    _updateStatus.value = UpdateCheckResult.Error("Server returned code $code")
                }
                conn.disconnect()
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
