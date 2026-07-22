package com.example.safetymode.asr

import org.json.JSONObject
import java.io.File

/**
 * Minimal Whisper tokenizer for the *decoding* side only (id -> text). It reads a
 * HuggingFace `tokenizer.json` (the data file you download from the model repo) and
 * builds the id->token table plus the GPT-2 byte-level BPE reverse map, then resolves
 * the special-token ids we need to prompt/stop the decoder.
 *
 * We look up special tokens by their literal content string (e.g. "<|notimestamps|>")
 * rather than hardcoding ids, because those ids differ between the multilingual and
 * English-only (".en") checkpoints. For whisper-tiny.en these resolve to
 * SOT=50257, notimestamps=50362, EOT=50256 — matching the exported model's config.
 */
class WhisperTokenizer private constructor(
    private val idToToken: Map<Int, String>,
    private val specialIds: Set<Int>,
    private val byteDecoder: Map<Char, Int>,
    val startOfTranscriptId: Int,
    val noTimestampsId: Int,
    val endOfTextId: Int,
) {

    /**
     * Forced decoder prefix for the English-only tiny.en model: SOT + no-timestamps.
     *
     * We must NOT include language/task tokens here. The bundled tokenizer.json is the
     * multilingual variant (it lists `<|en|>`, `<|transcribe|>`, etc.), but the exported
     * model is `.en` — feeding it those tokens makes the decoder emit end-of-text at the
     * very first step, yielding an empty transcript.
     */
    fun initialPromptTokens(): IntArray =
        intArrayOf(startOfTranscriptId, noTimestampsId)

    /** True once the model emits end-of-text (generation should stop). */
    fun isEndOfText(tokenId: Int): Boolean = tokenId == endOfTextId

    /**
     * Decode generated ids to text. Special tokens (SOT, language, task, timestamps,
     * EOT) are dropped; the remaining ids are byte-level-BPE decoded to UTF-8.
     */
    fun decode(ids: IntArray): String {
        val bytes = ArrayList<Byte>(ids.size * 2)
        for (id in ids) {
            if (id in specialIds) continue
            val token = idToToken[id] ?: continue
            for (ch in token) {
                val b = byteDecoder[ch] ?: continue
                bytes.add(b.toByte())
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8).trim()
    }

    companion object {
        fun fromFile(file: File): WhisperTokenizer {
            val root = JSONObject(file.readText())

            // --- vocab (token string -> id) lives under model.vocab -----------------
            val vocab = root.getJSONObject("model").getJSONObject("vocab")
            val idToToken = HashMap<Int, String>(vocab.length())
            val tokenToId = HashMap<String, Int>(vocab.length())
            val keys = vocab.keys()
            while (keys.hasNext()) {
                val tok = keys.next()
                val id = vocab.getInt(tok)
                idToToken[id] = tok
                tokenToId[tok] = id
            }

            // --- special / added tokens --------------------------------------------
            val specialIds = HashSet<Int>()
            if (root.has("added_tokens")) {
                val added = root.getJSONArray("added_tokens")
                for (i in 0 until added.length()) {
                    val obj = added.getJSONObject(i)
                    val id = obj.getInt("id")
                    val content = obj.getString("content")
                    idToToken[id] = content
                    tokenToId[content] = id
                    if (obj.optBoolean("special", true)) specialIds.add(id)
                }
            }

            fun requireId(content: String): Int =
                tokenToId[content]
                    ?: error("tokenizer.json missing required special token '$content'")

            val sot = requireId("<|startoftranscript|>")
            val noTs = requireId("<|notimestamps|>")
            val eot = requireId("<|endoftext|>")

            return WhisperTokenizer(
                idToToken = idToToken,
                specialIds = specialIds,
                byteDecoder = buildByteDecoder(),
                startOfTranscriptId = sot,
                noTimestampsId = noTs,
                endOfTextId = eot,
            )
        }

        /**
         * Reverse of GPT-2 `bytes_to_unicode()`: maps the printable unicode chars used
         * in the vocab back to their original byte values.
         */
        private fun buildByteDecoder(): Map<Char, Int> {
            val bs = ArrayList<Int>()
            (('!'.code)..('~'.code)).forEach { bs.add(it) }
            (('¡'.code)..('¬'.code)).forEach { bs.add(it) }
            (('®'.code)..('ÿ'.code)).forEach { bs.add(it) }

            val cs = ArrayList(bs)
            var n = 0
            for (b in 0 until 256) {
                if (b !in bs) {
                    bs.add(b)
                    cs.add(256 + n)
                    n++
                }
            }
            // char (from vocab space) -> original byte
            val decoder = HashMap<Char, Int>(256)
            for (i in bs.indices) {
                decoder[cs[i].toChar()] = bs[i]
            }
            return decoder
        }
    }
}
