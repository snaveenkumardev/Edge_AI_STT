package com.example.sentriai.service

import com.example.sentriai.model_inference.speech_to_text.AnalysisState
import com.example.sentriai.model_inference.speech_to_text.ChatMessage
import com.example.sentriai.model_inference.speech_to_text.TranscriptionUiState
import com.example.sentriai.model_inference.speech_to_text.cleanTranscriptText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level owner of listening state.
 *
 * Listening runs in [ListeningService] and survives the UI being navigated away from or
 * destroyed entirely, so its state cannot live in a ViewModel — a `viewModelScope`-owned
 * flow would be torn down while capture was still running, and reattaching the UI would
 * show an empty transcript.
 *
 * [ListeningService] is the only writer; `TranscriptionViewModel` re-exposes these flows
 * to Compose and is otherwise a pass-through.
 */
object ListeningStateHolder {

    private val _state = MutableStateFlow<TranscriptionUiState>(TranscriptionUiState.Idle)
    val state: StateFlow<TranscriptionUiState> = _state.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysis: StateFlow<AnalysisState> = _analysis.asStateFlow()

    // --- writers (service only) ------------------------------------------------

    fun setState(state: TranscriptionUiState) {
        _state.value = state
    }

    fun setAnalysis(analysis: AnalysisState) {
        _analysis.value = analysis
    }

    /**
     * Publish the in-progress utterance, replacing the trailing non-finalized bubble so
     * the live text grows in place rather than appending a bubble per tick.
     */
    fun updateActiveMessage(text: String) {
        val cleaned = cleanTranscriptText(text)
        if (cleaned.isBlank()) return
        val current = _chatMessages.value
        val last = current.lastOrNull()
        _chatMessages.value = if (last != null && !last.isFinalized) {
            current.dropLast(1) + last.copy(text = cleaned)
        } else {
            current + ChatMessage(text = cleaned)
        }
    }

    /** Close off the trailing bubble, recording whether it fired an emergency tool call. */
    fun finalizeActiveMessage(toolInvoked: Boolean) {
        val current = _chatMessages.value
        val last = current.lastOrNull() ?: return
        if (last.isFinalized) return
        _chatMessages.value =
            current.dropLast(1) + last.copy(isFinalized = true, toolInvoked = toolInvoked)
    }

    fun clearChatHistory() {
        _chatMessages.value = emptyList()
    }

    /** Latest text shown to the user — used for the notification's collapsed line. */
    fun latestText(): String? = _chatMessages.value.lastOrNull()?.text
}
