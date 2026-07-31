package com.example.sentriai.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sentriai.models.ModelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives the first-launch model download.
 *
 * The download runs in [viewModelScope], so leaving the screen cancels it. That is survivable
 * because bytes are kept in a `.part` file and the next attempt resumes — but it does mean a
 * download does not continue with the app in the background. Moving it to a foreground service
 * or WorkManager is the upgrade if that matters.
 */
class ModelSetupViewModel(application: Application) : AndroidViewModel(application) {

    val state: StateFlow<ModelRepository.State> = ModelRepository.state

    private var job: Job? = null

    init {
        start()
    }

    /** Begins, or retries, fetching whatever is missing. A run already in flight is left alone. */
    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            ModelRepository.ensureModels(getApplication())
        }
    }
}
