package com.edgeai.stt.vitals

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VitalsMonitor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val _vitals = MutableStateFlow(VitalsData())
    val vitals: StateFlow<VitalsData> = _vitals.asStateFlow()

    private var monitorJob: Job? = null

    fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (true) {
                sampleHardwareVitals()
                delay(500) // Sample hardware metrics every 500ms
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun updateAILatency(ttftMs: Long, totalGenMs: Long, tokensGenerated: Int) {
        val tokPerSec = if (totalGenMs > 0) (tokensGenerated * 1000.0) / totalGenMs else 0.0
        _vitals.update { current ->
            current.copy(
                tokenSpeedTokPerSec = Math.round(tokPerSec * 10.0) / 10.0,
                timeToFirstTokenMs = ttftMs,
                totalGenerationTimeMs = totalGenMs
            )
        }
    }

    fun updateSTTLatency(latencyMs: Long) {
        _vitals.update { current ->
            current.copy(sttLatencyMs = latencyMs)
        }
    }

    fun updateModelHardwareMode(mode: String) {
        _vitals.update { current ->
            current.copy(modelHardwareMode = mode)
        }
    }

    private fun sampleHardwareVitals() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 100
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10.0f

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val currentMicroAmps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val currentMilliAmps = Math.abs(currentMicroAmps / 1000)

        // Memory Usage
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()
        val usedRamBytes = runtime.totalMemory() - runtime.freeMemory()
        val usedRamMb = usedRamBytes / (1024 * 1024)
        val totalRamMb = (memoryInfo.totalMem) / (1024 * 1024)
        val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

        // Thermal Throttling
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isThermalThrottling = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus != PowerManager.THERMAL_STATUS_NONE
        } else {
            false
        }

        _vitals.update { current ->
            current.copy(
                batteryLevelPct = batteryPct,
                batteryTemperatureC = tempC,
                batteryCurrentmA = currentMilliAmps,
                isCharging = isCharging,
                usedRamMb = usedRamMb,
                totalRamMb = totalRamMb,
                nativeHeapMb = nativeHeapMb,
                isThermalThrottling = isThermalThrottling
            )
        }
    }
}
