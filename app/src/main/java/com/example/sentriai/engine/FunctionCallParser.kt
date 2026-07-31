package com.example.sentriai.engine

/**
 * Reads FunctionGemma's function-call wire format.
 *
 * The model does not emit JSON. A call looks like:
 *
 * ```
 * <start_function_call>call:trigger_emergency_alert{confidence:0.92,emergency_type:<escape>Fall<escape>}<end_function_call>
 * ```
 *
 * `<escape>` is a single token used as both the opening and the closing delimiter of a string
 * value, so it is tracked by toggling rather than by matching a pair.
 */
internal object FunctionCallParser {

    const val ESCAPE = "<escape>"
    private const val START_CALL = "<start_function_call>"
    private const val END_CALL = "<end_function_call>"

    data class Call(val name: String, val arguments: Map<String, String>)

    /**
     * @return the first call in [response], or null if there is no well-formed one.
     */
    fun parse(response: String): Call? {
        val start = response.indexOf(START_CALL)
        if (start < 0) return null

        var body = response.substring(start + START_CALL.length)
        // `<end_function_call>` is one of the bundle's stop tokens, so the runtime usually cuts
        // the response before it ever appears. Its presence is optional.
        body.indexOf(END_CALL).let { if (it >= 0) body = body.substring(0, it) }
        // The `call:` prefix is what the base model emits; fine-tuned bundles are known to drop it.
        body = body.trim().removePrefix("call:").trim()

        val open = body.indexOf('{')
        val close = body.lastIndexOf('}')
        if (open < 0 || close <= open) return null

        val name = body.substring(0, open).trim()
        if (name.isEmpty()) return null

        val arguments = LinkedHashMap<String, String>()
        for (part in splitTopLevel(body.substring(open + 1, close), ',')) {
            val colon = topLevelIndices(part, ':').firstOrNull() ?: continue
            val key = part.substring(0, colon).trim()
            if (key.isNotEmpty()) arguments[key] = unescape(part.substring(colon + 1))
        }

        return Call(name, arguments)
    }

    /**
     * Positions of [delimiter] at brace/bracket depth zero and outside an `<escape>`-quoted
     * string, so punctuation inside a spoken phrase is never mistaken for a separator.
     */
    private fun topLevelIndices(s: String, delimiter: Char): List<Int> {
        val positions = mutableListOf<Int>()
        var depth = 0
        var inEscape = false
        var i = 0
        while (i < s.length) {
            if (s.startsWith(ESCAPE, i)) {
                inEscape = !inEscape
                i += ESCAPE.length
                continue
            }
            if (!inEscape) {
                when (s[i]) {
                    '{', '[' -> depth++
                    '}', ']' -> depth--
                    delimiter -> if (depth == 0) positions.add(i)
                }
            }
            i++
        }
        return positions
    }

    private fun splitTopLevel(s: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        for (index in topLevelIndices(s, delimiter)) {
            parts.add(s.substring(start, index))
            start = index + 1
        }
        parts.add(s.substring(start))
        return parts.filter { it.isNotBlank() }
    }

    private fun unescape(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length >= 2 * ESCAPE.length &&
            trimmed.startsWith(ESCAPE) &&
            trimmed.endsWith(ESCAPE)
        ) {
            trimmed.substring(ESCAPE.length, trimmed.length - ESCAPE.length)
        } else {
            trimmed
        }
    }
}
