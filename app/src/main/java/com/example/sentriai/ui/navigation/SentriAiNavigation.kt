package com.example.sentriai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.sentriai.data.ProfileStore
import com.example.sentriai.model_inference.speech_to_text.TranscriptionViewModel
import com.example.sentriai.ui.screens.AiAssistantActivateScreen
import com.example.sentriai.ui.screens.ProfileSettingsScreen
import com.example.sentriai.ui.screens.VoiceTranscriptScreen

/** Destinations reachable from the nav host. */
object Routes {
    const val PROFILE = "profile"
    const val AI_ASSISTANT = "ai_assistant"
    const val VOICE_TRANSCRIPT = "voice_transcript"
}

/**
 * App-level navigation graph. First-run users start on the profile form and are moved
 * to the Guardian AI activation screen once they save; anyone with a completed profile
 * already stored lands on the activation screen directly. The profile chip in that
 * screen's top bar reopens the form for edits.
 */
@Composable
fun SentriAiNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    // Hoisted above the NavHost so it is scoped to the activity rather than to a single
    // back stack entry: the activation and transcript screens drive the same stream, and
    // the loaded Whisper model (~200 MB) survives navigating between them.
    val transcriptionViewModel: TranscriptionViewModel = viewModel()
    // Resolved once per host: NavHost only reads startDestination when it builds the
    // graph, and re-reading prefs on recomposition would have no effect anyway.
    val startDestination = remember {
        if (ProfileStore.isProfileComplete(context)) Routes.AI_ASSISTANT else Routes.PROFILE
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.PROFILE) {
            ProfileSettingsScreen(
                onProfileSaved = {
                    navController.navigate(Routes.AI_ASSISTANT) {
                        // The form is a one-time setup step — don't leave it on the back stack.
                        popUpTo(Routes.PROFILE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.AI_ASSISTANT) {
            AiAssistantActivateScreen(
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                onTranscriptionStarted = {
                    navController.navigate(Routes.VOICE_TRANSCRIPT) { launchSingleTop = true }
                },
                viewModel = transcriptionViewModel,
            )
        }

        composable(Routes.VOICE_TRANSCRIPT) {
            VoiceTranscriptScreen(
                // Back leaves the stream running — the assistant stays armed and the
                // activation screen keeps showing it as active.
                onBack = { navController.popBackStack() },
                viewModel = transcriptionViewModel,
            )
        }
    }
}
