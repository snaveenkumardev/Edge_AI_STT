package com.example.sentriai.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sentriai.data.TriggerLogEntry
import com.example.sentriai.data.TriggerLogStore
import com.example.sentriai.engine.EmergencyPipeline
import com.example.sentriai.engine.FunctionGemmaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Represents the readiness state of the on-device FunctionGemma model engine. */
enum class EngineState {
    LOADING,
    READY,
    UNAVAILABLE,
    ERROR
}

/**
 * ViewModel exposing trigger log entries and managing FunctionGemma engine state.
 */
class TriggerLogViewModel(application: Application) : AndroidViewModel(application) {

    private val _logEntries = MutableStateFlow<List<TriggerLogEntry>>(emptyList())
    val logEntries: StateFlow<List<TriggerLogEntry>> = _logEntries.asStateFlow()

    private val _entryCount = MutableStateFlow(0)
    val entryCount: StateFlow<Int> = _entryCount.asStateFlow()

    private val _engineState = MutableStateFlow(EngineState.LOADING)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _engineErrorMessage = MutableStateFlow<String?>(null)
    val engineErrorMessage: StateFlow<String?> = _engineErrorMessage.asStateFlow()

    init {
        refresh()
        initEngine()
    }

    /**
     * Initializes the FunctionGemma 270M engine asynchronously.
     */
    fun initEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            _engineState.value = EngineState.LOADING
            val success = EmergencyPipeline.initialize(getApplication())
            if (success) {
                _engineState.value = EngineState.READY
                _engineErrorMessage.value = null
            } else {
                _engineState.value = EngineState.UNAVAILABLE
                _engineErrorMessage.value = FunctionGemmaEngine.lastError ?: "Model initialization failed"
            }
        }
    }

    /** Re-read entries from disk. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            _logEntries.value = TriggerLogStore.loadEntries(ctx)
            _entryCount.value = _logEntries.value.size
        }
    }

    /**
     * Processes a transcript through the FunctionGemma emergency pipeline.
     */
    fun processTranscript(transcript: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val triggered = EmergencyPipeline.processTranscript(getApplication(), transcript)
            refresh()
            onComplete(triggered)
        }
    }

    /** Wipe every entry (testing / developer reset). */
    fun clearLog() {
        viewModelScope.launch(Dispatchers.IO) {
            TriggerLogStore.clearAll(getApplication())
            _logEntries.value = emptyList()
            _entryCount.value = 0
        }
    }

    override fun onCleared() {
        super.onCleared()
        FunctionGemmaEngine.close()
    }
}
