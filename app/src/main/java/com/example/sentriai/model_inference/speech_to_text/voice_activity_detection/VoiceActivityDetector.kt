package com.example.sentriai.model_inference.speech_to_text.voice_activity_detection

import com.example.sentriai.model_inference.speech_to_text.AudioRecorder
import kotlin.math.sqrt

/**
 * Per-frame speech/not-speech decision, energy based.
 *
 * Deliberately the cheap tier of a two-tier design: this runs on every 30 ms frame for the
 * whole session, so it has to cost almost nothing. It rejects true silence, which is where
 * nearly all of the saving is — an always-on guardian session is mostly silence, and every
 * avoided `WhisperModel.transcribe` call is a full 30 s encoder pass avoided (the model pads
 * every input to 30 s, so a 0.4 s clip costs exactly as much as a 15 s one).
 *
 * What it cannot do is tell speech from other loud sounds — a door slam, a TV, traffic. That
 * is what a neural second stage (Silero) would add, gated behind this one so the expensive
 * model only runs on frames that already cleared the energy floor.
 *
 * Thresholds are biased toward false positives on purpose. In a personal-safety app the costs
 * are wildly asymmetric: a false positive wastes one encoder pass, a false negative is a
 * missed distress phrase.
 */
class VoiceActivityDetector(
    /**
     * How far above the measured background level a frame must sit to count as speech, as a
     * linear RMS ratio. Lower is more sensitive. 2.5x is roughly +8 dB.
     */
    private val sensitivity: Float = DEFAULT_SENSITIVITY,
) {

    /** Running background-level estimate; negative until the first frame calibrates it. */
    private var noiseFloor = -1f

    /**
     * @param frame exactly [FRAME_SAMPLES] normalized samples in [-1, 1].
     * @return true if the frame looks like speech.
     */
    fun isSpeech(frame: FloatArray): Boolean {
        val rms = rms(frame)

        if (noiseFloor < 0f) {
            // First frame: seed the estimate rather than calling the whole frame speech just
            // because there is nothing to compare it against yet.
            noiseFloor = rms
            return false
        }

        // ABSOLUTE_FLOOR keeps a near-digital-silence input (noiseFloor ≈ 0, as happens when
        // the mic is muted or the OS is feeding zeros) from making every faint sample look
        // like speech by comparison.
        val threshold = maxOf(ABSOLUTE_FLOOR, noiseFloor * sensitivity)
        val speech = rms > threshold

        // Track the background only while it *is* background. Adapting during speech would
        // pull the floor up toward the speaker's own level and progressively go deaf to them.
        if (!speech) {
            noiseFloor = NOISE_ADAPT * noiseFloor + (1f - NOISE_ADAPT) * rms
        }
        return speech
    }

    /** Forget the background estimate — call between sessions, not between utterances. */
    fun reset() {
        noiseFloor = -1f
    }

    private fun rms(frame: FloatArray): Float {
        var sum = 0.0
        for (s in frame) sum += (s * s).toDouble()
        return sqrt(sum / frame.size).toFloat()
    }

    companion object {
        /** 30 ms at 16 kHz — the conventional VAD frame, and short enough to stay responsive. */
        const val FRAME_SAMPLES = AudioRecorder.SAMPLE_RATE * 30 / 1000

        const val DEFAULT_SENSITIVITY = 2.5f

        /** Below this RMS nothing counts as speech regardless of the noise floor. */
        private const val ABSOLUTE_FLOOR = 0.006f

        /** Per-frame smoothing for the background estimate; ~1 s to settle at 30 ms frames. */
        private const val NOISE_ADAPT = 0.97f
    }
}
