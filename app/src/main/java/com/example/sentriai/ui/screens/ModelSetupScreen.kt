package com.example.sentriai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sentriai.R
import com.example.sentriai.models.ModelAsset
import com.example.sentriai.models.ModelRepository
import com.example.sentriai.ui.theme.SentriAITheme

/**
 * First-launch gate while the on-device models download.
 *
 * [onReady] fires once every required model is present. Optional models that failed are
 * reported rather than blocking, since the app still detects emergencies without them.
 */
@Composable
fun ModelSetupScreen(
    onReady: (missingOptional: List<ModelAsset>) -> Unit,
    viewModel: ModelSetupViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Raised from an effect rather than straight out of composition: onReady swaps the whole
    // screen out, and driving that from the composition itself re-enters it mid-pass.
    val ready = state as? ModelRepository.State.Ready
    LaunchedEffect(ready) {
        if (ready != null) onReady(ready.missingOptional)
    }

    ModelSetupContent(state = state, onRetry = viewModel::start)
}

@Composable
private fun ModelSetupContent(
    state: ModelRepository.State,
    onRetry: () -> Unit,
) {
    val failed = state as? ModelRepository.State.Failed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(if (failed != null) Color(0xFFFEE2E2) else SoftBlueContainer)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (failed != null) Icons.Outlined.ErrorOutline else Icons.Default.CloudDownload,
                contentDescription = null,
                tint = if (failed != null) Color(0xFFC62828) else AccentBlue,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(
                if (failed != null) R.string.model_setup_failed_title else R.string.model_setup_title
            ),
            color = NavyInk,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = failed?.message ?: stringResource(R.string.model_setup_desc),
            color = MutedText,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                when (state) {
                    is ModelRepository.State.Checking -> {
                        Text(
                            text = stringResource(R.string.model_setup_checking),
                            color = NavyInk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is ModelRepository.State.Downloading -> DownloadRow(state)

                    is ModelRepository.State.Ready -> Text(
                        text = stringResource(R.string.model_setup_ready),
                        color = NavyInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    is ModelRepository.State.Failed -> Text(
                        text = stringResource(R.string.model_setup_resume_hint),
                        color = MutedText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }

        // Reachable from behind the gate on purpose: when a required model has no configured
        // source, this screen is as far as the app gets, so the hub shortcut has to live here
        // too and not only on the assistant screen. Debug builds only.
        HuggingFaceDownloadCard(modifier = Modifier.padding(top = 16.dp))

        if (failed != null) {
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .width(200.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyInk),
            ) {
                Text(
                    text = stringResource(R.string.model_setup_retry),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(state: ModelRepository.State.Downloading) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = state.asset.displayName,
            color = NavyInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${state.fileIndex} / ${state.fileCount}",
            color = MutedText,
            fontSize = 13.sp,
        )
    }

    Spacer(Modifier.height(12.dp))

    val progress = state.fileProgress
    if (progress != null) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else {
        // No Content-Length and no catalog size — show motion rather than a bar stuck at zero.
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text = if (state.bytesTotal > 0) {
            "${state.bytesDone / 1_048_576} MB of ${state.bytesTotal / 1_048_576} MB"
        } else {
            "${state.bytesDone / 1_048_576} MB"
        },
        color = MutedText,
        fontSize = 13.sp,
    )
}

@Preview(showBackground = true, widthDp = 335, heightDp = 690)
@Composable
private fun ModelSetupDownloadingPreview() {
    SentriAITheme(dynamicColor = false) {
        ModelSetupContent(
            state = ModelRepository.State.Downloading(
                asset = com.example.sentriai.models.ModelCatalog.WHISPER_MODEL,
                fileIndex = 3,
                fileCount = 5,
                bytesDone = 62_914_560,
                bytesTotal = 198_506_112,
            ),
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 335, heightDp = 690)
@Composable
private fun ModelSetupFailedPreview() {
    SentriAITheme(dynamicColor = false) {
        ModelSetupContent(
            state = ModelRepository.State.Failed(
                asset = com.example.sentriai.models.ModelCatalog.WHISPER_MODEL,
                message = "HTTP 404 fetching model.pte (Not Found)",
            ),
            onRetry = {},
        )
    }
}
