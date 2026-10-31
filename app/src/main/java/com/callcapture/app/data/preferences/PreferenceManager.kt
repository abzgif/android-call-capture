package com.callcapture.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.callcapture.app.data.model.AudioFormatOption
import com.callcapture.app.data.model.AudioSourceOption

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isCellularRecordEnabled: Boolean
        get() = prefs.getBoolean(KEY_CELLULAR_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CELLULAR_ENABLED, value).apply()

    var isWhatsAppRecordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WHATSAPP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WHATSAPP_ENABLED, value).apply()

    var isMessengerRecordEnabled: Boolean
        get() = prefs.getBoolean(KEY_MESSENGER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MESSENGER_ENABLED, value).apply()

    var isTelegramRecordEnabled: Boolean
        get() = prefs.getBoolean(KEY_TELEGRAM_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TELEGRAM_ENABLED, value).apply()

    var audioSourceOption: AudioSourceOption
        get() {
            val name = prefs.getString(KEY_AUDIO_SOURCE, AudioSourceOption.VOICE_RECOGNITION.name)
            return try {
                AudioSourceOption.valueOf(name ?: AudioSourceOption.VOICE_RECOGNITION.name)
            } catch (_: Exception) {
                AudioSourceOption.VOICE_RECOGNITION
            }
        }
        set(value) = prefs.edit().putString(KEY_AUDIO_SOURCE, value.name).apply()

    var audioFormatOption: AudioFormatOption
        get() {
            val name = prefs.getString(KEY_AUDIO_FORMAT, AudioFormatOption.WAV.name)
            return try {
                AudioFormatOption.valueOf(name ?: AudioFormatOption.WAV.name)
            } catch (_: Exception) {
                AudioFormatOption.WAV
            }
        }
        set(value) = prefs.edit().putString(KEY_AUDIO_FORMAT, value.name).apply()

    var isSpeakerphoneAssistantEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPEAKERPHONE_ASSISTANT, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEAKERPHONE_ASSISTANT, value).apply()

    var softwareGainMultiplier: Float
        get() = prefs.getFloat(KEY_SOFTWARE_GAIN, 3.5f)
        set(value) = prefs.edit().putFloat(KEY_SOFTWARE_GAIN, value).apply()

    companion object {
        private const val PREFS_NAME = "omnicall_preferences"
        private const val KEY_CELLULAR_ENABLED = "pref_cellular_enabled"
        private const val KEY_WHATSAPP_ENABLED = "pref_whatsapp_enabled"
        private const val KEY_MESSENGER_ENABLED = "pref_messenger_enabled"
        private const val KEY_TELEGRAM_ENABLED = "pref_telegram_enabled"
        private const val KEY_AUDIO_SOURCE = "pref_audio_source"
        private const val KEY_AUDIO_FORMAT = "pref_audio_format"
        private const val KEY_SPEAKERPHONE_ASSISTANT = "pref_speakerphone_assistant"
        private const val KEY_SOFTWARE_GAIN = "pref_software_gain"
    }
}
