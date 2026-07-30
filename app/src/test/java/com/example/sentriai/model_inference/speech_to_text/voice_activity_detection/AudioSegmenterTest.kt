package com.example.sentriai.model_inference.speech_to_text.voice_activity_detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the segmentation state machine.
 *
 * [AudioSegmenter] has no Android runtime dependency — the only thing it takes from
 * `AudioRecorder` is a `const val`, which the compiler inlines — so all of this runs without a
 * device. That matters because the parts of the listening pipeline that *do* need a device
 * (background mic access) can't be covered here at all.
 *
 * "Speech" is synthesised as samples loud enough to clear the detector's energy floor, and
 * silence as true zeros. The detector is energy-based, so that is a faithful stimulus.
 */
class AudioSegmenterTest {

    private val frame = VoiceActivityDetector.FRAME_SAMPLES

    private fun silence(frames: Int) = FloatArray(frames * frame)

    /** Alternating +/- amplitude gives a predictable RMS well above the absolute floor. */
    private fun speech(frames: Int, amplitude: Float = 0.2f) =
        FloatArray(frames * frame) { if (it % 2 == 0) amplitude else -amplitude }

    @Test
    fun `silence alone never opens a segment`() {
        val segmenter = AudioSegmenter()
        assertTrue(segmenter.offer(silence(200)).isEmpty())
        assertNull(segmenter.flush())
    }

    @Test
    fun `one utterance followed by silence yields exactly one segment`() {
        val segmenter = AudioSegmenter()
        segmenter.offer(silence(20))
        segmenter.offer(speech(40)) // 1.2 s of speech
        val segments = segmenter.offer(silence(40)) // past the hangover

        assertEquals(1, segments.size)
        assertEquals(AudioSegmenter.Reason.HANGOVER, segments[0].reason)
    }

    @Test
    fun `a short blip is rejected as noise, not transcribed as an utterance`() {
        val segmenter = AudioSegmenter()
        segmenter.offer(silence(20))
        // 4 frames = 120 ms: enough to clear ENTER_FRAMES and open a segment, but under the
        // minimum speech duration. This is the door-slam case.
        segmenter.offer(speech(4))
        val segments = segmenter.offer(silence(40))

        assertTrue("a 120 ms blip must not reach the model", segments.isEmpty())
    }

    @Test
    fun `pre-roll makes the segment start before detected speech onset`() {
        val segmenter = AudioSegmenter()
        segmenter.offer(silence(20))
        segmenter.offer(speech(20))
        val segments = segmenter.offer(silence(40))

        assertEquals(1, segments.size)
        // 20 frames of speech; the segment must be materially longer because it carries
        // pre-roll ahead of onset and hangover behind it. Without pre-roll the first phoneme
        // would be clipped.
        assertTrue(
            "expected pre-roll + hangover padding, got ${segments[0].samples.size} samples",
            segments[0].samples.size > 20 * frame,
        )
    }

    @Test
    fun `continuous speech is cut before the model's 30 s window`() {
        val segmenter = AudioSegmenter()
        segmenter.offer(silence(20))
        // 20 s with no pause at all — the hangover will never fire, so only the hard cap can
        // close this.
        val segments = segmenter.offer(speech(20 * 1000 / 30))

        assertTrue("continuous speech must still produce output", segments.isNotEmpty())
        assertEquals(AudioSegmenter.Reason.MAX_DURATION, segments[0].reason)
        val thirtySeconds = 30 * 16_000
        segments.forEach {
            assertTrue(
                "segment of ${it.durationSeconds}s would be truncated by the model",
                it.samples.size < thirtySeconds,
            )
        }
    }

    @Test
    fun `flush emits the utterance still open so a mid-sentence stop keeps its tail`() {
        val segmenter = AudioSegmenter()
        segmenter.offer(silence(20))
        // Speech that never sees its trailing silence — the user hit Stop mid-sentence.
        assertTrue(segmenter.offer(speech(30)).isEmpty())

        val tail = segmenter.flush()
        assertNotNull("the in-flight utterance must survive a stop", tail)
        assertEquals(AudioSegmenter.Reason.FLUSH, tail!!.reason)
    }

    @Test
    fun `samples split across offers are not lost at the boundary`() {
        val segmenter = AudioSegmenter()
        segmenter.offer(silence(20))
        // Deliberately not frame-aligned: the segmenter must carry the remainder across calls.
        val burst = speech(30)
        var at = 0
        val chunk = frame + 137
        while (at < burst.size) {
            val end = minOf(at + chunk, burst.size)
            segmenter.offer(burst.copyOfRange(at, end))
            at = end
        }
        val segments = segmenter.offer(silence(40))

        assertEquals(1, segments.size)
        // Frame-aligned output, and long enough that no whole frame went missing.
        assertTrue(segments[0].samples.size >= 30 * frame)
    }

    @Test
    fun `reset clears an in-flight utterance`() {
        val segmenter = AudioSegmenter()
        segmenter.offer(silence(20))
        segmenter.offer(speech(30))
        segmenter.reset()

        assertNull("reset must drop the open segment", segmenter.flush())
    }
}
