package com.example.sentriai.engine

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real classifier on a real device. Gemma is now the only thing judging
 * unprompted speech, so these are the only tests that cover that path at all — the JVM tests
 * can reach the safe word and nothing else.
 *
 * Recall and false alarms are measured separately, and both are reported as a rate rather than
 * failing on the first bad case: the rate is what decides whether this is fit to ship.
 *
 * Requires the classifier `.task` on the device. Run with:
 * `./gradlew connectedDebugAndroidTest --tests '*EmergencyDetectorDeviceTest*'`
 */
@RunWith(AndroidJUnit4::class)
class EmergencyDetectorDeviceTest {

    companion object {
        private const val TAG = "DetectorDeviceTest"

        @BeforeClass
        @JvmStatic
        fun loadModel() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val ready = runBlocking { EmergencyDetector.initialize(context) }
            assertTrue(
                "Classifier model is not on this device — ${EmergencyDetector.lastError}",
                ready,
            )
        }
    }

    /** Unambiguous emergencies stated plainly. Anything that misses these is broken. */
    private val obviousEmergencies = listOf(
        "I slipped getting out of the bath and I can't get up",
        "my chest is really tight and I can't breathe",
        "somebody help me please",
        // One word, no sentence. The classifier answers NO to this; the plea check is what
        // catches it, and a device that misses it is worthless.
        "Help",
        "Help me",
        "call an ambulance",
    )

    /**
     * Emergencies described without any of the obvious words — no "fell", "help me", "chest
     * pain". These are the cases that justify running a language model at all, since no amount
     * of string matching reaches them.
     */
    private val impliedEmergencies = listOf(
        "I have been lying on the floor since this morning and nobody has come",
        "there is a crushing pressure spreading down my left arm and into my jaw",
        "my legs gave way in the hallway and I cannot reach the phone",
    )

    /**
     * Everyday speech, none of it repeated from the few-shot examples in the prompt — a query
     * that duplicates an example verbatim is not a fair read on the model, and one of these
     * used to be exactly that.
     *
     * Several are near-misses on purpose: they mention the body, medicine or falling without
     * describing an emergency, which is where a jumpy classifier gets a caregiver texted at
     * three in the morning.
     */
    private val ordinarySpeech = listOf(
        "I am making myself a cup of tea",
        "that was really helpful, thank you",
        "my heart is set on going to the fair on Saturday",
        "the weather forecast says it will rain tomorrow afternoon",
        "I took my tablets after breakfast like the doctor said",
        "she fell about laughing when I told her that story",
        "my grandson is coming to visit at the weekend",
        "I think I will have an early night, I am tired",
        // Every one of these fired a real SMS on device. A near-empty transcript is what a
        // monitoring mic hears most of the day, so these matter more than their length suggests.
        "Hello.",
        "Hello",
        "Hello, hello, hello.",
        "And",
        "Could you turn the television down a bit",
    )

    @Test
    fun classifierCatchesRealEmergencies() = runBlocking {
        val cases = obviousEmergencies + impliedEmergencies
        val missed = mutableListOf<String>()

        for (phrase in cases) {
            val decision = EmergencyDetector.detect(phrase)
            Log.i(
                TAG,
                "EMERGENCY-CASE [${decision?.source?.name ?: "MISSED"}] " +
                    "${decision?.emergencyType ?: "-"} <- \"$phrase\"",
            )
            if (decision == null) missed += phrase
        }

        Log.i(TAG, "EMERGENCY-CASE misses: ${missed.size}/${cases.size}")
        assertTrue(
            "Missed ${missed.size} of ${cases.size} emergencies — each one is a call for help " +
                "that never reaches anybody:\n" + missed.joinToString("\n"),
            missed.isEmpty(),
        )
    }

    @Test
    fun ordinarySpeechDoesNotFire() = runBlocking {
        // Every phrase is scored before asserting. Failing on the first false alarm hides the
        // rate, and the rate is the number that decides whether this is shippable.
        val falseAlarms = mutableListOf<String>()

        for (phrase in ordinarySpeech) {
            val decision = EmergencyDetector.detect(phrase)
            Log.i(TAG, "ORDINARY-CASE [${decision?.source?.name ?: "none"}] <- \"$phrase\"")
            if (decision != null) {
                falseAlarms += "\"$phrase\" -> ${decision.emergencyType} via ${decision.source}"
            }
        }

        Log.i(TAG, "ORDINARY-CASE false alarms: ${falseAlarms.size}/${ordinarySpeech.size}")
        assertTrue(
            "False alarms on ${falseAlarms.size} of ${ordinarySpeech.size} ordinary phrases — " +
                "each one texts a caregiver:\n" + falseAlarms.joinToString("\n"),
            falseAlarms.isEmpty(),
        )
    }
}
