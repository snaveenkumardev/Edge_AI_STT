package com.example.sentriai.engine

import android.content.Context
import android.util.Log
import com.example.sentriai.data.TriggerLogStore
import com.example.sentriai.sms.EmergencySmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How much of the pipeline is actually operational, for the status badge in the UI. */
enum class PipelineReadiness {
    /** Both the classifier and the tool-call model loaded. */
    FULL,

    /**
     * The classifier LLM is not loaded, so only the safe word can raise an alert. Unprompted
     * speech is not monitored at all in this state — it is close to the feature being off.
     */
    DEGRADED,
}

/**
 * End-to-end processing for one speech transcript, in two stages.
 *
 * **Stage 1 — decide.** [EmergencyDetector] answers whether this utterance is an emergency.
 * **Stage 2 — act.** Only once the answer is yes, [FunctionGemmaEngine] formats the decision as
 * a `trigger_emergency_alert` call, which is then dispatched by [EmergencySmsSender] and
 * recorded by [TriggerLogStore].
 *
 * Keeping the two apart is what makes the tool-calling model safe to rely on: it is never in a
 * position to veto an emergency, only to describe one that has already been established.
 */
object EmergencyPipeline {

    private const val TAG = "EmergencyPipeline"

    /**
     * Whether stage 2 runs the model at all.
     *
     * With it off, alerts are dispatched straight from the detector's decision. That skips a
     * ~280 MB resident model whose only remaining contribution is extracting the spoken phrase,
     * so it is worth measuring both ways on the target device before shipping.
     */
    @Volatile
    var toolCallStageEnabled: Boolean = true

    @Volatile
    var readiness: PipelineReadiness = PipelineReadiness.DEGRADED
        private set

    /**
     * Loads both models.
     *
     * @return true when the classifier is available. False means detection has fallen back to
     *   the phrase trigger alone, which the UI must surface.
     */
    suspend fun initialize(context: Context): Boolean {
        val classifierReady = EmergencyDetector.initialize(context)
        val toolCallReady = if (toolCallStageEnabled) {
            FunctionGemmaEngine.initialize(context)
        } else {
            false
        }

        readiness = if (classifierReady) PipelineReadiness.FULL else PipelineReadiness.DEGRADED
        Log.i(TAG, "Pipeline ready: classifier=$classifierReady toolCall=$toolCallReady")
        return classifierReady
    }

    /** The reason the pipeline is degraded, or null when it is fully loaded. */
    fun statusMessage(): String? =
        if (readiness == PipelineReadiness.FULL) null
        else EmergencyDetector.lastError ?: "Classifier model unavailable — safe word only"

    /**
     * @return true if an emergency alert was dispatched.
     */
    suspend fun processTranscript(context: Context, transcript: String): Boolean =
        withContext(Dispatchers.Default) {
            if (transcript.isBlank()) return@withContext false

            Log.d(TAG, "Processing transcript: \"$transcript\"")

            val decision = EmergencyDetector.detect(transcript)
            if (decision == null) {
                // The detector has already logged which detectors ran and what each concluded.
                return@withContext false
            }

            val alert = if (toolCallStageEnabled) {
                FunctionGemmaEngine.buildAlertCall(decision, transcript)
            } else {
                Log.d(TAG, "Tool-call stage disabled; dispatching from the decision directly.")
                ToolCallResult(decision.emergencyType, decision.triggerPhrase, decision.confidence)
            }

            Log.w(
                TAG,
                "DISPATCH ${alert.emergencyType} detectedBy=${decision.source} " +
                    "confidence=${"%.2f".format(alert.confidence)} phrase=\"${alert.triggerPhrase}\"",
            )

            val smsResult = EmergencySmsSender.sendEmergencyAlert(
                context = context,
                emergencyType = alert.emergencyType,
                triggerPhrase = alert.triggerPhrase,
                confidence = alert.confidence,
            )

            if (smsResult.success) {
                Log.w(TAG, "SMS sent to ${smsResult.handlerNumber}")
            } else {
                Log.e(TAG, "SMS FAILED to ${smsResult.handlerNumber}: ${smsResult.failureReason}")
            }

            TriggerLogStore.logEvent(
                context = context,
                triggerPhrase = alert.triggerPhrase,
                emergencyType = alert.emergencyType,
                fullTranscript = transcript,
                confidenceScore = alert.confidence,
                smsSuccess = smsResult.success,
                handlerNumber = smsResult.handlerNumber,
                failureReason = smsResult.failureReason,
            )

            true
        }
}
