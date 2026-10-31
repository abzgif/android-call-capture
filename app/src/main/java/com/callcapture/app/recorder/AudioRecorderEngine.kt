package com.callcapture.app.recorder

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.callcapture.app.data.model.AudioFormatOption
import com.callcapture.app.data.model.AudioSourceOption
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

data class RecordingResult(
    val file: File,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

interface AudioRecorderEngine {
    fun start(outputFile: File, source: AudioSourceOption)
    fun stop(): RecordingResult
    fun getMaxAmplitude(): Int
    fun isRecording(): Boolean
}

class MediaRecorderEngine(private val context: Context) : AudioRecorderEngine {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0
    private var recording = false

    override fun start(outputFile: File, source: AudioSourceOption) {
        currentFile = outputFile
        startTimeMs = System.currentTimeMillis()

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.apply {
                setAudioSource(source.sourceValue)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            recording = true
            Log.d("MediaRecorderEngine", "MediaRecorder started with source ${source.title}: ${outputFile.name}")
        } catch (e: Exception) {
            Log.w("MediaRecorderEngine", "Failed with ${source.title}, attempting fallback to VOICE_RECOGNITION", e)
            try {
                recorder.reset()
                recorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(64000)
                    setAudioSamplingRate(16000)
                    setAudioChannels(1)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                    start()
                }
                mediaRecorder = recorder
                recording = true
                Log.d("MediaRecorderEngine", "MediaRecorder started with fallback VOICE_RECOGNITION")
            } catch (fallbackError: Exception) {
                Log.e("MediaRecorderEngine", "Fallback to VOICE_RECOGNITION also failed", fallbackError)
                recording = false
                recorder.release()
                throw fallbackError
            }
        }
    }

    override fun stop(): RecordingResult {
        recording = false
        val durationMs = System.currentTimeMillis() - startTimeMs
        val file = currentFile ?: File("")

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e("MediaRecorderEngine", "Error stopping MediaRecorder", e)
        } finally {
            mediaRecorder = null
        }

        val size = if (file.exists()) file.length() else 0L
        return RecordingResult(
            file = file,
            durationMs = durationMs,
            fileSizeBytes = size,
            success = size > 0
        )
    }

    override fun getMaxAmplitude(): Int {
        return try {
            if (recording) mediaRecorder?.maxAmplitude ?: 0 else 0
        } catch (_: Exception) {
            0
        }
    }

    override fun isRecording(): Boolean = recording
}

class PcmWavRecordingEngine(
    private val gainMultiplier: Float = 3.5f
) : AudioRecorderEngine {

    private var audioRecord: AudioRecord? = null
    private var currentFile: File? = null
    private var rawPcmFile: File? = null
    private var startTimeMs: Long = 0
    private val isRecordingActive = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var currentMaxAmplitude: Int = 0

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    override fun start(outputFile: File, source: AudioSourceOption) {
        currentFile = outputFile
        rawPcmFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.raw")
        startTimeMs = System.currentTimeMillis()

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize * 2, 4096)

        var record: AudioRecord? = null
        val sourcesToTry = listOf(
            source.sourceValue,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        ).distinct()

        for (src in sourcesToTry) {
            try {
                val candidate = AudioRecord(
                    src,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    record = candidate
                    Log.d("PcmWavEngine", "AudioRecord initialized successfully with audio source: $src")
                    break
                } else {
                    candidate.release()
                }
            } catch (e: Exception) {
                Log.w("PcmWavEngine", "AudioSource $src failed to initialize: ${e.message}")
            }
        }

        if (record == null) {
            isRecordingActive.set(false)
            throw IllegalStateException("Failed to initialize AudioRecord with any available audio source")
        }

        try {
            audioRecord = record
            record.startRecording()
            isRecordingActive.set(true)

            recordThread = Thread {
                val buffer = ShortArray(bufferSize / 2)
                val fos = FileOutputStream(rawPcmFile)

                try {
                    while (isRecordingActive.get()) {
                        val shortsRead = record.read(buffer, 0, buffer.size)
                        if (shortsRead > 0) {
                            var maxAmp = 0
                            val byteBuffer = ByteBuffer.allocate(shortsRead * 2).order(ByteOrder.LITTLE_ENDIAN)

                            for (i in 0 until shortsRead) {
                                val input = buffer[i].toFloat()
                                val absInput = kotlin.math.abs(input)

                                // Dynamic Voice Leveler & Soft Limiter:
                                // Remote/downlink voice is typically quieter (< 3000 amplitude).
                                // We boost faint signals aggressively (up to 5.5x) so caller voice is crystal clear,
                                // while compressing loud nearby voice to avoid clipping and harsh distortion.
                                val dynamicGain = if (absInput < 2500f) {
                                    gainMultiplier * 1.6f
                                } else if (absInput < 10000f) {
                                    val factor = 1.0f + 0.6f * (1.0f - (absInput - 2500f) / 7500f)
                                    gainMultiplier * factor
                                } else {
                                    // Loud local speech: keep at unity or smooth compression
                                    maxOf(1.0f, gainMultiplier * 0.7f)
                                }

                                var amplified = (input * dynamicGain).toInt()
                                if (amplified > 32000) amplified = 32000
                                else if (amplified < -32000) amplified = -32000

                                val sample = amplified.toShort()
                                byteBuffer.putShort(sample)

                                val absSample = kotlin.math.abs(sample.toInt())
                                if (absSample > maxAmp) maxAmp = absSample
                            }

                            currentMaxAmplitude = maxAmp
                            fos.write(byteBuffer.array())
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PcmWavEngine", "Exception during PCM recording loop", e)
                } finally {
                    try {
                        fos.flush()
                        fos.close()
                    } catch (_: Exception) {}
                }
            }.apply {
                name = "OmniCall-AudioRecord-Thread"
                start()
            }

        } catch (e: Exception) {
            isRecordingActive.set(false)
            Log.e("PcmWavEngine", "Failed to start AudioRecord", e)
            throw e
        }
    }

    override fun stop(): RecordingResult {
        isRecordingActive.set(false)
        val durationMs = System.currentTimeMillis() - startTimeMs

        try {
            recordThread?.join(1000)
        } catch (_: Exception) {}

        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("PcmWavEngine", "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }

        // Convert raw PCM to proper WAV with 44-byte RIFF header
        val finalWavFile = currentFile ?: File("")
        val pcm = rawPcmFile
        if (pcm != null && pcm.exists()) {
            try {
                writeWavFile(pcm, finalWavFile, sampleRate, 1, 16)
                pcm.delete()
            } catch (e: Exception) {
                Log.e("PcmWavEngine", "Error converting PCM to WAV", e)
            }
        }

        val size = if (finalWavFile.exists()) finalWavFile.length() else 0L
        return RecordingResult(
            file = finalWavFile,
            durationMs = durationMs,
            fileSizeBytes = size,
            success = size > 44
        )
    }

    override fun getMaxAmplitude(): Int = currentMaxAmplitude

    override fun isRecording(): Boolean = isRecordingActive.get()

    private fun writeWavFile(pcmFile: File, wavFile: File, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val pcmSize = pcmFile.length()
        val totalDataLen = pcmSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val fis = FileInputStream(pcmFile)
        val fos = FileOutputStream(wavFile)

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM format
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmSize and 0xff).toByte()
        header[41] = (pcmSize shr 8 and 0xff).toByte()
        header[42] = (pcmSize shr 16 and 0xff).toByte()
        header[43] = (pcmSize shr 24 and 0xff).toByte()

        fos.write(header, 0, 44)

        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            fos.write(buffer, 0, bytesRead)
        }

        fis.close()
        fos.flush()
        fos.close()
    }
}
