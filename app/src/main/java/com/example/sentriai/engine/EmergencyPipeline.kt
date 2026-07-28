package com.example.sentriai.engine

import android.content.Context
import android.util.Log
import com.example.sentriai.data.TriggerLogStore
import com.example.sentriai.sms.EmergencySmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * End-to-end processing pipeline for speech transcripts.
 *
 * Connects:
 * 1. [FunctionGemmaEngine] -> analyzes transcript for tool trigger decisions
 * 2. [EmergencySmsSender] -> sends SMS if emergency alert tool call fires
 * 3. [TriggerLogStore] -> logs every trigger event (success or failure)
 */
object EmergencyPipeline {

    private const val TAG = "EmergencyPipeline"

    /**
     * Initializes the underlying FunctionGemma engine.
     */
    suspend fun initialize(context: Context): Boolean {
        return FunctionGemmaEngine.initialize(context)
    }

    /**
     * Processes a single speech transcript through FunctionGemma and the emergency dispatcher.
     *
     * @param context Application context.
     * @param transcript The speech text from Whisper STT.
     * @return true if an emergency alert tool call was triggered, false otherwise.
     */
    suspend fun processTranscript(context: Context, transcript: String): Boolean = withContext(Dispatchers.Default) {
        if (transcript.isBlank()) return@withContext false

        Log.d(TAG, "Processing transcript through pipeline: \"$transcript\"")
        val toolResult = FunctionGemmaEngine.analyzeTranscript(transcript)

        if (toolResult != null) {
            Log.w(TAG, "Emergency alert triggered by FunctionGemma! Type: ${toolResult.emergencyType}")

            // Attempt to send SMS
            val smsResult = EmergencySmsSender.sendEmergencyAlert(
                context = context,
                emergencyType = toolResult.emergencyType,
                triggerPhrase = toolResult.triggerPhrase,
                confidence = toolResult.confidence
            )

            // Log event to disk
            TriggerLogStore.logEvent(
                context = context,
                triggerPhrase = toolResult.triggerPhrase,
                emergencyType = toolResult.emergencyType,
                fullTranscript = transcript,
                confidenceScore = toolResult.confidence,
                smsSuccess = smsResult.success,
                handlerNumber = smsResult.handlerNumber,
                failureReason = smsResult.failureReason
            )

            true
        } else {
            Log.d(TAG, "No emergency tool trigger detected for transcript.")
            false
        }
    }
}
