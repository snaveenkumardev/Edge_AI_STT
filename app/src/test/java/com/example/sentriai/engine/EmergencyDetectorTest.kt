package com.example.sentriai.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Covers the safe-word trigger — the only detector that runs without a model, and therefore the
 * only part of detection testable off-device.
 *
 * The classifier is exercised by `EmergencyDetectorDeviceTest` in `androidTest`, which needs a
 * real device and the `.task` bundle.
 */
class EmergencyDetectorTest {

    @Before
    fun reset() = EmergencyDetector.resetPhraseStreak()

    @Test
    fun `safe word fires only on the third utterance`() {
        assertNull(EmergencyDetector.checkPhraseTrigger("Mimi"))
        assertNull(EmergencyDetector.checkPhraseTrigger("Mimi"))

        val fired = EmergencyDetector.checkPhraseTrigger("Mimi")
        assertEquals(EmergencyType.PHRASE, fired?.emergencyType)
        assertEquals(EmergencyDecision.Source.PHRASE_TRIGGER, fired?.source)
    }

    @Test
    fun `safe word repeated within one utterance fires immediately`() {
        assertNotNull(EmergencyDetector.checkPhraseTrigger("Mimi Mimi Mimi"))
    }

    @Test
    fun `whisper vowel confusions still count toward the streak`() {
        assertNull(EmergencyDetector.checkPhraseTrigger("Mime"))
        assertNull(EmergencyDetector.checkPhraseTrigger("Memi"))
        assertNotNull(EmergencyDetector.checkPhraseTrigger("Meme"))
    }

    @Test
    fun `an unrelated utterance resets the streak`() {
        EmergencyDetector.checkPhraseTrigger("Mimi")
        EmergencyDetector.checkPhraseTrigger("Mimi")
        assertNull(EmergencyDetector.checkPhraseTrigger("what time is it"))

        // Streak restarted, so the next two must not be enough on their own.
        assertNull(EmergencyDetector.checkPhraseTrigger("Mimi"))
        assertNull(EmergencyDetector.checkPhraseTrigger("Mimi"))
        assertNotNull(EmergencyDetector.checkPhraseTrigger("Mimi"))
    }

    @Test
    fun `similar words are not the safe word`() {
        repeat(4) { assertNull(EmergencyDetector.checkPhraseTrigger("Mama")) }
        repeat(4) { assertNull(EmergencyDetector.checkPhraseTrigger("Momo")) }
    }

    @Test
    fun `a bare cry for help fires`() {
        // The classifier answers NO to a one-word "Help" — measured on device — so this is the
        // only thing standing between that utterance and silence.
        listOf("Help", "help", "Help!", "help me", "somebody help me please", "please help")
            .forEach {
                val decision = EmergencyDetector.explicitPlea(it)
                assertEquals(it, EmergencyType.HELP, decision?.emergencyType)
                assertEquals(EmergencyDecision.Source.EXPLICIT_PLEA, decision?.source)
            }
    }

    @Test
    fun `other ways of summoning help fire`() {
        listOf("call an ambulance", "call 911", "call 999", "it is an emergency", "SOS")
            .forEach { assertNotNull(it, EmergencyDetector.explicitPlea(it)) }
    }

    @Test
    fun `words containing help are not a plea`() {
        // The old keyword scan fired on all of these. Word boundaries are what fixes it.
        listOf(
            "that was really helpful, thank you",
            "she helped me with the shopping yesterday",
            "he is always helping out at the church",
            "the helpline number is on the fridge",
        ).forEach { assertNull(it, EmergencyDetector.explicitPlea(it)) }
    }

    @Test
    fun `ordinary speech is not a plea`() {
        listOf(
            "I am making myself a cup of tea",
            "my grandson is coming to visit at the weekend",
            "she fell about laughing when I told her that story",
            "Hello.",
        ).forEach { assertNull(it, EmergencyDetector.explicitPlea(it)) }
    }

    @Test
    fun `ordinary speech never fires the phrase trigger`() {
        // Detection of unprompted speech belongs entirely to the classifier now, so nothing
        // here may produce a decision on its own.
        listOf(
            "I fell in the kitchen",
            "somebody help me",
            "my chest pain is getting worse",
            "hello",
        ).forEach { assertNull(it, EmergencyDetector.checkPhraseTrigger(it)) }
    }
}
