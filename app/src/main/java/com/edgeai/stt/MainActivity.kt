package com.edgeai.stt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.edgeai.stt.ai.FunctionGemmaMediaPipeEngine
import com.edgeai.stt.ai.SherpaOnnxSTTEngine
import com.edgeai.stt.audio.AudioStreamRecorder
import com.edgeai.stt.location.LocationHelper
import com.edgeai.stt.ui.components.VitalsHUDCard
import com.edgeai.stt.ui.screens.HarmDetectorScreen
import com.edgeai.stt.ui.screens.VitalsDashboardScreen
import com.edgeai.stt.ui.screens.VoiceDetectorScreen
import com.edgeai.stt.ui.theme.EdgeAISTTTheme
import com.edgeai.stt.vitals.VitalsMonitor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var vitalsMonitor: VitalsMonitor
    private lateinit var recorder: AudioStreamRecorder
    private lateinit var locationHelper: LocationHelper
    private lateinit var sttEngine: SherpaOnnxSTTEngine
    private lateinit var functionGemmaEngine: FunctionGemmaMediaPipeEngine

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vitalsMonitor = VitalsMonitor(applicationContext)
        vitalsMonitor.startMonitoring()

        recorder = AudioStreamRecorder(applicationContext)
        locationHelper = LocationHelper(applicationContext)
        sttEngine = SherpaOnnxSTTEngine(applicationContext, vitalsMonitor)
        functionGemmaEngine = FunctionGemmaMediaPipeEngine(applicationContext, vitalsMonitor, locationHelper)

        requestMultiplePermissionsLauncher.launch(
            arrayOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            EdgeAISTTTheme {
                val scope = rememberCoroutineScope()
                remember {
                    scope.launch {
                        functionGemmaEngine.initialize()
                        sttEngine.initialize()
                    }
                }


                MainScreenLayout(
                    vitalsMonitor = vitalsMonitor,
                    recorder = recorder,
                    sttEngine = sttEngine,
                    functionGemmaEngine = functionGemmaEngine
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vitalsMonitor.stopMonitoring()
        recorder.stopRecording()
    }
}

sealed class Screen(val route: String, val title: String, val iconText: String) {
    object VoiceDetector : Screen("voice_detector", "Voice Detector", "🎙️")
    object HarmDetector : Screen("harm_detector", "Harm Detector", "🛡️")
    object VitalsDashboard : Screen("vitals_dashboard", "AI Vitals", "⚡")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenLayout(
    vitalsMonitor: VitalsMonitor,
    recorder: AudioStreamRecorder,
    sttEngine: SherpaOnnxSTTEngine,
    functionGemmaEngine: FunctionGemmaMediaPipeEngine
) {
    val navController = rememberNavController()
    var isHudVisible by remember { mutableStateOf(true) }
    val vitals by vitalsMonitor.vitals.collectAsState()

    val screens = listOf(
        Screen.VoiceDetector,
        Screen.HarmDetector,
        Screen.VitalsDashboard
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Edge AI STT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    actions = {
                        Text(
                            text = if (isHudVisible) "HUD ON " else "HUD OFF ",
                            fontSize = 12.sp,
                            color = if (isHudVisible) Color(0xFF00E5FF) else Color.Gray
                        )
                        IconButton(onClick = { isHudVisible = !isHudVisible }) {
                            Text(text = if (isHudVisible) "📊" else "📈", fontSize = 18.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                if (isHudVisible) {
                    VitalsHUDCard(vitals = vitals)
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                screens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Text(screen.iconText, fontSize = 20.sp) },
                        label = { Text(screen.title, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.VoiceDetector.route
            ) {
                composable(Screen.VoiceDetector.route) {
                    VoiceDetectorScreen(
                        recorder = recorder,
                        sttEngine = sttEngine,
                        functionGemmaEngine = functionGemmaEngine
                    )
                }
                composable(Screen.HarmDetector.route) {
                    HarmDetectorScreen(
                        functionGemmaEngine = functionGemmaEngine
                    )
                }
                composable(Screen.VitalsDashboard.route) {
                    VitalsDashboardScreen(
                        vitalsMonitor = vitalsMonitor
                    )
                }
            }
        }
    }
}
