package com.example.safetymode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safetymode.asr.TranscriptionUiState
import com.example.safetymode.asr.TranscriptionViewModel
import com.example.safetymode.ui.theme.SafetyModeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafetyModeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    TranscriptionScreen(modifier = Modifier.padding(inner))
                }
            }
        }
    }
}

@Composable
fun TranscriptionScreen(
    modifier: Modifier = Modifier,
    vm: TranscriptionViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.startStreaming()
    }

    fun ensurePermissionThenStream() {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.startStreaming()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("Whisper Voice-to-Text", style = MaterialTheme.typography.headlineSmall)

        when (val s = state) {
            TranscriptionUiState.Idle -> {
                Text("Tap start and speak.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { ensurePermissionThenStream() }) { Text("Start") }
            }

            is TranscriptionUiState.Listening -> {
                CircularProgressIndicator()
                Text("Listening… speak now.", style = MaterialTheme.typography.bodyMedium)
                if (s.text.isNotBlank()) {
                    Text(s.text, style = MaterialTheme.typography.bodyLarge)
                }
                Button(onClick = { vm.stopStreaming() }) { Text("Stop") }
            }

            TranscriptionUiState.Recording -> {
                Text("Recording… speak now.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { vm.stopAndTranscribe() }) { Text("Stop & Transcribe") }
            }

            TranscriptionUiState.Transcribing -> {
                CircularProgressIndicator()
                Text("Transcribing on-device…", style = MaterialTheme.typography.bodyMedium)
            }

            is TranscriptionUiState.Result -> {
                Text("Transcript", style = MaterialTheme.typography.titleMedium)
                Text(s.text, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { vm.reset() }) { Text("Start again") }
            }

            is TranscriptionUiState.Error -> {
                Text(
                    "Error: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { vm.reset() }) { Text("Try again") }
            }
        }
    }
}
