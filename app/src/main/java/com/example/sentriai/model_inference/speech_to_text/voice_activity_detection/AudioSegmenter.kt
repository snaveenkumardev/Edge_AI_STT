package com.example.sentriai.model_inference.speech_to_text.voice_activity_detection

import com.example.sentriai.model_inference.speech_to_text.AudioRecorder

/**
 * Turns a continuous PCM stream into complete spoken utterances.
 *
 * This is the half of VAD that actually matters. A raw per-frame speech/not-speech decision is
 * far too twitchy to cut audio on: it flickers on plosives and on the gaps between words. The
 * state machine here adds the three things that make it usable:
 *
 * - **Hysteresis.** Opening a segment needs [ENTER_FRAMES] consecutive speech frames, so
 *   clicks and taps don't start one.
 * - **Hangover.** Closing one needs [HANGOVER_MS] of continuous silence, so the natural pauses
 *   between words don't shatter a sentence into five segments.
 * - **Pre-roll.** Detection is inherently late — by the time [ENTER_FRAMES] frames have
 *   confirmed speech, its first phoneme is already behind us. The last [PRE_ROLL_MS] is kept
 *   buffered at all times and prepended to every segment, so "help me" doesn't arrive as
 *   "elp me".
 *
 * Replaces a fixed-cadence design that re-transcribed a growing window every 5 s (~4 encoder
 * passes per 20 s of audio, whether or not anyone spoke) and cut at an arbitrary 20 s
 * boundary — splitting phrases in half so the emergency check never saw a whole sentence.
 *
 * Not thread-safe: drive it from a single consumer.
 */
class AudioSegmenter(
    private val vad: VoiceActivityDetector = VoiceActivityDetector(),
) {

    private enum class State { SILENCE, SPEECH }

    private var state = State.SILENCE

    /** Samples left over from the last [offer] that didn't fill a whole frame. */
    private var leftover = FloatArray(0)

    /** Rolling pre-roll window, maintained only while in [State.SILENCE]. */
    private val preRoll = ArrayDeque<FloatArray>()

    /** The utterance being accumulated while in [State.SPEECH]. */
    private var current = ArrayList<FloatArray>()
    private var currentSamples = 0

    /**
     * Frames *classified as speech* within the current segment. The floor in [close] has to be
     * measured against this rather than the segment length: every segment carries ~350 ms of
     * pre-roll and ~700 ms of hangover, so its total length is always over a second and a
     * length-based floor would never reject anything.
     */
    private var currentSpeechFrames = 0

    private var speechRun = 0
    private var silenceRun = 0

    /** Why a segment closed — logged so segmentation can be tuned against real audio. */
    enum class Reason { HANGOVER, MAX_DURATION, FLUSH }

    class Segment(val samples: FloatArray, val reason: Reason) {
        val durationSeconds: Float get() = samples.size / SAMPLE_RATE_F
    }

    /**
     * Feed newly captured audio.
     *
     * @return every utterance completed by this batch, in order. Usually empty: a batch only
     *   yields a segment when it happens to contain the end of one.
     */
    fun offer(samples: FloatArray): List<Segment> {
        if (samples.isEmpty() && leftover.isEmpty()) return emptyList()

        val buffer = if (leftover.isEmpty()) samples else leftover + samples
        val frameCount = buffer.size / FRAME
        if (frameCount == 0) {
            leftover = buffer
            return emptyList()
        }

        val out = mutableListOf<Segment>()
        for (i in 0 until frameCount) {
            consume(buffer.copyOfRange(i * FRAME, (i + 1) * FRAME))?.let { out += it }
        }
        leftover = buffer.copyOfRange(frameCount * FRAME, buffer.size)
        return out
    }

    /**
     * End of stream: emit the utterance still in flight, so the tail isn't lost when the user
     * stops mid-sentence. A sub-frame remainder is dropped — at most 30 ms, not worth a pass.
     */
    fun flush(): Segment? {
        val tail = close(Reason.FLUSH)
        preRoll.clear()
        leftover = FloatArray(0)
        return tail
    }

    fun reset() {
        state = State.SILENCE
        preRoll.clear()
        current = ArrayList()
        currentSamples = 0
        currentSpeechFrames = 0
        speechRun = 0
        silenceRun = 0
        leftover = FloatArray(0)
        vad.reset()
    }

    // --- state machine ---------------------------------------------------------

    private fun consume(frame: FloatArray): Segment? {
        val speech = vad.isSpeech(frame)

        when (state) {
            State.SILENCE -> {
                preRoll.addLast(frame)
                while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()

                if (!speech) {
                    speechRun = 0
                    return null
                }
                speechRun++
                if (speechRun < ENTER_FRAMES) return null

                // Open a segment seeded with the pre-roll, which already holds the frames that
                // just confirmed speech plus the audio immediately before them.
                state = State.SPEECH
                current = ArrayList(preRoll)
                currentSamples = current.sumOf { it.size }
                // The ENTER_FRAMES that opened this segment were speech; they are in the
                // pre-roll and must count toward the floor.
                currentSpeechFrames = ENTER_FRAMES
                preRoll.clear()
                speechRun = 0
                silenceRun = 0
            }

            State.SPEECH -> {
                current.add(frame)
                currentSamples += frame.size
                if (speech) currentSpeechFrames++
                silenceRun = if (speech) 0 else silenceRun + 1

                if (silenceRun >= HANGOVER_FRAMES) return close(Reason.HANGOVER)

                // Someone talking continuously would otherwise run past the model's 30 s
                // ceiling; cut early and keep going rather than dropping audio.
                if (currentSamples >= MAX_SEGMENT_SAMPLES) return close(Reason.MAX_DURATION)
            }
        }
        return null
    }

    /** Close the in-flight utterance, returning it unless it holds too little actual speech. */
    private fun close(reason: Reason): Segment? {
        state = State.SILENCE
        silenceRun = 0
        speechRun = 0
        if (current.isEmpty()) return null

        val samples = flatten(current, currentSamples)
        val speechFrames = currentSpeechFrames
        current = ArrayList()
        currentSamples = 0
        currentSpeechFrames = 0
        // Rejects a door slam or a tap: enough energy to open a segment, not enough sustained
        // speech to be an utterance. Without this every blip costs a full encoder pass.
        return if (speechFrames >= MIN_SPEECH_FRAMES) Segment(samples, reason) else null
    }

    private fun flatten(frames: List<FloatArray>, total: Int): FloatArray {
        val out = FloatArray(total)
        var at = 0
        for (f in frames) {
            System.arraycopy(f, 0, out, at, f.size)
            at += f.size
        }
        return out
    }

    companion object {
        private const val FRAME = VoiceActivityDetector.FRAME_SAMPLES
        private const val SAMPLE_RATE = AudioRecorder.SAMPLE_RATE
        private const val SAMPLE_RATE_F = SAMPLE_RATE.toFloat()

        private const val MS_PER_FRAME = FRAME * 1000 / SAMPLE_RATE

        /** ~90 ms of confirmation before a segment opens. */
        private const val ENTER_FRAMES = 3

        /** Silence that ends an utterance. Long enough to span a mid-sentence breath. */
        private const val HANGOVER_MS = 700
        private const val HANGOVER_FRAMES = HANGOVER_MS / MS_PER_FRAME

        /** Audio kept from before speech onset so the first phoneme survives. */
        private const val PRE_ROLL_MS = 350
        private const val PRE_ROLL_FRAMES = PRE_ROLL_MS / MS_PER_FRAME

        /**
         * Minimum *speech* (not segment) duration for an utterance to be worth transcribing.
         * Kept short deliberately: "help" is about 300 ms, and missing it costs far more than
         * an occasional wasted pass.
         */
        private const val MIN_SPEECH_MS = 240
        private const val MIN_SPEECH_FRAMES = MIN_SPEECH_MS / MS_PER_FRAME

        /** Hard cut, comfortably under the model's fixed 30 s window. */
        private const val MAX_SEGMENT_MS = 15_000
        private const val MAX_SEGMENT_SAMPLES = MAX_SEGMENT_MS * SAMPLE_RATE / 1000
    }
}
