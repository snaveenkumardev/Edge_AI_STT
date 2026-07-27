package com.edgeai.stt.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.sqrt

enum class AcousticStressState {
    NORMAL,
    ELEVATED,
    PANIC_SCREAM
}

class AudioStressAnalyzer {
    private val _dbLevel = MutableStateFlow(0f)
    val dbLevel: StateFlow<Float> = _dbLevel.asStateFlow()

    private val _stressState = MutableStateFlow(AcousticStressState.NORMAL)
    val stressState: StateFlow<AcousticStressState> = _stressState.asStateFlow()

    fun processPcmChunk(pcmData: FloatArray, readSize: Int) {
        if (readSize <= 0) return

        var sumSquare = 0.0
        var maxAmplitude = 0f

        for (i in 0 until readSize) {
            val sample = pcmData[i]
            sumSquare += (sample * sample).toDouble()
            val absSample = Math.abs(sample)
            if (absSample > maxAmplitude) {
                maxAmplitude = absSample
            }
        }

        val rms = sqrt(sumSquare / readSize)
        // Convert normalized Float [-1.0, 1.0] RMS to approximate dB SPL (0 to 90 dB)
        val db = if (rms > 0.0) {
            (20 * log10(rms) + 90.0).toFloat().coerceAtLeast(0f)
        } else {
            0f
        }

        _dbLevel.value = db

        // Acoustic stress classification thresholds
        val newState = when {
            db > 82f && maxAmplitude > 0.9f -> AcousticStressState.PANIC_SCREAM
            db > 68f && maxAmplitude > 0.6f -> AcousticStressState.ELEVATED
            else -> AcousticStressState.NORMAL
        }

        _stressState.value = newState
    }

    fun reset() {
        _dbLevel.value = 0f
        _stressState.value = AcousticStressState.NORMAL
    }
}
