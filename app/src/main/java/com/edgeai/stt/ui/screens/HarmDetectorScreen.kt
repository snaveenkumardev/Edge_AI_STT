package com.edgeai.stt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import com.edgeai.stt.ai.FunctionGemmaMediaPipeEngine
import com.edgeai.stt.ai.ToolInvocationResult
import kotlinx.coroutines.launch

@Composable
fun HarmDetectorScreen(
    functionGemmaEngine: FunctionGemmaMediaPipeEngine,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var rawJsonResponse by remember { mutableStateOf("") }
    var toolResultText by remember { mutableStateOf("") }
    val isGenerating by functionGemmaEngine.isGenerating.collectAsState()
    val isModelLoaded by functionGemmaEngine.isModelLoaded.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Harm Detector & Tool Calling",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "On-Device via FunctionGemma (MediaPipe API)",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Model Loaded Status Badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isModelLoaded) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFFD600).copy(alpha = 0.15f),
            border = BorderStroke(1.dp, if (isModelLoaded) Color(0xFF00E676) else Color(0xFFFFD600))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
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

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Type emergency/danger prompt to test tool selection…") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(12.dp),
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (inputText.isNotBlank() && !isGenerating) {
                    scope.launch {
                        val (json, result) = functionGemmaEngine.generateToolCall(inputText)
                        rawJsonResponse = json
                        toolResultText = when (result) {
                            is ToolInvocationResult.Emergency -> """
                                🚨 EMERGENCY TOOL ACTIVATED
                                📍 Location: ${result.location}
                                📝 Context: "${result.contextSummary}"
                                ℹ️ ${result.message}
                            """.trimIndent()
                            is ToolInvocationResult.GeneralTool -> "🛠️ Tool Call Invoked: ${result.name}"
                            ToolInvocationResult.NoTool -> "✅ Safe Prompt - No Tool Invocation Required"
                        }
                    }
                }
            },
            enabled = inputText.isNotBlank() && !isGenerating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Analyzing Intent…")
            } else {
                Text("Check Intent & Execute Tool")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (toolResultText.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Intent Analysis Result:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = toolResultText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "FunctionGemma Raw Schema Output:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = rawJsonResponse,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Color(0xFF00E5FF),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
