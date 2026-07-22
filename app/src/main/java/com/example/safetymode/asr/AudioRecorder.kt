package com.example.safetymode.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

/**
 * Records mono 16 kHz PCM from the microphone — the exact format Whisper expects.
 *
 * For live streaming the caller polls [drain] periodically to pull the audio captured
 * since the previous drain (as normalized float samples in [-1, 1]) without stopping
 * capture. [stop] tears the recorder down; any audio not yet drained stays available
 * for one final [drain].
 *
 * Caller is responsible for holding the RECORD_AUDIO permission before [start].
 */
class AudioRecorder {

    private var record: AudioRecord? = null
    @Volatile private var recording = false
    private var thread: Thread? = null
    private val buffer = ByteArrayOutputStream()
    private val lock = Any()

    val isRecording: Boolean get() = recording

    @SuppressLint("MissingPermission")
    fun start() {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = maxOf(minBuf, SAMPLE_RATE * 2) // ~1 s of headroom
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }
        synchronized(lock) { buffer.reset() }
        record = recorder
        recorder.startRecording()
        recording = true
        thread = Thread {
            val chunk = ByteArray(bufSize)
            while (recording) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) synchronized(lock) { buffer.write(chunk, 0, read) }
            }
        }.also { it.start() }
    }

    /**
     * Pull all audio captured since the previous call as normalized float PCM,
     * clearing the internal buffer. Safe to call from any thread while recording.
     */
    fun drain(): FloatArray = synchronized(lock) {
        val bytes = buffer.toByteArray()
        buffer.reset()
        toFloatPcm(bytes)
    }

    /**
     * Signal the capture loop to stop without blocking. The loop exits on its next
     * read; call [stop] afterwards to join the thread and release the recorder.
     */
    fun signalStop() {
        recording = false
    }

    /**
     * Stop capture, release resources, and return any audio not yet consumed as
     * normalized float PCM. Idempotent.
     *
     * - Record flow (no [drain] calls): returns the entire recording.
     * - Streaming flow: returns only the tail captured since the last [drain].
     */
    fun stop(): FloatArray {
        recording = false
        thread?.join()
        thread = null
        record?.apply {
            runCatching { stop() }
            release()
        }
        record = null
        return drain()
    }

    /** Convert little-endian 16-bit PCM bytes to floats in [-1, 1]. */
    private fun toFloatPcm(bytes: ByteArray): FloatArray {
        val samples = FloatArray(bytes.size / 2)
        var j = 0
        for (i in samples.indices) {
            val lo = bytes[j].toInt() and 0xFF
            val hi = bytes[j + 1].toInt() // sign-extended
            val sample = (hi shl 8) or lo
            samples[i] = sample / 32768f
            j += 2
        }
        return samples
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
