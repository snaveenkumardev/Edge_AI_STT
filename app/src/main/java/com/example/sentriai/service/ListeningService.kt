package com.example.sentriai.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.sentriai.R
import com.example.sentriai.engine.EmergencyPipeline
import com.example.sentriai.model_inference.speech_to_text.AnalysisState
import com.example.sentriai.model_inference.speech_to_text.AudioRecorder
import com.example.sentriai.model_inference.speech_to_text.TranscriptionUiState
import com.example.sentriai.model_inference.speech_to_text.WhisperModel
import com.example.sentriai.model_inference.speech_to_text.cleanTranscriptText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Foreground service that owns the whole listening pipeline: mic capture, Whisper
 * transcription and the FunctionGemma emergency check.
 *
 * All three used to live in `TranscriptionViewModel` on `viewModelScope`, which meant
 * listening died the moment the UI went away. Worse, a backgrounded app without a
 * microphone-type foreground service gets *silence* rather than an error from
 * [AudioRecorder] — the loop kept ticking and transcribed zeros. Owning the pipeline in a
 * `microphone`-type FGS with its own scope fixes both.
 *
 * State is published to [ListeningStateHolder] rather than returned, so the UI can attach
 * and detach freely while this keeps running.
 *
 * Must be started while the app is visible: from API 34 a microphone-type FGS cannot be
 * started from the background.
 */
class ListeningService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recorder = AudioRecorder()

    @Volatile private var model: WhisperModel? = null
    private var streamJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stopListening()
            else -> Log.w(TAG, "unknown action ${intent?.action}, ignoring")
        }
        // Deliberately not sticky: a restart by the OS would happen from the background,
        // where a microphone FGS cannot legally start on API 34+. The user reopens the app.
        return START_NOT_STICKY
    }

    private fun start() {
        if (streamJob?.isActive == true) {
            Log.d(TAG, "already listening, ignoring start")
            return
        }
        if (!hasMicPermission()) {
            Log.e(TAG, "start without RECORD_AUDIO")
            fail(getString(R.string.listening_error_no_mic_permission))
            return
        }

        ListeningNotifications.ensureChannel(this)
        // The platform allows ~5 s between onStartCommand and startForeground before it
        // throws, so this happens before any model loading.
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                ListeningNotifications.NOTIFICATION_ID,
                ListeningNotifications.build(this, getString(R.string.listening_notification_starting)),
                // FOREGROUND_SERVICE_TYPE_MICROPHONE only exists from API 30 (the location
                // and dataSync types landed in 29). Passing 0 below that is correct — types
                // are not enforced until 34.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                },
            )
        }.onFailure {
            // API 31+ throws ForegroundServiceStartNotAllowedException if we were started
            // from the background, which is the one case we cannot recover from.
            Log.e(TAG, "startForeground failed", it)
            fail(getString(R.string.listening_error_foreground_denied))
        }.isSuccess
        if (!started) return

        runCatching { recorder.start() }.onFailure {
            Log.e(TAG, "mic start failed", it)
            fail(getString(R.string.listening_error_mic, it.message ?: ""))
            return
        }

        acquireWakeLock()
        ListeningStateHolder.clearChatHistory()
        ListeningStateHolder.setAnalysis(AnalysisState.Idle)
        // Capture is live but the model isn't; the loop flips this to Listening once
        // WhisperModel.load() returns, which is when transcription can actually begin.
        ListeningStateHolder.setState(TranscriptionUiState.Preparing)

        streamJob = scope.launch { streamLoop() }
    }

    /** Wind the loop down; it emits the final transcript and then stops the service. */
    private fun stopListening() {
        Log.d(TAG, "stop requested")
        if (!recorder.isRecording) {
            stopSelfAndCleanUp()
            return
        }
        ListeningStateHolder.setState(TranscriptionUiState.Finishing)
        recorder.signalStop()
    }

    // --- the listening loop ----------------------------------------------------
    //
    // Whisper is not a token-streaming model — it transcribes fixed 30 s windows. To fake
    // a live feed the audio accumulated so far is re-transcribed every STREAM_INTERVAL_MS;
    // the window is closed off as its own chat bubble before it reaches Whisper's 30 s
    // ceiling, and a fresh one begins.

    private suspend fun streamLoop() {
        val whisper = runCatching {
            model ?: WhisperModel.load(this).also { model = it }
        }.getOrElse {
            Log.e(TAG, "model load failed", it)
            recorder.stop()
            fail(it.message ?: getString(R.string.listening_error_model))
            stopSelfAndCleanUp()
            return
        }

        // FunctionGemma is a process-global object that the UI normally warms up. In a
        // background session there may be no UI, so make sure it is ready here too;
        // initialize() is a no-op once loaded.
        runCatching { EmergencyPipeline.initialize(this) }
            .onFailure { Log.e(TAG, "FunctionGemma init failed", it) }

        // Loading can take long enough for a stop to arrive first; don't announce a live
        // stream we are about to wind down.
        if (recorder.isRecording) {
            ListeningStateHolder.setState(TranscriptionUiState.Listening(""))
            updateNotification(getString(R.string.listening_notification_listening))
        }

        var window = FloatArray(0)
        var lastText = ""
        var tick = 0

        try {
            while (coroutineContext.isActive && recorder.isRecording) {
                delay(STREAM_INTERVAL_MS)

                window += recorder.drain()
                tick++
                if (window.isEmpty()) continue

                val text = cleanTranscriptText(
                    runCatching { whisper.transcribe(window) }
                        .onFailure { Log.e(TAG, "partial transcribe failed", it) }
                        .getOrDefault(""),
                )
                Log.d(
                    TAG,
                    "tick #$tick: window=${"%.1f".format(window.size / SAMPLE_RATE_F)}s " +
                        "peak=${"%.3f".format(peak(window))} text='$text'",
                )

                // A stop can land mid-tick (both the delay and the transcribe take time);
                // publishing Listening then would overwrite Finishing and make the UI look
                // live again while the loop is already winding down.
                if (!recorder.isRecording) break

                if (text.isNotBlank()) {
                    lastText = text
                    ListeningStateHolder.setState(TranscriptionUiState.Listening(text))
                    ListeningStateHolder.updateActiveMessage(text)
                    // Note the notification is deliberately *not* updated with the
                    // transcript: it would put the user's speech in the notification shade
                    // for anyone holding the phone to read.

                    if (analyze(text)) {
                        // Emergency fired. Close the bubble and drop the audio so the same
                        // phrase cannot trigger a second alert on the next tick.
                        window = FloatArray(0)
                        lastText = ""
                        continue
                    }
                }

                // Close the window off before it reaches Whisper's 30 s ceiling.
                if (window.size >= MAX_WINDOW_SAMPLES) {
                    Log.d(TAG, "tick #$tick: window rollover")
                    ListeningStateHolder.finalizeActiveMessage(toolInvoked = false)
                    window = FloatArray(0)
                    lastText = ""
                }
            }
        } finally {
            finishUp(window, lastText)
        }
    }

    /**
     * Run the completed text through the emergency pipeline, which decides, sends the SMS
     * and writes the trigger log.
     *
     * @return true if an emergency alert fired.
     */
    private suspend fun analyze(text: String): Boolean {
        ListeningStateHolder.setAnalysis(AnalysisState.Analyzing)
        val triggered = runCatching { EmergencyPipeline.processTranscript(this, text) }
            .onFailure { Log.e(TAG, "emergency pipeline failed", it) }
            .getOrDefault(false)
        if (triggered) {
            ListeningStateHolder.finalizeActiveMessage(toolInvoked = true)
            ListeningStateHolder.setAnalysis(AnalysisState.Emergency(EMERGENCY_LABEL))
        } else {
            ListeningStateHolder.setAnalysis(AnalysisState.Safe)
        }
        return triggered
    }

    /** Transcribe whatever audio never made it into a tick, then publish the final text. */
    private suspend fun finishUp(pending: FloatArray, lastText: String) {
        val whisper = model
        var window = pending
        // recorder.stop() joins the capture thread and releases AudioRecord; it also hands
        // back the tail audio captured since the last drain.
        window += runCatching { recorder.stop() }.getOrDefault(FloatArray(0))

        var finalText = lastText
        if (whisper != null && window.isNotEmpty()) {
            val tail = cleanTranscriptText(
                runCatching { whisper.transcribe(window) }
                    .onFailure { Log.e(TAG, "final transcribe failed", it) }
                    .getOrDefault(""),
            )
            if (tail.isNotBlank()) {
                finalText = tail
                ListeningStateHolder.updateActiveMessage(tail)
                runCatching { analyze(tail) }
            }
        }

        // Closed here rather than in onDestroy so it always happens on the thread that runs
        // inference. transcribe() is a blocking native call that job cancellation cannot
        // interrupt, so destroying the ExecuTorch modules from the main thread could free
        // them mid-forward() — a native use-after-free.
        model?.close()
        model = null

        ListeningStateHolder.finalizeActiveMessage(toolInvoked = false)
        // A read error means capture died on us rather than being stopped — report that
        // instead of a clean result, which would otherwise look like "heard nothing".
        val readError = recorder.readError
        if (readError != null) {
            Log.e(TAG, "capture ended abnormally: $readError")
            fail(getString(R.string.listening_error_mic, readError))
        } else {
            ListeningStateHolder.setState(
                TranscriptionUiState.Result(
                    finalText.ifBlank { getString(R.string.listening_no_speech) },
                ),
            )
        }
        stopSelfAndCleanUp()
    }

    // --- lifecycle helpers -----------------------------------------------------

    private fun fail(message: String) {
        ListeningStateHolder.setState(TranscriptionUiState.Error(message))
    }

    private fun stopSelfAndCleanUp() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        if (recorder.isRecording) runCatching { recorder.stop() }
        // Only safe to close from here if no inference can be in flight. When the loop is
        // still active, cancelling it runs its finally block, which closes the model on the
        // inference thread instead.
        if (streamJob?.isActive != true) {
            model?.close()
            model = null
        }
        releaseWakeLock()
        streamJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * A foreground service keeps the process alive but does not keep the CPU awake once
     * the screen is off, which would stall transcription mid-session.
     *
     * Held without a timeout on purpose: any timeout short enough to satisfy lint would
     * silently stop transcribing partway through a long monitoring session, which is the
     * exact failure this whole change exists to fix. The lock cannot outlive its usefulness
     * — [onDestroy] releases it, and if the process is killed the OS reclaims it.
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            // No timeout: a timeout would silently stop listening mid-session. Release is
            // guaranteed by onDestroy instead.
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    /** One update per tick at most, and only when the text actually changed. */
    private fun updateNotification(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.notify(
                ListeningNotifications.NOTIFICATION_ID,
                ListeningNotifications.build(this, text),
            )
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun peak(samples: FloatArray): Float {
        var max = 0f
        for (s in samples) {
            val a = if (s < 0f) -s else s
            if (a > max) max = a
        }
        return max
    }

    companion object {
        private const val TAG = "ListeningService"
        private const val WAKE_LOCK_TAG = "SentriAI:listening"
        private const val STREAM_INTERVAL_MS = 5_000L
        private const val SAMPLE_RATE_F = AudioRecorder.SAMPLE_RATE.toFloat()

        /** Close the window off at 20 s, comfortably under Whisper's 30 s limit. */
        private const val MAX_WINDOW_SAMPLES = AudioRecorder.SAMPLE_RATE * 20

        private const val EMERGENCY_LABEL = "Detected"

        const val ACTION_START = "com.example.sentriai.action.START_LISTENING"
        const val ACTION_STOP = "com.example.sentriai.action.STOP_LISTENING"

        /**
         * Start listening. Only legal while the app is visible — see the class docs.
         * Uses [Context.startForegroundService] on API 26+ so the platform expects the
         * startForeground call that follows.
         */
        fun start(context: Context) {
            val intent = Intent(context, ListeningService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Safe to call from anywhere: stopping an already-foreground service is allowed. */
        fun stop(context: Context) {
            val intent = Intent(context, ListeningService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "stop dispatch failed", it) }
        }
    }
}
