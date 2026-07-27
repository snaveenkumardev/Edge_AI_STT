package com.edgeai.stt.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AudioStreamRecorder(private val context: Context) {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _audioBufferFlow = MutableSharedFlow<FloatArray>(extraBufferCapacity = 64)
    val audioBufferFlow: SharedFlow<FloatArray> = _audioBufferFlow.asSharedFlow()

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (!hasPermission() || recordingJob?.isActive == true) return

        val bufferSize = Math.max(minBufferSize, 3200) // 100ms at 16kHz
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()

        recordingJob = scope.launch {
            val shortBuffer = ShortArray(1600) // 100ms chunks
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readShorts = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                if (readShorts > 0) {
                    val floatArray = FloatArray(readShorts)
                    for (i in 0 until readShorts) {
                        floatArray[i] = shortBuffer[i] / 32768.0f // Normalize to [-1.0, 1.0]
                    }
                    _audioBufferFlow.emit(floatArray)
                }
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }
}
