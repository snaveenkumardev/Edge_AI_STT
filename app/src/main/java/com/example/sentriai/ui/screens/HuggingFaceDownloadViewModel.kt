package com.example.sentriai.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sentriai.BuildConfig
import com.example.sentriai.models.ModelAsset
import com.example.sentriai.models.ModelCatalog
import com.example.sentriai.models.ModelDownloader
import com.example.sentriai.models.ModelStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the debug-only "download from Hugging Face" button.
 *
 * Deliberately separate from [ModelSetupViewModel]: that one gates first launch and pulls the
 * required set from configured storage, whereas this is a developer shortcut for pulling a
 * gated `.task` straight off the hub with a personal token. Keeping them apart means this
 * cannot interfere with the production path.
 */
class HuggingFaceDownloadViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface State {
        data object Idle : State
        data class Downloading(val bytesDone: Long, val bytesTotal: Long) : State {
            val progress: Float?
                get() = if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else null
        }

        data object Done : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /** Catalog entries with a hub source that are not already on disk. */
    fun downloadable(): List<ModelAsset> = ModelCatalog.all.filter {
        it.huggingFaceUrl != null && !ModelStore.isAvailable(getApplication(), it)
    }

    /** True when a token was found in local.properties at build time. */
    fun hasToken(): Boolean = BuildConfig.HF_TOKEN.isNotBlank()

    fun download(asset: ModelAsset) {
        if (job?.isActive == true) return
        val url = asset.huggingFaceUrl ?: return

        if (!hasToken()) {
            _state.value = State.Failed(
                "No Hugging Face token. Add hfToken=hf_… to local.properties and rebuild."
            )
            return
        }

        job = viewModelScope.launch {
            _state.value = State.Downloading(0, asset.sizeBytes ?: 0)
            val result = ModelDownloader.downloadFrom(
                context = getApplication(),
                asset = asset,
                sourceUrl = url,
                authToken = BuildConfig.HF_TOKEN,
            ) { done, total ->
                _state.value = State.Downloading(done, total)
            }
            _state.value = result.fold(
                onSuccess = { State.Done },
                onFailure = { State.Failed(it.message ?: "Download failed") },
            )
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = State.Idle
    }
}
