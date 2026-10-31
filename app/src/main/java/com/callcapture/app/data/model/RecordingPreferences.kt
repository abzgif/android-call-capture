package com.callcapture.app.data.model

import android.media.MediaRecorder

enum class AudioSourceOption(val title: String, val sourceValue: Int) {
    VOICE_RECOGNITION("Voice Recognition (Recommended)", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    VOICE_COMMUNICATION("Voice Communication (Hardware AEC)", MediaRecorder.AudioSource.VOICE_COMMUNICATION),
    MIC("Standard Microphone", MediaRecorder.AudioSource.MIC),
    CAMCORDER("Camcorder Microphone", MediaRecorder.AudioSource.CAMCORDER),
    VOICE_CALL("Direct Modem Audio (Two-Way)", MediaRecorder.AudioSource.VOICE_CALL),
    VOICE_DOWNLINK("Direct Downlink (Remote Caller)", MediaRecorder.AudioSource.VOICE_DOWNLINK),
    UNPROCESSED("Unprocessed Raw Mic", 9), // MediaRecorder.AudioSource.UNPROCESSED
    DEFAULT("System Default", MediaRecorder.AudioSource.DEFAULT)
}

enum class AudioFormatOption(val extension: String, val mimeType: String) {
    AAC("m4a", "audio/mp4"),
    WAV("wav", "audio/wav")
}
