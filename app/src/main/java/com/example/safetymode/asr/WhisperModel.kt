package com.example.safetymode.asr

import android.content.Context
import android.util.Log
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
        for (step in 0 until maxNewTokens) {
            val next = argmax(lastLogits!!)
            if (step < 3) {
                Log.d(TAG, "decode step $step: argmax=$next logit=${lastLogits[next]} isEot=${tokenizer.isEndOfText(next)}")
            }
            if (tokenizer.isEndOfText(next)) break
            generated.add(next)
            lastLogits = decoderStep(next, encoderOut, position)
            position++
        }
        Log.d(TAG, "decode: generated ${generated.size} tokens=${generated.take(12)}")
        return generated.toIntArray()
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

        const val MODEL_ASSET = "model.pte"
        const val PREPROCESSOR_ASSET = "whisper_preprocessor.pte"
        const val TOKENIZER_ASSET = "tokenizer.json"

        /**
         * Load the model. Assets are copied to filesDir first because ExecuTorch's
         * [Module] loads from a filesystem path, not an asset stream. SoLoader must
         * already be initialized (see SafetyModeApp).
         */
        fun load(context: Context): WhisperModel {
            val modelPath = copyAsset(context, MODEL_ASSET)
            val preprocPath = copyAsset(context, PREPROCESSOR_ASSET)
            val tokenizerFile = copyAsset(context, TOKENIZER_ASSET)

            Log.i(TAG, "Loading ExecuTorch modules")
            val preprocessor = Module.load(preprocPath.absolutePath)
            val model = Module.load(modelPath.absolutePath)
            val tokenizer = WhisperTokenizer.fromFile(tokenizerFile)
            return WhisperModel(preprocessor, model, tokenizer)
        }

        private fun copyAsset(context: Context, name: String): File {
            val outFile = File(context.filesDir, name)
            // Uncompressed assets (.pte, via noCompress) expose a real length we can use
            // as a cheap staleness check; compressed assets (tokenizer.json) don't, so
            // for those we just copy when the file is missing.
            val assetSize = runCatching {
                context.assets.openFd(name).use { it.length }
            }.getOrNull()
            if (outFile.exists() &&
                (assetSize == null || outFile.length() == assetSize)
            ) {
                return outFile
            }
            context.assets.open(name).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            return outFile
        }
    }
}
