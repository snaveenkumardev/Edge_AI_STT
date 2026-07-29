package com.example.sentriai.model_inference.speech_to_text

import java.util.UUID

/**
 * State types shared between the listening service (which produces them) and the UI
 * (which observes them).
 *
 * These deliberately live outside [TranscriptionViewModel]: listening outlives any
 * ViewModel, so the state it produces cannot be owned by one. See
 * `com.example.sentriai.service.ListeningStateHolder` for the process-level owner.
 */

/** UI-facing state for the transcription screen. */
sealed interface TranscriptionUiState {
    data object Idle : TranscriptionUiState

    /**
     * Live streaming: mic is capturing but the model is still being loaded, so no text
     * can be produced yet. The first load copies ~200 MB of assets to filesDir and takes
     * a while; audio recorded during it is kept and transcribed once loading finishes.
     */
    data object Preparing : TranscriptionUiState

    /** Live streaming: model is ready, mic is on and [text] grows as speech is transcribed. */
    data class Listening(val text: String) : TranscriptionUiState

    /** Stop requested; the loop is transcribing the tail audio before emitting [Result]. */
    data object Finishing : TranscriptionUiState
    data class Result(val text: String) : TranscriptionUiState
    data class Error(val message: String) : TranscriptionUiState
}

/** Where the emergency analysis of the current transcript has got to. */
sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Analyzing : AnalysisState
    data object Safe : AnalysisState
    data class Emergency(val type: String) : AnalysisState
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFinalized: Boolean = false,
    val toolInvoked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Strip Whisper's bracketed non-speech annotations and collapse whitespace. */
fun cleanTranscriptText(text: String): String =
    text
        .replace(Regex("\\[.*?\\]"), "")
        .replace(Regex("\\(.*?\\)"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
