package com.edgeai.stt.ai

import android.content.Context
import android.os.SystemClock
import com.edgeai.stt.vitals.VitalsMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle

class SherpaOnnxSTTEngine(
    private val context: Context,
    private val vitalsMonitor: VitalsMonitor
) {
    private val _useSystemSpeech = MutableStateFlow(false)
    val useSystemSpeech: StateFlow<Boolean> = _useSystemSpeech.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    fun setUseSystemSpeech(value: Boolean) {
        _useSystemSpeech.value = value
    }

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private var transcriptionJob: Job? = null

    private var whisperModel: WhisperModel? = null

    // Holds incoming raw samples pushed from mic recorder in processAudioChunk
    private val incomingSamples = Collections.synchronizedList(mutableListOf<Float>())

    // committed text of past rolling window segments
    private val committedText = java.lang.StringBuilder()
    // Current rolling window of samples
    private var currentWindow = FloatArray(0)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("WhisperEngine", "Initializing WhisperModel...")
            whisperModel = WhisperModel.load(context)
            _isModelLoaded.value = true
            vitalsMonitor.updateModelHardwareMode("CPU (ExecuTorch)")
            android.util.Log.d("WhisperEngine", "Whisper ExecuTorch STT initialized successfully.")
        } catch (e: Exception) {
            _isModelLoaded.value = false
            vitalsMonitor.updateModelHardwareMode("Error (None)")
            android.util.Log.e("WhisperEngine", "Initialization error: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private suspend fun initSpeechRecognizer() = withContext(Dispatchers.Main) {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        android.util.Log.d("SpeechRecognizer", "onReadyForSpeech")
                    }
                    override fun onBeginningOfSpeech() {
                        android.util.Log.d("SpeechRecognizer", "onBeginningOfSpeech")
                        _isTranscribing.value = true
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        android.util.Log.d("SpeechRecognizer", "onEndOfSpeech")
                        _isTranscribing.value = false
                    }
                    override fun onError(error: Int) {
                        val errMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No recognition match"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input timeout"
                            else -> "Unknown error"
                        }
                        android.util.Log.w("SpeechRecognizer", "Error: $error ($errMsg)")
                        _isTranscribing.value = false
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _liveTranscript.value = matches[0]
                            android.util.Log.d("SpeechRecognizer", "Results: ${matches[0]}")
                        }
                        _isTranscribing.value = false
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _liveTranscript.value = matches[0]
                            android.util.Log.d("SpeechRecognizer", "Partial results: ${matches[0]}")
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startListening() {
        incomingSamples.clear()
        committedText.setLength(0)
        currentWindow = FloatArray(0)
        _liveTranscript.value = ""

        // Cancel previous streaming jobs
        transcriptionJob?.cancel()

        if (_useSystemSpeech.value || !_isModelLoaded.value) {
            // Fallback to Android SpeechRecognizer
            coroutineScope.launch {
                initSpeechRecognizer()
                withContext(Dispatchers.Main) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    speechRecognizer?.startListening(intent)
                }
            }
        } else {
            // Start the rolling window streaming loop
            transcriptionJob = coroutineScope.launch {
                runStreamingLoop()
            }
        }
    }

    private suspend fun runStreamingLoop() = withContext(Dispatchers.Default) {
        val model = whisperModel ?: return@withContext
        var tick = 0

        while (coroutineScope.coroutineContext[Job]?.isActive == true) {
            delay(STREAM_INTERVAL_MS)
            
            // Drain fresh samples
            val fresh = synchronized(incomingSamples) {
                val array = incomingSamples.toFloatArray()
                incomingSamples.clear()
                array
            }
            
            currentWindow += fresh
            tick++
            
            if (currentWindow.isEmpty()) continue

            _isTranscribing.value = true
            val startTime = SystemClock.elapsedRealtime()

            val partial = runCatching { model.transcribe(currentWindow) }
                .onFailure { android.util.Log.e("WhisperEngine", "Partial transcribe failed", it) }
                .getOrDefault("")

            val elapsed = SystemClock.elapsedRealtime() - startTime
            vitalsMonitor.updateSTTLatency(elapsed)
            _isTranscribing.value = false

            val fullLiveText = liveText(committedText, partial)
            _liveTranscript.value = fullLiveText
            android.util.Log.d("WhisperEngine", "tick #$tick: partial='$partial' live='$fullLiveText'")

            // Roll the window over before hitting Whisper's 30s limit (at 20s)
            if (currentWindow.size >= MAX_WINDOW_SAMPLES) {
                android.util.Log.d("WhisperEngine", "Window rollover. Committing partial: '$partial'")
                if (partial.isNotBlank()) {
                    committedText.append(partial.trim()).append(' ')
                }
                currentWindow = FloatArray(0)
            }
        }
    }

    private fun liveText(committed: java.lang.StringBuilder, partial: String): String {
        return (committed.toString() + partial).trim()
    }

    suspend fun stopListening(): String = withContext(Dispatchers.Default) {
        transcriptionJob?.cancel()
        transcriptionJob = null

        if (_useSystemSpeech.value || !_isModelLoaded.value) {
            // Stop Android SpeechRecognizer on Main Thread
            withContext(Dispatchers.Main) {
                speechRecognizer?.stopListening()
            }
            // Give SpeechRecognizer a small delay to finish and update liveTranscript
            kotlinx.coroutines.delay(400)
        } else {
            // Process the final tail window synchronously in this coroutine
            val model = whisperModel
            if (model != null) {
                // Drain any final samples
                val fresh = synchronized(incomingSamples) {
                    val array = incomingSamples.toFloatArray()
                    incomingSamples.clear()
                    array
                }
                currentWindow += fresh
                if (currentWindow.isNotEmpty()) {
                    val tail = runCatching { model.transcribe(currentWindow) }
                        .onFailure { android.util.Log.e("WhisperEngine", "Final transcribe failed", it) }
                        .getOrDefault("")
                    if (tail.isNotBlank()) {
                        committedText.append(tail.trim())
                    }
                }
                val finalText = committedText.toString().trim()
                _liveTranscript.value = finalText
                android.util.Log.d("WhisperEngine", "Final transcription complete: '$finalText'")
            }
        }
        return@withContext _liveTranscript.value
    }

    suspend fun processAudioChunk(samples: FloatArray) {
        val startTime = SystemClock.elapsedRealtime()
        synchronized(incomingSamples) {
            for (s in samples) {
                incomingSamples.add(s)
            }
        }
        android.util.Log.d("WhisperEngine", "processAudioChunk: added ${samples.size} samples. Total incoming = ${incomingSamples.size}")

        // Update Latency if active speech audio
        var sum = 0f
        for (s in samples) {
            sum += s * s
        }
        val rms = Math.sqrt((sum / samples.size).toDouble()).toFloat()

        if (rms > 0.02f) {
            val sttLatency = SystemClock.elapsedRealtime() - startTime
            vitalsMonitor.updateSTTLatency(sttLatency)
        }
    }

    fun appendTranscript(text: String) {
        if (text.isNotBlank()) {
            val current = _liveTranscript.value
            _liveTranscript.value = if (current.isBlank()) text.trim() else "$current ${text.trim()}"
        }
    }

    fun clearTranscript() {
        _liveTranscript.value = ""
        incomingSamples.clear()
        committedText.setLength(0)
        currentWindow = FloatArray(0)
    }

    companion object {
        private const val STREAM_INTERVAL_MS = 1500L
        private const val SAMPLE_RATE = 16000
        private const val MAX_WINDOW_SAMPLES = SAMPLE_RATE * 20
    }
}
