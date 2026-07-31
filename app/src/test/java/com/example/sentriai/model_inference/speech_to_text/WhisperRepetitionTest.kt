package com.example.sentriai.model_inference.speech_to_text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against Whisper's repetition hallucination.
 *
 * Greedy decoding with no repetition penalty collapses into a cycle when the audio holds no
 * clear speech. On a real 15 s segment of room noise it produced "Hello." 112 times, ran to the
 * 224-token cap, and that transcript reached the emergency classifier — which labelled it a
 * medical emergency and sent a live SMS to a caregiver.
 */
class WhisperRepetitionTest {

    /** "Hello" then "." — the exact pair from the log that caused the false alert. */
    private val hello = listOf(18435, 13)

    @Test
    fun `detects the loop from the real failure`() {
        val tokens = List(56) { hello[it % 2] }
        assertTrue(WhisperModel.isLooping(tokens))
    }

    @Test
    fun `detects a single repeated token`() {
        assertTrue(WhisperModel.isLooping(listOf(5, 5, 5, 5)))
    }

    @Test
    fun `three repeats are not yet a loop`() {
        // Emphasis, not a glitch — a distressed person really does repeat themselves.
        assertFalse(WhisperModel.isLooping(listOf(7, 8, 7, 8, 7, 8)))
    }

    @Test
    fun `ordinary speech is not a loop`() {
        assertFalse(WhisperModel.isLooping(listOf(40, 41, 42, 43, 44, 45, 46, 47, 40, 41)))
    }

    @Test
    fun `a repeated cycle is trimmed to two copies`() {
        val trimmed = WhisperModel.trimRepetition(List(56) { hello[it % 2] })
        assertEquals(hello + hello, trimmed)
    }

    @Test
    fun `trimming keeps the words that came before the loop`() {
        val prefix = listOf(100, 101, 102)
        val trimmed = WhisperModel.trimRepetition(prefix + List(20) { hello[it % 2] })
        assertEquals(prefix + hello + hello, trimmed)
    }

    @Test
    fun `a genuine repeated cry survives as intelligible text`() {
        // "help me" six times must not be discarded — it is the emergency, not a glitch.
        val helpMe = listOf(2842, 385)
        val trimmed = WhisperModel.trimRepetition(List(12) { helpMe[it % 2] })
        assertEquals(helpMe + helpMe, trimmed)
    }

    @Test
    fun `non-repeating output is left alone`() {
        val tokens = listOf(10, 11, 12, 13, 14, 15, 16, 17)
        assertEquals(tokens, WhisperModel.trimRepetition(tokens))
    }

    @Test
    fun `short output is left alone`() {
        val tokens = listOf(18435)
        assertEquals(tokens, WhisperModel.trimRepetition(tokens))
    }
}
