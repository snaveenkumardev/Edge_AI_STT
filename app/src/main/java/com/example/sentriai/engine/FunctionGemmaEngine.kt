package com.example.sentriai.engine

import android.content.Context
import android.util.Log
import com.example.sentriai.models.ModelCatalog
import com.example.sentriai.models.ModelStore
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The emergency alert as it will be dispatched: an [EmergencyDecision] turned into concrete
 * call arguments.
 */
data class ToolCallResult(
    val emergencyType: String,
    val triggerPhrase: String,
    val confidence: Float,
)

/**
 * Formats an already-made emergency decision as a `trigger_emergency_alert` function call.
 *
 * This engine does **not** decide whether an emergency exists — [EmergencyDetector] does that,
 * and by the time [buildAlertCall] is reached the answer is already yes. Asking a 270M
 * function-calling model to infer distress from a statement like "I fell" was asking it to do
 * intent classification, which is not what it was tuned for; routing an explicit instruction to
 * a single declared tool is.
 *
 * Because the decision is already made, every failure path here still returns a usable result
 * built straight from the decision. A malformed model response degrades the arguments, never
 * the alert.
 */
object FunctionGemmaEngine {

    private const val TAG = "FunctionGemmaEngine"

    private const val FUNCTION_NAME = "trigger_emergency_alert"

    // `<escape>` delimits every string value in FunctionGemma's DSL; see [FunctionCallParser].
    private const val ESCAPE = FunctionCallParser.ESCAPE

    /**
     * The tool declaration in FunctionGemma's own DSL — not JSON schema, which the model was
     * never trained to read. Property order is alphabetical and `type` values are uppercase
     * because that is what the training-time chat template emits.
     */
    private const val TOOL_DECLARATION =
        "<start_function_declaration>declaration:$FUNCTION_NAME{" +
            "description:${ESCAPE}Send an emergency SMS alert to the caregiver of the person being monitored.$ESCAPE," +
            "parameters:{properties:{" +
            "confidence:{description:${ESCAPE}Detection confidence between 0.0 and 1.0$ESCAPE,type:${ESCAPE}NUMBER$ESCAPE}," +
            "emergency_type:{description:${ESCAPE}The kind of emergency that was detected$ESCAPE," +
            "enum:[${ESCAPE}Fall$ESCAPE,${ESCAPE}Medical Distress$ESCAPE,${ESCAPE}Help Call$ESCAPE,${ESCAPE}Distress Phrase$ESCAPE]," +
            "type:${ESCAPE}STRING$ESCAPE}," +
            "trigger_phrase:{description:${ESCAPE}The exact words the person spoke$ESCAPE,type:${ESCAPE}STRING$ESCAPE}" +
            "},required:[${ESCAPE}emergency_type$ESCAPE,${ESCAPE}trigger_phrase$ESCAPE,${ESCAPE}confidence$ESCAPE]," +
            "type:${ESCAPE}OBJECT$ESCAPE}}" +
            "<end_function_declaration>"

    // Google documents this exact sentence as the prompt-based trigger that switches the model
    // out of plain conversation and into function-calling mode. Without it — or with the
    // declaration outside a `developer` turn — the model answers in prose instead of calling.
    private const val ACTIVATION_LINE =
        "You are a model that can do function calling with the following functions"

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    var lastError: String? = null
        private set

    fun isReady(): Boolean = llmInference != null

    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (llmInference != null) return@withContext true

        val modelFile = ModelStore.resolve(context, ModelCatalog.FUNCTION_GEMMA).getOrElse { e ->
            lastError = "FunctionGemma unavailable: ${e.message}"
            Log.w(TAG, lastError!!)
            return@withContext false
        }

        try {
            Log.i(TAG, "Loading FunctionGemma from ${modelFile.absolutePath}")
            llmInference = LlmInference.createFromOptions(
                context,
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(512)
                    .build(),
            )
            lastError = null
            Log.i(TAG, "FunctionGemma ready.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "FunctionGemma load failed: ${e.message}", e)
            llmInference = null
            lastError = e.message ?: e.javaClass.simpleName
            false
        }
    }

    /**
     * Turns a settled [decision] into the arguments for the emergency alert.
     *
     * The decision stays authoritative for the emergency type and the confidence. What the model
     * contributes is argument extraction — pulling the exact spoken words out of the utterance —
     * so a wrong or missing call costs a better `trigger_phrase`, nothing more.
     */
    suspend fun buildAlertCall(
        decision: EmergencyDecision,
        transcript: String,
    ): ToolCallResult = withContext(Dispatchers.Default) {
        val fallback = ToolCallResult(decision.emergencyType, decision.triggerPhrase, decision.confidence)
        val inference = llmInference ?: return@withContext fallback

        var session: LlmInferenceSession? = null
        try {
            session = LlmInferenceSession.createFromOptions(
                inference,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(1)
                    .setTemperature(0.0f)
                    // The bundle's metadata otherwise wraps every chunk in a user turn, which
                    // would nest the developer turn inside it. Blanking all six affixes hands
                    // full control of the prompt to buildToolCallPrompt; `<bos>` is a
                    // model-level start token and is still prepended.
                    .setPromptTemplates(
                        PromptTemplates.builder()
                            .setUserPrefix("")
                            .setUserSuffix("")
                            .setModelPrefix("")
                            .setModelSuffix("")
                            .setSystemPrefix("")
                            .setSystemSuffix("")
                            .build(),
                    )
                    .build(),
            )

            session.addQueryChunk(buildToolCallPrompt(decision, transcript))
            val response = session.generateResponse()
            Log.d(TAG, "FunctionGemma raw response: $response")

            val call = FunctionCallParser.parse(response)
            if (call == null) {
                Log.w(TAG, "No parsable function call; dispatching from the detector's decision.")
                return@withContext fallback
            }
            if (call.name != FUNCTION_NAME) {
                Log.w(TAG, "Model called '${call.name}' instead of $FUNCTION_NAME; ignoring its arguments.")
                return@withContext fallback
            }

            val spoken = call.arguments["trigger_phrase"]?.takeIf { it.isNotBlank() }
            ToolCallResult(
                emergencyType = decision.emergencyType,
                triggerPhrase = spoken ?: decision.triggerPhrase,
                confidence = decision.confidence,
            )
        } catch (e: Exception) {
            Log.e(TAG, "FunctionGemma inference failed: ${e.message}", e)
            fallback
        } finally {
            runCatching { session?.close() }
        }
    }

    /**
     * Builds the full prompt in FunctionGemma's training format: declarations inside a
     * `developer` turn, then the request as a direct instruction to call the tool.
     *
     * The user turn is phrased as a command rather than a question because the model is trained
     * on single-turn "route this request to a tool" data — the emergency has already been
     * established, so there is nothing here for it to judge.
     */
    private fun buildToolCallPrompt(decision: EmergencyDecision, transcript: String): String {
        val spoken = transcript.replace('"', '\'').trim()
        return "<start_of_turn>developer\n" +
            ACTIVATION_LINE + TOOL_DECLARATION +
            "<end_of_turn>\n" +
            "<start_of_turn>user\n" +
            "A ${decision.emergencyType} emergency was detected for the person I monitor. " +
            "They said: \"$spoken\". Send the emergency alert now with confidence " +
            "${"%.2f".format(decision.confidence)}." +
            "<end_of_turn>\n" +
            "<start_of_turn>model\n"
    }

    fun close() {
        runCatching { llmInference?.close() }
            .onFailure { Log.e(TAG, "Error closing LlmInference: ${it.message}") }
        llmInference = null
    }
}
