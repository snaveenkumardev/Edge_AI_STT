package com.edgeai.stt.vitals

/**
 * Data class representing real-time system performance and AI telemetry.
 */
data class VitalsData(
    val tokenSpeedTokPerSec: Double = 0.0,
    val timeToFirstTokenMs: Long = 0,
    val totalGenerationTimeMs: Long = 0,
    val sttLatencyMs: Long = 0,
    val batteryLevelPct: Int = 100,
    val batteryTemperatureC: Float = 0f,
    val batteryCurrentmA: Int = 0,
    val isCharging: Boolean = false,
    val usedRamMb: Long = 0,
    val totalRamMb: Long = 0,
    val nativeHeapMb: Long = 0,
    val cpuUsagePct: Float = 0f,
    val isThermalThrottling: Boolean = false,
    val modelHardwareMode: String = "CPU"
)
