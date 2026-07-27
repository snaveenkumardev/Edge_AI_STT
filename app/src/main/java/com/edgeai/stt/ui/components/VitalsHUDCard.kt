package com.edgeai.stt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeai.stt.vitals.VitalsData

@Composable
fun VitalsHUDCard(vitals: VitalsData, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.9f))
            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Token Speed Metric
            VitalsHUDMetricItem(
                label = "TOKEN SPEED",
                value = "${vitals.tokenSpeedTokPerSec}",
                unit = "tok/s",
                color = Color(0xFF00E5FF)
            )

            // TTFT Latency Metric
            VitalsHUDMetricItem(
                label = "TTFT LATENCY",
                value = "${vitals.timeToFirstTokenMs}",
                unit = "ms",
                color = Color(0xFF00C853)
            )

            // Battery Draw Metric
            VitalsHUDMetricItem(
                label = "BATTERY",
                value = "${vitals.batteryLevelPct}%",
                unit = "${vitals.batteryTemperatureC}°C",
                color = if (vitals.batteryLevelPct < 20) Color(0xFFFF1744) else Color(0xFFFFC107)
            )

            // Model Execution Mode Metric
            VitalsHUDMetricItem(
                label = "EXEC MODE",
                value = vitals.modelHardwareMode,
                unit = "",
                color = if (vitals.modelHardwareMode.contains("GPU")) Color(0xFFFF4081) else Color(0xFFFFC107)
            )

            // RAM Usage Metric
            VitalsHUDMetricItem(
                label = "RAM USED",
                value = "${vitals.usedRamMb}",
                unit = "MB",
                color = Color(0xFFB388FF)
            )
        }
    }
}

@Composable
private fun VitalsHUDMetricItem(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = " $unit",
                fontSize = 10.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }
    }
}
