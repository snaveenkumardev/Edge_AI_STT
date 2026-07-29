package com.example.sentriai.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sentriai.R
import com.example.sentriai.data.TriggerLogEntry
import com.example.sentriai.engine.EmergencyPipeline
import com.example.sentriai.model_inference.speech_to_text.TranscriptionUiState
import com.example.sentriai.model_inference.speech_to_text.TranscriptionViewModel
import com.example.sentriai.ui.theme.SentriAITheme
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.example.sentriai.model_inference.speech_to_text.ChatMessage

// ── Analysis status model ───────────────────────────────────────────────────

private sealed interface AnalysisStatus {
    data object Idle : AnalysisStatus
    data object Analyzing : AnalysisStatus
    data class Safe(val message: String) : AnalysisStatus
    data class Emergency(val type: String) : AnalysisStatus
}

private val AnalyzingBlue = Color(0xFF2563EB)
private val SafeGreen = Color(0xFF15803D)
private val EmergencyAmber = Color(0xFFD97706)

/**
 * Live transcript surface, reached from the activation screen once the mic is capturing
 * and the Whisper model has finished loading.
 *
 * Text grows every couple of seconds as the streaming loop re-transcribes its window.
 * Stopping here winds the loop down and leaves the final transcript on screen; the back
 * button returns to the activation screen and deliberately leaves the stream running,
 * so the assistant stays armed until it is powered off.
 *
 * @param viewModel must be the same instance the activation screen started — the stream
 *   lives in it, so a per-destination instance would show an empty transcript.
 */
@Composable
fun VoiceTranscriptScreen(
    onBack: () -> Unit,
    viewModel: TranscriptionViewModel,
    triggerLogViewModel: TriggerLogViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val logEntries by triggerLogViewModel.logEntries.collectAsState()

    // Finishing carries no text of its own, so the last transcript we saw is held here to
    // keep it on screen while the loop transcribes its tail audio.
    var transcript by rememberSaveable { mutableStateOf("") }
    var lastTriggeredCharIndex by rememberSaveable { mutableStateOf(-1) }
    var analysisInProgress by rememberSaveable { mutableStateOf(false) }
    var analysisStatus by rememberSaveable { mutableStateOf<String>("idle") }

    var previousTranscriptText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state) {
        // Update local transcript display
        when (val current = state) {
            is TranscriptionUiState.Listening -> {
                transcript = current.text
                if (transcript.isNotBlank()) {
                    viewModel.updateActiveMessage(transcript)
                }
            }
            is TranscriptionUiState.Result -> {
                transcript = current.text
                if (transcript.isNotBlank()) {
                    viewModel.updateActiveMessage(transcript)
                }
            }
            is TranscriptionUiState.Preparing -> {
                lastTriggeredCharIndex = -1
                analysisInProgress = false
                analysisStatus = "idle"
                previousTranscriptText = ""
            }
            else -> Unit
        }

        // Run emergency safety threat analysis on every new transcript update
        if (transcript.isNotBlank() && !analysisInProgress) {
            analysisInProgress = true
            analysisStatus = "analyzing"
            try {
                var isEmergency = false
                var newTriggerDetected = false

                withContext(Dispatchers.Default) {
                    val toolResult = com.example.sentriai.engine.FunctionGemmaEngine.analyzeTranscript(transcript)
                    if (toolResult != null) {
                        isEmergency = true
                        val triggerPhrase = toolResult.triggerPhrase
                        val matchIndex = transcript.lowercase().lastIndexOf(triggerPhrase.lowercase())
                        if (matchIndex != -1) {
                            val matchEndChar = matchIndex + triggerPhrase.length
                            if (matchEndChar > lastTriggeredCharIndex) {
                                // New safety threat detected! Trigger the alert and send SMS
                                newTriggerDetected = true
                                lastTriggeredCharIndex = matchEndChar

                                // Attempt to send SMS
                                val smsResult = com.example.sentriai.sms.EmergencySmsSender.sendEmergencyAlert(
                                    context = context,
                                    emergencyType = toolResult.emergencyType,
                                    triggerPhrase = toolResult.triggerPhrase,
                                    confidence = toolResult.confidence
                                )

                                // Log event to disk
                                com.example.sentriai.data.TriggerLogStore.logEvent(
                                    context = context,
                                    triggerPhrase = toolResult.triggerPhrase,
                                    emergencyType = toolResult.emergencyType,
                                    fullTranscript = transcript,
                                    confidenceScore = toolResult.confidence,
                                    smsSuccess = smsResult.success,
                                    handlerNumber = smsResult.handlerNumber,
                                    failureReason = smsResult.failureReason
                                )
                            }
                        }
                    }
                }

                if (newTriggerDetected) {
                    analysisStatus = "emergency"
                    viewModel.finalizeActiveMessage(toolInvoked = true)
                    // Clear the box immediately upon triggering an emergency alert!
                    viewModel.clearTranscript()
                    transcript = ""
                    previousTranscriptText = ""
                    lastTriggeredCharIndex = -1
                } else {
                    analysisStatus = "safe"
                    if (state is TranscriptionUiState.Result) {
                        viewModel.finalizeActiveMessage(toolInvoked = false)
                    }
                }

                // Refresh log entries so any new alert appears inline immediately
                triggerLogViewModel.refresh()
            } catch (e: Exception) {
                Log.e("VoiceTranscript", "Error running EmergencyPipeline: ${e.message}", e)
                analysisStatus = "safe"
                if (state is TranscriptionUiState.Result) {
                    viewModel.finalizeActiveMessage(toolInvoked = false)
                }
            } finally {
                analysisInProgress = false
            }
        } else if (transcript.isBlank() && state is TranscriptionUiState.Result) {
            viewModel.finalizeActiveMessage(toolInvoked = false)
        }
    }

    LaunchedEffect(transcript) {
        if (transcript.isNotBlank()) {
            delay(3000L)
            viewModel.finalizeActiveMessage(toolInvoked = false)
            viewModel.clearTranscript()
            transcript = ""
            previousTranscriptText = ""
            lastTriggeredCharIndex = -1
            analysisStatus = "safe"
        }
    }

    // Map serializable status string to sealed class for rendering
    val currentAnalysisStatus: AnalysisStatus = when (analysisStatus) {
        "analyzing" -> AnalysisStatus.Analyzing
        "safe" -> AnalysisStatus.Safe(stringResource(R.string.voice_transcript_no_threat))
        "emergency" -> AnalysisStatus.Emergency("Detected")
        else -> AnalysisStatus.Idle
    }

    VoiceTranscriptContent(
        state = state,
        chatMessages = chatMessages,
        analysisStatus = currentAnalysisStatus,
        logEntries = logEntries,
        onStopClick = viewModel::stopStreaming,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun VoiceTranscriptContent(
    state: TranscriptionUiState,
    chatMessages: List<ChatMessage>,
    analysisStatus: AnalysisStatus = AnalysisStatus.Idle,
    logEntries: List<TriggerLogEntry> = emptyList(),
    onStopClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLive = state is TranscriptionUiState.Listening || state is TranscriptionUiState.Preparing
    val isFinishing = state is TranscriptionUiState.Finishing
    val error = (state as? TranscriptionUiState.Error)?.message

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground),
    ) {
        VoiceTranscriptTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            StatusLine(state = state)

            Spacer(Modifier.height(14.dp))

            // Dynamic weights to split space proportionally and prevent overflows
            val transcriptWeight = if (logEntries.isEmpty()) 1f else 0.55f

            ChatTranscriptCard(
                chatMessages = chatMessages,
                error = error,
                isLive = isLive,
                modifier = Modifier.weight(transcriptWeight),
            )

            Spacer(Modifier.height(12.dp))

            // ── Analysis Status Chip ────────────────────────────────────
            AnalysisStatusChip(status = analysisStatus)

            // ── Inline Alert Log ────────────────────────────────────────
            if (logEntries.isNotEmpty()) {
                InlineAlertLog(
                    entries = logEntries,
                    modifier = Modifier.weight(0.45f),
                )
                Spacer(Modifier.height(12.dp))
            }

            TranscriptActionButton(
                isLive = isLive,
                isFinishing = isFinishing,
                onStopClick = onStopClick,
                onDoneClick = onBack,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Analysis Status Chip ────────────────────────────────────────────────────

@Composable
private fun AnalysisStatusChip(status: AnalysisStatus) {
    if (status is AnalysisStatus.Idle) return

    val (label, dotColor) = when (status) {
        is AnalysisStatus.Analyzing -> stringResource(R.string.voice_transcript_analyzing) to AnalyzingBlue
        is AnalysisStatus.Safe -> status.message to SafeGreen
        is AnalysisStatus.Emergency -> stringResource(R.string.voice_transcript_emergency_detected, status.type) to EmergencyAmber
        else -> return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when (status) {
                    is AnalysisStatus.Analyzing -> SoftBlueContainer
                    is AnalysisStatus.Safe -> Color(0xFFDCFCE7)
                    is AnalysisStatus.Emergency -> Color(0xFFFEF3C7)
                    else -> CardBackground
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = dotColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
}

// ── Inline Alert Log (recent entries) ───────────────────────────────────────

@Composable
private fun InlineAlertLog(
    entries: List<TriggerLogEntry>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Text(
            text = stringResource(R.string.voice_transcript_alert_log_label).uppercase(),
            color = MutedText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.9.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Show entries in a scrollable list inside allocated weight space
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(entries, key = { it.id }) { entry ->
                TriggerLogRow(entry)
            }
        }
    }
}

// ── Existing composables (unchanged) ────────────────────────────────────────

@Composable
private fun VoiceTranscriptTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TopBarBackground)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.voice_transcript_back),
                tint = NavyInk,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.voice_transcript_title),
            color = NavyInk,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Coloured dot plus a one-line description of what the streaming loop is doing. */
@Composable
private fun StatusLine(state: TranscriptionUiState) {
    val (label, dotColor) = when (state) {
        is TranscriptionUiState.Preparing -> R.string.voice_transcript_status_preparing to MutedText
        is TranscriptionUiState.Listening -> R.string.voice_transcript_status_listening to PowerOnGreen
        is TranscriptionUiState.Finishing -> R.string.voice_transcript_status_finishing to MutedText
        is TranscriptionUiState.Error -> R.string.voice_transcript_status_stopped to ErrorRed
        else -> R.string.voice_transcript_status_stopped to MutedText
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(label),
            color = MutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun ChatTranscriptCard(
    chatMessages: List<ChatMessage>,
    error: String?,
    isLive: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBackground)
            .border(width = 1.dp, color = CardBorder, shape = RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.voice_transcript_label).uppercase(),
            color = MutedText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.9.sp,
        )
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            when {
                error != null -> Text(
                    text = error,
                    color = ErrorRed,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                )
                chatMessages.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(
                            if (isLive) R.string.voice_transcript_waiting
                            else R.string.voice_transcript_status_stopped,
                        ),
                        color = MutedText,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(chatMessages, key = { it.id }) { message ->
                            ChatMessageItem(message = message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 2.dp
                    )
                )
                .background(if (message.toolInvoked) Color(0xFFFEE2E2) else SoftBlueContainer)
                .border(
                    width = 1.dp,
                    color = if (message.toolInvoked) Color(0xFFFCA5A5) else CardBorder,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 2.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                color = NavyInk,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.height(4.dp))

        val statusText = when {
            !message.isFinalized -> "analyzing..."
            message.toolInvoked -> "- tool invoked"
            else -> "- no tool invoked"
        }
        val statusColor = when {
            !message.isFinalized -> AccentBlue
            message.toolInvoked -> ErrorRed
            else -> MutedText
        }
        val statusFontWeight = if (message.toolInvoked) FontWeight.Bold else FontWeight.Normal

        Text(
            text = statusText,
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = statusFontWeight,
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

/**
 * Stop while the stream is live, then Done once the final transcript has landed. Taps are
 * refused in between so a second stop can't land while the loop is still winding down.
 */
@Composable
private fun TranscriptActionButton(
    isLive: Boolean,
    isFinishing: Boolean,
    onStopClick: () -> Unit,
    onDoneClick: () -> Unit,
) {
    val stopping = isLive || isFinishing
    val containerColor = if (stopping) StopRed else NavyInk
    Button(
        onClick = if (isLive) onStopClick else onDoneClick,
        enabled = !isFinishing,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(29.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = containerColor,
            disabledContentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(
            text = stringResource(
                when {
                    isFinishing -> R.string.ai_assistant_power_stopping
                    isLive -> R.string.voice_transcript_stop
                    else -> R.string.voice_transcript_done
                },
            ),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Preview(name = "Listening", showBackground = true, widthDp = 335, heightDp = 690)
@Composable
private fun VoiceTranscriptListeningPreview() {
    SentriAITheme(dynamicColor = false) {
        VoiceTranscriptContent(
            state = TranscriptionUiState.Listening("Hello, I am walking home from the station now."),
            chatMessages = listOf(
                ChatMessage(text = "Hello, I am walking home from the station now.", isFinalized = false)
            ),
            analysisStatus = AnalysisStatus.Analyzing,
            onStopClick = {},
            onBack = {},
        )
    }
}

@Preview(name = "Stopped", showBackground = true, widthDp = 335, heightDp = 690)
@Composable
private fun VoiceTranscriptStoppedPreview() {
    SentriAITheme(dynamicColor = false) {
        VoiceTranscriptContent(
            state = TranscriptionUiState.Result("Hello, I am walking home from the station now."),
            chatMessages = listOf(
                ChatMessage(text = "Hello, I am walking home from the station now.", isFinalized = true, toolInvoked = false)
            ),
            analysisStatus = AnalysisStatus.Safe("No threat detected"),
            onStopClick = {},
            onBack = {},
        )
    }
}
