package com.edgeai.stt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeai.stt.vitals.VitalsData
import com.edgeai.stt.vitals.VitalsMonitor

@Composable
fun VitalsDashboardScreen(
    vitalsMonitor: VitalsMonitor,
    modifier: Modifier = Modifier
) {
    val vitals by vitalsMonitor.vitals.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Edge AI Performance Vitals",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Real-time Telemetry & Hardware Throughput",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // Section 1: AI Speed & Latency Breakdown
        item {
            VitalsSectionTitle("⚡ AI Inference & Latency Metrics")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VitalsDetailCard(
                    title = "Token Speed",
                    value = "${vitals.tokenSpeedTokPerSec}",
                    unit = "tok/s",
                    subtitle = "LLM Generation Rate",
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
                VitalsDetailCard(
                    title = "Time-To-First-Token",
                    value = "${vitals.timeToFirstTokenMs}",
                    unit = "ms",
                    subtitle = "Initial TTFT Latency",
                    color = Color(0xFF00C853),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VitalsDetailCard(
                    title = "Total LLM Gen Time",
                    value = "${vitals.totalGenerationTimeMs}",
                    unit = "ms",
                    subtitle = "Full Output Latency",
                    color = Color(0xFF2979FF),
                    modifier = Modifier.weight(1f)
                )
                VitalsDetailCard(
                    title = "STT Audio Latency",
                    value = "${vitals.sttLatencyMs}",
                    unit = "ms",
                    subtitle = "Whisper Buffer Latency",
                    color = Color(0xFFB388FF),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VitalsDetailCard(
                    title = "Model Execution Mode",
                    value = vitals.modelHardwareMode,
                    unit = "",
                    subtitle = "Active Inference Unit",
                    color = if (vitals.modelHardwareMode.contains("GPU")) Color(0xFFFF4081) else Color(0xFFFFC107),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Section 2: Battery & Power Consumption
        item {
            VitalsSectionTitle("🔋 Battery & Power Consumption")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Battery Charge Level", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${vitals.batteryLevelPct}% ${if (vitals.isCharging) "⚡ (Charging)" else ""}",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (vitals.batteryLevelPct < 20) Color(0xFFFF1744) else Color(0xFF00C853)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { vitals.batteryLevelPct / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (vitals.batteryLevelPct < 20) Color(0xFFFF1744) else Color(0xFF00C853),
                        trackColor = Color.Gray.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Battery Temp", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                "${vitals.batteryTemperatureC}°C",
                                fontWeight = FontWeight.Bold,
                                color = if (vitals.batteryTemperatureC > 40f) Color(0xFFFF1744) else Color.Unspecified
                            )
                        }
                        Column {
                            Text("Current Drain Rate", fontSize = 11.sp, color = Color.Gray)
                            Text("${vitals.batteryCurrentmA} mA", fontWeight = FontWeight.Bold, color = Color(0xFFFFC107))
                        }
                        Column {
                            Text("Thermal State", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                if (vitals.isThermalThrottling) "⚠️ Throttled" else "🟢 Normal",
                                fontWeight = FontWeight.Bold,
                                color = if (vitals.isThermalThrottling) Color(0xFFFF1744) else Color(0xFF00C853)
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Memory Footprint & System Throughput
        item {
            VitalsSectionTitle("💾 Memory & System Throughput")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val ramPct = if (vitals.totalRamMb > 0) (vitals.usedRamMb.toFloat() / vitals.totalRamMb.toFloat()) else 0f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("App RAM Footprint", fontWeight = FontWeight.Bold)
                        Text("${vitals.usedRamMb} MB / ${vitals.totalRamMb} MB", fontWeight = FontWeight.ExtraBold, color = Color(0xFFB388FF))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { ramPct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFB388FF),
                        trackColor = Color.Gray.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Native C++ Heap Allocation:", fontSize = 12.sp, color = Color.Gray)
                        Text("${vitals.nativeHeapMb} MB", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun VitalsSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun VitalsDetailCard(
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
                Text(" $unit", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.padding(bottom = 2.dp))
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 9.sp, color = Color.Gray)
        }
    }
}
