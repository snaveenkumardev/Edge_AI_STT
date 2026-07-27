package com.edgeai.stt.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeai.stt.ai.FunctionGemmaMediaPipeEngine
import com.edgeai.stt.ai.SherpaOnnxSTTEngine
import com.edgeai.stt.ai.ToolInvocationResult
import com.edgeai.stt.audio.AcousticStressState
import com.edgeai.stt.audio.AudioStreamRecorder
import com.edgeai.stt.audio.AudioStressAnalyzer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class LogItem(
    val role: String,
    val userText: String = "",
    val result: ToolInvocationResult? = null,
    val rawJson: String = ""
)

@Composable
fun VoiceDetectorScreen(
    recorder: AudioStreamRecorder,
    sttEngine: SherpaOnnxSTTEngine,
    functionGemmaEngine: FunctionGemmaMediaPipeEngine,
    audioStressAnalyzer: AudioStressAnalyzer = remember { AudioStressAnalyzer() },
    modifier: Modifier = Modifier
) {
    var isRecording by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }
    val isGenerating by functionGemmaEngine.isGenerating.collectAsState()
    val isModelLoaded by functionGemmaEngine.isModelLoaded.collectAsState()
    val isTranscribing by sttEngine.isTranscribing.collectAsState()
    val liveTranscript by sttEngine.liveTranscript.collectAsState()
    val isSTTModelLoaded by sttEngine.isModelLoaded.collectAsState()
    val useSystemSpeech by sttEngine.useSystemSpeech.collectAsState()

    val dbLevel by audioStressAnalyzer.dbLevel.collectAsState()
    val stressState by audioStressAnalyzer.stressState.collectAsState()
    
    val conversationList = remember { mutableStateListOf<LogItem>() }
    val scope = rememberCoroutineScope()

    // Continuously process microphone audio buffer flow when recording for STT & Acoustic Stress
    LaunchedEffect(isRecording) {
        if (isRecording) {
            var panicTriggered = false
            // Only collect raw microphone stream if we are not using the System Speech recognizer (to prevent hardware lock conflicts)
            if (!useSystemSpeech) {
                recorder.audioBufferFlow.collect { pcmChunk ->
                    audioStressAnalyzer.processPcmChunk(pcmChunk, pcmChunk.size)
                    if (isSTTModelLoaded) {
                        sttEngine.processAudioChunk(pcmChunk)
                    }

                    // Auto-trigger emergency protocol if high panic/screaming amplitude burst is detected
                    if (audioStressAnalyzer.stressState.value == AcousticStressState.PANIC_SCREAM) {
                        if (!panicTriggered) {
                            panicTriggered = true
                            val currentTranscript = sttEngine.liveTranscript.value.trim()
                            val alertPrompt = if (currentTranscript.isNotEmpty()) {
                                "HIGH PANIC SCREAM DETECTED: $currentTranscript"
                            } else {
                                "HIGH PANIC SCREAM DETECTED"
                            }
                            android.util.Log.i("VoiceDetector", "Panic scream auto-triggered. Spoken transcript: '$currentTranscript'")
                            conversationList.add(LogItem(role = "user", userText = alertPrompt))
                            val (rawJson, result) = functionGemmaEngine.generateToolCall(
                                userPrompt = alertPrompt,
                                overrideStressState = AcousticStressState.PANIC_SCREAM,
                                overrideDbLevel = audioStressAnalyzer.dbLevel.value
                            )
                            conversationList.add(LogItem(role = "assistant", result = result, rawJson = rawJson))
                        }
                    }
                }
            }
        } else {
            audioStressAnalyzer.reset()
        }
    }

    val recordBtnColor by animateColorAsState(
        if (isRecording) Color(0xFFFF1744) else Color(0xFF00C853),
        label = "recordBtnColor"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Voice Detector & Danger Triage",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "On-Device Whisper STT + Acoustic Panic HUD + FunctionGemma",
            fontSize = 11.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Model Loaded Status Badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isModelLoaded) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFFD600).copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isModelLoaded) Color(0xFF00E676) else Color(0xFFFFD600))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (isModelLoaded) Color(0xFF00E676) else Color(0xFFFFD600))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isModelLoaded) "🟢 LLM Model Active (Gemma .bin)" else "🟡 Rule Triage Mode (No .bin File)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isModelLoaded) Color(0xFF00E676) else Color(0xFFFFD600)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // STT Status Badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (useSystemSpeech) Color(0xFF00B0FF).copy(alpha = 0.15f) else if (isSTTModelLoaded) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFF9100).copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (useSystemSpeech) Color(0xFF00B0FF) else if (isSTTModelLoaded) Color(0xFF00E676) else Color(0xFFFF9100))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (useSystemSpeech) Color(0xFF00B0FF) else if (isSTTModelLoaded) Color(0xFF00E676) else Color(0xFFFF9100))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (useSystemSpeech) "🔵 System STT Active (API)" else if (isSTTModelLoaded) "🟢 Whisper STT Active (Local)" else "🟡 System STT Fallback (No Local Model)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (useSystemSpeech) Color(0xFF00B0FF) else if (isSTTModelLoaded) Color(0xFF00E676) else Color(0xFFFF9100)
                )
            }
        }

        if (isSTTModelLoaded) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("STT Engine: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (!useSystemSpeech) Color(0xFF00E676).copy(alpha = 0.2f) else Color.DarkGray,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (!useSystemSpeech) Color(0xFF00E676) else Color.Gray),
                    modifier = Modifier.clickable { sttEngine.setUseSystemSpeech(false) }
                ) {
                    Text("Whisper Local", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (!useSystemSpeech) Color(0xFF00E676) else Color.LightGray)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (useSystemSpeech) Color(0xFF00B0FF).copy(alpha = 0.2f) else Color.DarkGray,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (useSystemSpeech) Color(0xFF00B0FF) else Color.Gray),
                    modifier = Modifier.clickable { sttEngine.setUseSystemSpeech(true) }
                ) {
                    Text("System API", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (useSystemSpeech) Color(0xFF00B0FF) else Color.LightGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live Acoustic Stress & Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRecording) "🔴 Recording & STT Active" else if (isTranscribing) "🎙️ Listening…" else "🟢 Ready",
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRecording) Color(0xFFFF1744) else Color(0xFF00C853)
                    )
                    if (isGenerating) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Triage…", fontSize = 11.sp, color = Color(0xFF00E5FF))
                        }
                    }
                }

                // Acoustic Audio Stress Meter HUD
                if (isRecording) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Acoustic Stress: ${dbLevel.toInt()} dB SPL",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val stressBadge = when (stressState) {
                            AcousticStressState.NORMAL -> Pair("🟢 Normal", Color(0xFF00C853))
                            AcousticStressState.ELEVATED -> Pair("🟡 Elevated", Color(0xFFFF9100))
                            AcousticStressState.PANIC_SCREAM -> Pair("🔴 PANIC SCREAM", Color(0xFFFF1744))
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = stressBadge.second.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, stressBadge.second)
                        ) {
                            Text(
                                text = stressBadge.first,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = stressBadge.second
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (dbLevel / 90f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = when (stressState) {
                            AcousticStressState.PANIC_SCREAM -> Color(0xFFFF1744)
                            AcousticStressState.ELEVATED -> Color(0xFFFF9100)
                            else -> Color(0xFF00C853)
                        }
                    )
                }

                if (liveTranscript.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Live Transcript:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = liveTranscript,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Conversation Log
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(conversationList) { item ->
                if (item.role == "user") {
                    // User Spoken Prompt Bubble
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF00C853).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFF00C853), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "🗣️ ${item.userText}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    // Assistant Intent Analysis Result Card
                    item.result?.let { res ->
                        IntentResultCard(result = res, rawJson = item.rawJson)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Manual text override row for testing/emulators
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = manualInputText,
                onValueChange = { manualInputText = it },
                placeholder = { Text("Try: 'someone is following me', 'I had an accident'", fontSize = 11.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Button(
                onClick = {
                    if (manualInputText.isNotBlank()) {
                        val textToAnalyze = manualInputText.trim()
                        manualInputText = ""
                        conversationList.add(LogItem(role = "user", userText = textToAnalyze))
                        scope.launch {
                            val (rawJson, result) = functionGemmaEngine.generateToolCall(textToAnalyze)
                            conversationList.add(LogItem(role = "assistant", result = result, rawJson = rawJson))
                        }
                    }
                },
                enabled = manualInputText.isNotBlank() && !isGenerating
            ) {
                Text("Send")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Record Button
        Button(
            onClick = {
                if (isRecording) {
                    val finalStress = audioStressAnalyzer.stressState.value
                    val finalDb = audioStressAnalyzer.dbLevel.value

                    if (!useSystemSpeech) {
                        recorder.stopRecording()
                    }
                    isRecording = false

                    scope.launch {
                        val transcript = sttEngine.stopListening()
                        android.util.Log.i("VoiceDetector", "User spoke (Final Transcript): '$transcript'")
                        if (transcript.isNotBlank()) {
                            conversationList.add(LogItem(role = "user", userText = transcript))
                            val (rawJson, result) = functionGemmaEngine.generateToolCall(
                                userPrompt = transcript,
                                overrideStressState = finalStress,
                                overrideDbLevel = finalDb
                            )
                            conversationList.add(LogItem(role = "assistant", result = result, rawJson = rawJson))
                        }
                    }
                } else {
                    sttEngine.clearTranscript()
                    if (!useSystemSpeech) {
                        recorder.startRecording()
                    }
                    sttEngine.startListening()
                    isRecording = true
                }
            },
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = recordBtnColor)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isRecording) "STOP" else "START",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "RECORD",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = {
                sttEngine.clearTranscript()
                conversationList.clear()
            }
        ) {
            Text("Clear Session", fontSize = 11.sp)
        }
    }
}

@Composable
fun IntentResultCard(
    result: ToolInvocationResult,
    rawJson: String
) {
    var showRawSchema by remember { mutableStateOf(false) }

    when (result) {
        is ToolInvocationResult.Emergency -> {
            val accentColor = when (result.toolName) {
                "stalker_threat_alert" -> Color(0xFFFF1744) // Bright Red
                "medical_emergency_dispatch" -> Color(0xFFFF3D00) // Deep Orange
                "accident_sos_alert" -> Color(0xFFFF9100) // Amber Safety
                "suicide_crisis_helpline" -> Color(0xFF00B0FF) // Crisis Blue
                "fire_disaster_sos" -> Color(0xFFFF5252) // Fire Red
                else -> Color(0xFFFF9800)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    // Header Banner with Gradient Accent
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.8f), accentColor.copy(alpha = 0.3f))
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = result.protocolAction,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.Black.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = "TOOL: ${result.toolName}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Content Details
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = result.message,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // GPS Location Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "📍 GPS Position: ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF)
                            )
                            Text(
                                text = result.location,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Context Summary Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "📝 Context: ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Text(
                                text = "\"${result.contextSummary}\"",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }

                        // Collapsible Raw JSON Toggle
                        if (rawJson.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .clickable { showRawSchema = !showRawSchema }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showRawSchema) "▼ Hide Raw JSON Schema" else "▶ Inspect FunctionGemma Raw JSON",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                            }

                            AnimatedVisibility(visible = showRawSchema) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = rawJson,
                                        modifier = Modifier.padding(10.dp),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        is ToolInvocationResult.GeneralTool -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "🛠️ Tool: ${result.name}", fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                    Text(text = result.message, fontSize = 12.sp, color = Color.White)
                }
            }
        }
        ToolInvocationResult.NoTool -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🟢 Safe Intent - No emergency tool invocation needed", fontSize = 12.sp, color = Color(0xFF00C853))
                }
            }
        }
    }
}
