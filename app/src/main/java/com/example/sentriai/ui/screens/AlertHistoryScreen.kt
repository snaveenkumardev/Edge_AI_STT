package com.example.sentriai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentriai.R

/**
 * Full-screen Alert History page — navigable from the home screen's bell icon.
 *
 * Reuses the existing [TriggerLogSheetContent] composable for the log body
 * and wraps it in a standard top-bar layout.
 */
@Composable
fun AlertHistoryScreen(
    onBack: () -> Unit,
    triggerLogViewModel: TriggerLogViewModel,
    modifier: Modifier = Modifier,
) {
    val logEntries by triggerLogViewModel.logEntries.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground),
    ) {
        // ── Top bar ─────────────────────────────────────────────────────
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
                text = stringResource(R.string.alert_history_title),
                color = NavyInk,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // ── Log body (reused from TriggerLogSheet) ──────────────────────
        TriggerLogSheetContent(
            entries = logEntries,
            onClearLog = { triggerLogViewModel.clearLog() },
            modifier = Modifier.weight(1f),
        )
    }
}
