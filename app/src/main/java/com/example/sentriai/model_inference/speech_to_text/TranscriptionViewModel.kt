package com.example.sentriai.model_inference.speech_to_text

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.sentriai.service.ListeningService
import com.example.sentriai.service.ListeningStateHolder
import kotlinx.coroutines.flow.StateFlow

/**
 * Controller for the listening session — not its owner.
 *
 * The mic, the Whisper model and the streaming loop all live in [ListeningService] so that
 * listening survives the UI being backgrounded or destroyed. This class only translates UI
 * intent into service commands and re-exposes [ListeningStateHolder]'s flows to Compose.
 *
 * Note what is deliberately absent: there is no `onCleared` teardown. Cancelling capture
 * when the ViewModel clears is exactly what made backgrounding impossible before.
 */
class TranscriptionViewModel(app: Application) : AndroidViewModel(app) {

    val state: StateFlow<TranscriptionUiState> = ListeningStateHolder.state
    val chatMessages: StateFlow<List<ChatMessage>> = ListeningStateHolder.chatMessages
    val analysis: StateFlow<AnalysisState> = ListeningStateHolder.analysis

    /**
     * Start mic capture and the streaming loop. Callers must hold RECORD_AUDIO, and this
     * must be called while the app is visible — from API 34 a microphone-type foreground
     * service cannot be started from the background.
     */
    fun startStreaming() {
        ListeningService.start(getApplication())
    }

    /** Ask the service to wind down; it publishes the final transcript before stopping. */
    fun stopStreaming() {
        ListeningService.stop(getApplication())
    }

    fun clearChatHistory() {
        ListeningStateHolder.clearChatHistory()
    }

    fun reset() {
        ListeningStateHolder.setState(TranscriptionUiState.Idle)
        ListeningStateHolder.setAnalysis(AnalysisState.Idle)
    }
}
