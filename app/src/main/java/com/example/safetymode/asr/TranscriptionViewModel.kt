package com.example.safetymode.asr

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** UI-facing state for the transcription screen. */
sealed interface TranscriptionUiState {
    data object Idle : TranscriptionUiState
    data object Recording : TranscriptionUiState
    data object Transcribing : TranscriptionUiState

    /** Live streaming: mic is on and [text] grows as speech is transcribed. */
    data class Listening(val text: String) : TranscriptionUiState
    data class Result(val text: String) : TranscriptionUiState
    data class Error(val message: String) : TranscriptionUiState
}

class TranscriptionViewModel(app: Application) : AndroidViewModel(app) {

    private val recorder = AudioRecorder()
    @Volatile private var model: WhisperModel? = null
    private var streamJob: Job? = null

    private val _state = MutableStateFlow<TranscriptionUiState>(TranscriptionUiState.Idle)
    val state: StateFlow<TranscriptionUiState> = _state.asStateFlow()

    /** Called once permission is granted. Begins mic capture. */
    fun startRecording() {
        if (recorder.isRecording) return
        runCatching { recorder.start() }
            .onSuccess { _state.value = TranscriptionUiState.Recording }
            .onFailure { _state.value = TranscriptionUiState.Error("Mic error: ${it.message}") }
    }

    /** Stop capture and run transcription off the main thread. */
    fun stopAndTranscribe() {
        if (!recorder.isRecording) return
        _state.value = TranscriptionUiState.Transcribing
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    // recorder.stop() joins the capture thread and tears down AudioRecord,
                    // both of which can block — keep them off the main thread.
                    val pcm = recorder.stop()
                    val m = model ?: WhisperModel.load(getApplication()).also { model = it }
                    m.transcribe(pcm)
                }
            }
            _state.value = result.fold(
                onSuccess = { TranscriptionUiState.Result(it.ifBlank { "(no speech detected)" }) },
                onFailure = {
                    Log.e("TranscriptionVM", "transcribe failed", it)
                    TranscriptionUiState.Error(it.message ?: "Transcription failed")
                },
            )
        }
    }

    // --- Live streaming -------------------------------------------------------
    //
    // Whisper is not a token-streaming model — it transcribes fixed 30 s windows. To
    // fake a live feed we re-transcribe the audio accumulated so far every
    // STREAM_INTERVAL_MS; once a window nears the 30 s limit its text is committed as a
    // finalized prefix and a fresh window begins. Displayed text is `committed + partial`.
    // These methods are independent of the record-then-transcribe flow above.

    /** Called once permission is granted. Starts mic + the live streaming loop. */
    fun startStreaming() {
        Log.d(TAG, "startStreaming() called, isRecording=${recorder.isRecording}")
        if (recorder.isRecording) return
        runCatching { recorder.start() }.onFailure {
            Log.e(TAG, "mic start failed", it)
            _state.value = TranscriptionUiState.Error("Mic error: ${it.message}")
            return
        }
        Log.d(TAG, "mic started, launching stream loop")
        _state.value = TranscriptionUiState.Listening("")
        streamJob = viewModelScope.launch(Dispatchers.Default) { streamLoop() }
    }

    /** Ask the loop to wind down; it emits the final transcript when it finishes. */
    fun stopStreaming() {
        Log.d(TAG, "stopStreaming() called")
        recorder.signalStop()
    }

    private suspend fun streamLoop() {
        Log.d(TAG, "streamLoop: loading model…")
        val m = runCatching {
            model ?: WhisperModel.load(getApplication()).also { model = it }
        }.getOrElse {
            Log.e(TAG, "model load failed", it)
            recorder.stop()
            _state.value = TranscriptionUiState.Error(it.message ?: "Model load failed")
            return
        }
        Log.d(TAG, "streamLoop: model ready, entering loop")

        val committed = StringBuilder()
        var window = FloatArray(0)
        var tick = 0

        try {
            while (coroutineContext.isActive && recorder.isRecording) {
                delay(STREAM_INTERVAL_MS)
                val fresh = recorder.drain()
                window += fresh
                tick++
                Log.d(
                    TAG,
                    "tick #$tick: fresh=${fresh.size} window=${window.size} " +
                        "(${"%.1f".format(window.size / 16000f)}s) peak=${"%.3f".format(peak(window))}",
                )

                if (window.isEmpty()) {
                    Log.d(TAG, "tick #$tick: no audio yet, skipping")
                    continue
                }

                val partial = runCatching { m.transcribe(window) }
                    .onFailure { Log.e(TAG, "partial transcribe failed", it) }
                    .getOrDefault("")
                Log.d(TAG, "tick #$tick: partial='$partial' (len=${partial.length})")
                _state.value = TranscriptionUiState.Listening(live(committed, partial))

                // Roll the window over before it hits Whisper's 30 s ceiling.
                if (window.size >= MAX_WINDOW_SAMPLES) {
                    Log.d(TAG, "tick #$tick: window rollover, committing '$partial'")
                    if (partial.isNotBlank()) committed.append(partial.trim()).append(' ')
                    window = FloatArray(0)
                }
            }
        } finally {
            // Capture is done — join/release and fold in any un-transcribed tail audio.
            window += recorder.stop()
            Log.d(TAG, "streamLoop finally: transcribing tail window=${window.size}")
            if (window.isNotEmpty()) {
                val tail = runCatching { m.transcribe(window) }
                    .onFailure { Log.e(TAG, "final transcribe failed", it) }
                    .getOrDefault("")
                if (tail.isNotBlank()) committed.append(tail.trim())
            }
            val finalText = committed.toString().trim().ifBlank { "(no speech detected)" }
            Log.d(TAG, "streamLoop finally: final text='$finalText'")
            _state.value = TranscriptionUiState.Result(finalText)
        }
    }

    private fun live(committed: CharSequence, partial: String): String =
        (committed.toString() + partial).trim()

    /** Max absolute sample — a quick "is the mic actually hearing anything?" signal. */
    private fun peak(samples: FloatArray): Float {
        var max = 0f
        for (s in samples) {
            val a = if (s < 0f) -s else s
            if (a > max) max = a
        }
        return max
    }

    fun reset() {
        _state.value = TranscriptionUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        if (recorder.isRecording) recorder.stop()
        model?.close()
        model = null
    }

    companion object {
        private const val TAG = "TranscriptionVM"
        private const val STREAM_INTERVAL_MS = 1_500L
        // Roll the window over at 20 s, comfortably under Whisper's 30 s limit.
        private const val MAX_WINDOW_SAMPLES = AudioRecorder.SAMPLE_RATE * 20
    }
}
