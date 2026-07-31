package com.example.sentriai.model_inference.speech_to_text

import android.content.Context
import android.util.Log
import com.example.sentriai.models.ModelAsset
import com.example.sentriai.models.ModelCatalog
import com.example.sentriai.models.ModelStore
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File

/**
 * On-device Whisper (tiny.en) transcription through the generic ExecuTorch runtime.
 *
 * Pipeline: raw 16 kHz mono PCM -> log-mel spectrogram (preprocessor.pte) ->
 * encoder -> autoregressive greedy decode (text_decoder) -> [WhisperTokenizer] decode.
 *
 * The method names and tensor shapes below were verified by introspecting the exported
 * `model.pte` (openai/whisper-tiny.en, --recipe xnnpack --qlinear 8da4w):
 *   encoder(mel[1,80,3000] f32) -> hidden[1,1500,384] f32
 *   text_decoder(input_ids[1,1] i64, hidden[1,1500,384] f32, cache_position[1] i64)
 *       -> logits[1,1,51864] f32   (stateful KV-cache in the module's mutable buffers)
 */
class WhisperModel private constructor(
    private val preprocessor: Module,
    private val model: Module,
    private val tokenizer: WhisperTokenizer,
) {

    /** Transcribe a mono 16 kHz PCM buffer (float samples in [-1, 1]). */
    fun transcribe(pcm16k: FloatArray, maxNewTokens: Int = 224): String {
        Log.d(TAG, "transcribe: pcm samples=${pcm16k.size}")
        val mel = preprocess(pcm16k)
        val encoderOut = encode(mel)
        val generated = greedyDecode(encoderOut, maxNewTokens)
        val text = tokenizer.decode(generated)
        Log.d(TAG, "transcribe: tokens=${generated.size} text='$text'")
        return text
    }

    // --- pipeline stages -------------------------------------------------------

    /**
     * Run the mel-spectrogram preprocessor. Whisper wants a fixed 30 s window, so the
     * audio is padded/trimmed to [SAMPLES_30S] before it goes in. The preprocessor's
     * single output feeds straight into the encoder as a [1, 80, 3000] mel tensor.
     */
    private fun preprocess(pcm16k: FloatArray): Tensor {
        val padded = FloatArray(SAMPLES_30S)
        System.arraycopy(pcm16k, 0, padded, 0, minOf(pcm16k.size, SAMPLES_30S))
        // The preprocessor's input 0 has an immutable rank of 1 (a raw [480000] waveform).
        // Passing a rank-2 [1, 480000] tensor makes ExecuTorch abort the process
        // ("Attempted to change the tensor rank which is immutable: old=1, new=2").
        val input = Tensor.fromBlob(padded, longArrayOf(SAMPLES_30S.toLong()))
        val out = preprocessor.forward(EValue.from(input))
        return out[0].toTensor()
    }

    /** encoder(mel[1,80,3000]) -> encoder_hidden_states[1,1500,384]. */
    private fun encode(mel: Tensor): Tensor {
        val out = model.execute("encoder", EValue.from(mel))
        return out[0].toTensor()
    }

    /**
     * Greedy autoregressive decode. text_decoder is stateful: it is fed one token at a
     * time with the encoder output and the current cache position, keeping its KV-cache
     * in the module's mutable buffers across calls. We prime the cache with the forced
     * prompt (SOT + no-timestamps), then greedily pick argmax tokens until EOT.
     */
    private fun greedyDecode(encoderOut: Tensor, maxNewTokens: Int): IntArray {
        val prompt = tokenizer.initialPromptTokens()
        Log.d(TAG, "decode: prompt=${prompt.toList()} eot=${tokenizer.endOfTextId}")

        var position = 0
        var lastLogits: FloatArray? = null
        // Prefill the forced prompt so the cache is primed, keeping the logits emitted
        // after the final prompt token to choose the first generated token.
        for (tok in prompt) {
            lastLogits = decoderStep(tok, encoderOut, position)
            position++
        }

        val generated = ArrayList<Int>()
        var looped = false
        for (step in 0 until maxNewTokens) {
            val next = argmax(lastLogits!!)
            if (step < 3) {
                Log.d(TAG, "decode step $step: argmax=$next logit=${lastLogits[next]} isEot=${tokenizer.isEndOfText(next)}")
            }
            if (tokenizer.isEndOfText(next)) break
            generated.add(next)

            // Greedy decoding with no repetition penalty falls into a cycle when the audio
            // carries no clear speech — it emitted "Hello." 112 times on one 15 s segment of
            // room noise, ran to the token cap, and that transcript reached the classifier and
            // triggered a live SMS. Stop as soon as the cycle is unmistakable.
            if (isLooping(generated)) {
                looped = true
                break
            }

            lastLogits = decoderStep(next, encoderOut, position)
            position++
        }

        if (looped) {
            Log.w(TAG, "decode: repetition loop after ${generated.size} tokens — trimming")
        } else if (generated.size >= maxNewTokens) {
            // Real speech in a 15 s window is well under 100 tokens, so reaching the cap is
            // itself evidence the output is not a faithful transcript.
            Log.w(TAG, "decode: hit the $maxNewTokens token cap; output is likely hallucinated")
        }

        val trimmed = trimRepetition(generated)
        Log.d(TAG, "decode: generated ${trimmed.size} tokens=${trimmed.take(12)}")
        return trimmed.toIntArray()
    }

    private fun decoderStep(tokenId: Int, encoderOut: Tensor, position: Int): FloatArray {
        val inputIds = Tensor.fromBlob(longArrayOf(tokenId.toLong()), longArrayOf(1, 1))
        val cachePos = Tensor.fromBlob(longArrayOf(position.toLong()), longArrayOf(1))
        val out = model.execute(
            "text_decoder",
            EValue.from(inputIds),
            EValue.from(encoderOut),
            EValue.from(cachePos),
        )
        return out[0].toTensor().dataAsFloatArray
    }

    private fun argmax(logits: FloatArray): Int {
        var best = 0
        var bestVal = logits[0]
        for (i in 1 until logits.size) {
            if (logits[i] > bestVal) {
                bestVal = logits[i]
                best = i
            }
        }
        return best
    }

    fun close() {
        runCatching { preprocessor.destroy() }
        runCatching { model.destroy() }
    }

    companion object {
        private const val TAG = "WhisperModel"
        private const val SAMPLE_RATE = 16_000
        private const val SAMPLES_30S = SAMPLE_RATE * 30

        /**
         * How many identical repeats of a token cycle prove a loop rather than emphasis.
         *
         * Four is deliberately generous. Someone in real distress does say "help me help me
         * help me", and cutting at two would treat a genuine cry for help as a glitch.
         */
        private const val LOOP_REPEATS = 4

        /** Copies of a repeated cycle kept after trimming. */
        private const val KEEP_REPEATS = 2

        /** Longest cycle considered — a repeated word or clause, not a repeated paragraph. */
        private const val MAX_CYCLE_TOKENS = 12

        /** True once the tail of [tokens] is one short cycle repeated [LOOP_REPEATS] times. */
        internal fun isLooping(tokens: List<Int>): Boolean {
            for (cycle in 1..MAX_CYCLE_TOKENS) {
                val needed = cycle * LOOP_REPEATS
                if (tokens.size < needed) break
                val tail = tokens.subList(tokens.size - needed, tokens.size)
                val unit = tail.subList(0, cycle)
                val repeated = (1 until LOOP_REPEATS).all { r ->
                    tail.subList(r * cycle, (r + 1) * cycle) == unit
                }
                if (repeated) return true
            }
            return false
        }

        /**
         * Collapses a repeated tail down to [KEEP_REPEATS] copies.
         *
         * Trimming rather than discarding is the safety-relevant choice: the repeated content
         * may itself be the emergency. "help me" six times becomes "help me help me", which
         * still says exactly what it needs to, while "Hello." a hundred times stops being the
         * degenerate input that pushed the classifier into calling it a medical emergency.
         */
        internal fun trimRepetition(tokens: List<Int>): List<Int> {
            for (cycle in 1..MAX_CYCLE_TOKENS) {
                if (tokens.size < cycle * LOOP_REPEATS) break
                val unit = tokens.subList(tokens.size - cycle, tokens.size)

                var repeats = 0
                var end = tokens.size
                while (end - cycle >= 0 && tokens.subList(end - cycle, end) == unit) {
                    repeats++
                    end -= cycle
                }

                if (repeats >= LOOP_REPEATS) {
                    return tokens.subList(0, end + cycle * KEEP_REPEATS)
                }
            }
            return tokens
        }

        /**
         * Load the model from the files [ModelRepository] downloaded on first launch.
         *
         * ExecuTorch's [Module] loads from a filesystem path, so [ModelStore] is what turns a
         * catalog entry into one — extracting from `assets/` instead if you chose to bundle a
         * model anyway. SoLoader must already be initialized (see SafetyModeApp).
         *
         * @throws IllegalStateException if a model is missing, which means the setup screen
         *   was bypassed — callers surface the message rather than retrying.
         */
        fun load(context: Context): WhisperModel {
            val preprocPath = require(context, ModelCatalog.WHISPER_PREPROCESSOR)
            val modelPath = require(context, ModelCatalog.WHISPER_MODEL)
            val tokenizerFile = require(context, ModelCatalog.WHISPER_TOKENIZER)

            Log.i(TAG, "Loading ExecuTorch modules")
            val preprocessor = Module.load(preprocPath.absolutePath)
            val model = Module.load(modelPath.absolutePath)
            val tokenizer = WhisperTokenizer.fromFile(tokenizerFile)
            return WhisperModel(preprocessor, model, tokenizer)
        }

        private fun require(context: Context, asset: ModelAsset): File =
            ModelStore.resolve(context, asset).getOrElse {
                throw IllegalStateException("${asset.displayName} is not available: ${it.message}", it)
            }
    }
}
