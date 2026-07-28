package com.example.sentriai.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentriai.R
import com.example.sentriai.data.TriggerLogEntry

// ── Palette (matches AiAssistant.kt / GuardianPalette.kt) ───────────────────
private val SheetBackground = PageBackground

private val SuccessGreen = Color(0xFF15803D)
private val SuccessGreenBg = Color(0xFFDCFCE7)
private val FailureRed = Color(0xFFC62828)
private val FailureRedBg = Color(0xFFFEE2E2)

// ── Sheet Content ───────────────────────────────────────────────────────────

/**
 * Full content rendered inside the [ModalBottomSheet].
 *
 * Shows a header, the chronological log as a scrollable list, and a
 * discreet "Clear Log" button at the bottom for testing resets.
 */
@Composable
fun TriggerLogSheetContent(
    entries: List<TriggerLogEntry>,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SheetBackground)
            .padding(bottom = 24.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_trigger_log),
                contentDescription = null,
                tint = NavyInk,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.trigger_log_title),
                color = NavyInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            if (entries.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SoftBlueContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = entries.size.toString(),
                        color = AccentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        HorizontalDivider(color = CardBorder, thickness = 1.dp)

        // ── Body ────────────────────────────────────────────────────────
        if (entries.isEmpty()) {
            TriggerLogEmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp,
                ),
            ) {
                items(entries, key = { it.id }) { entry ->
                    TriggerLogRow(entry)
                }
            }
        }

        // ── Clear button (testing only) ─────────────────────────────────
        if (entries.isNotEmpty()) {
            TextButton(
                onClick = { showClearDialog = true },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.trigger_log_clear),
                    color = MutedText,
                    fontSize = 13.sp,
                )
            }
        }
    }

    if (showClearDialog) {
        ClearLogConfirmDialog(
            onConfirm = {
                showClearDialog = false
                onClearLog()
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

// ── Single Row ──────────────────────────────────────────────────────────────

@Composable
internal fun TriggerLogRow(entry: TriggerLogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(if (entry.smsSuccess) SuccessGreen else FailureRed),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Row 1: emergency type + timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.emergencyType,
                    color = NavyInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relativeTimestamp(entry.timestampMillis),
                    color = MutedText,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Row 2: trigger phrase
            Text(
                text = "\"${entry.triggerPhrase}\"",
                color = NavyInk.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            // Row 3: confidence + delivery status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Confidence
                Text(
                    text = stringResource(
                        R.string.trigger_log_confidence,
                        entry.confidenceScore * 100f,
                    ),
                    color = MutedText,
                    fontSize = 13.sp,
                )

                // Delivery status chip
                val (statusText, statusColor, statusBg) = if (entry.smsSuccess) {
                    Triple(
                        stringResource(R.string.trigger_log_sms_sent) + " ✓",
                        SuccessGreen,
                        SuccessGreenBg,
                    )
                } else {
                    Triple(
                        stringResource(R.string.trigger_log_sms_failed) + " ✗",
                        FailureRed,
                        FailureRedBg,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Failure reason (only when SMS failed)
            if (!entry.smsSuccess && !entry.failureReason.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.failureReason,
                    color = FailureRed.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Handler number
            Spacer(Modifier.height(4.dp))
            Text(
                text = "To: ${entry.handlerNumber}",
                color = MutedText,
                fontSize = 12.sp,
            )
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────────────────

@Composable
private fun TriggerLogEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(SoftBlueContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(30.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.trigger_log_empty_title),
            color = NavyInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.trigger_log_empty_body),
            color = MutedText,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Clear-log Confirmation Dialog ───────────────────────────────────────────

@Composable
private fun ClearLogConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.trigger_log_clear),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.trigger_log_clear_confirm),
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.trigger_log_clear_yes),
                    color = FailureRed,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.trigger_log_clear_no),
                    color = MutedText,
                )
            }
        },
    )
}

// ── Helpers ─────────────────────────────────────────────────────────────────

/** Produces human-friendly relative timestamps ("2 min ago", "Yesterday"). */
private fun relativeTimestamp(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
