package com.example.sentriai.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Result returned when FunctionGemma decides to invoke [FUNCTION_NAME].
 */
data class ToolCallResult(
    val emergencyType: String,
    val triggerPhrase: String,
    val confidence: Float,
)

/**
 * On-device LLM engine wrapping FunctionGemma 270M IT via MediaPipe LLM Inference API.
 *
 * Checks for the model file at:
 * 1. Primary: `/data/local/tmp/llm/functiongemma-270m-it.task` (pushed via ADB)
 * 2. Fallback: `context.filesDir/functiongemma-270m-it.task`
 * 3. Fallback: `context.cacheDir/functiongemma-270m-it.task`
 */
object FunctionGemmaEngine {

    private const val TAG = "FunctionGemmaEngine"

    const val DEFAULT_MODEL_PATH = "/data/local/tmp/llm/functiongemma-270m-it.task"
    private const val FUNCTION_NAME = "trigger_emergency_alert"

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    var lastError: String? = null
        private set

    /**
     * Resolves the model file location on device.
     */
    fun findModelFile(context: Context): File? {
        val filesDirModel = File(context.filesDir, "functiongemma-270m-it.task")

        // Retrieve actual asset size reliably using InputStream.available()
        val assetLength = try {
            context.assets.open("functiongemma-270m-it.task").use { it.available().toLong() }
        } catch (_: Exception) {
            -1L
        }

        // 1. If already extracted in filesDir with matching size, return immediately
        if (filesDirModel.exists() && filesDirModel.length() > 0 && assetLength > 0 && filesDirModel.length() == assetLength) {
            Log.i(TAG, "Found valid bundled FunctionGemma model in app filesDir: ${filesDirModel.absolutePath} (${filesDirModel.length()} bytes)")
            return filesDirModel
        }

        // 2. Extract from app assets (bundled directly inside the APK)
        try {
            Log.i(TAG, "Extracting bundled functiongemma-270m-it.task from APK assets to filesDir...")
            if (filesDirModel.exists()) filesDirModel.delete()
            context.assets.open("functiongemma-270m-it.task").use { input ->
                filesDirModel.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024) // 64KB buffer for high-throughput I/O
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }
            Log.i(TAG, "Successfully extracted bundled model to filesDir (${filesDirModel.length()} bytes)")
            return filesDirModel
        } catch (e: Exception) {
            Log.w(TAG, "Asset model extraction failed: ${e.message}", e)
            lastError = "Asset copy failed: ${e.message}"
        }

        // 3. Optional fallback: ADB push location
        val primary = File(DEFAULT_MODEL_PATH)
        if (primary.exists() && primary.length() > 0) return primary

        val cacheDirModel = File(context.cacheDir, "functiongemma-270m-it.task")
        if (cacheDirModel.exists() && cacheDirModel.length() > 0) return cacheDirModel

        if (lastError == null) {
            lastError = "Model file not found in storage or assets"
        }
        return null
    }

    /**
     * True if the engine has been successfully initialized and is ready for inference.
     */
    fun isReady(): Boolean = isInitialized && llmInference != null

    /**
     * Asynchronously loads the MediaPipe LLM Inference engine.
     *
     * @return true if initialized successfully, false if the model file is not found or error occurred.
     */
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext true

        lastError = null
        val modelFile = findModelFile(context)
        if (modelFile == null) {
            Log.w(TAG, "FunctionGemma model file not found: $lastError")
            isInitialized = false
            return@withContext false
        }

        try {
            Log.i(TAG, "Initializing FunctionGemma model from: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true
            lastError = null
            Log.i(TAG, "FunctionGemma engine successfully initialized.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FunctionGemma engine: ${e.message}", e)
            isInitialized = false
            llmInference = null
            lastError = e.message ?: e.javaClass.simpleName
            false
        }
    }

    /**
     * Analyzes a Whisper STT transcript chunk to determine if `trigger_emergency_alert` should fire.
     *
     * @param transcript The speech-to-text transcript.
     * @return [ToolCallResult] if an emergency was detected by FunctionGemma, or null otherwise.
     */
    suspend fun analyzeTranscript(transcript: String): ToolCallResult? = withContext(Dispatchers.Default) {
        val inference = llmInference
        if (inference == null || !isInitialized) {
            Log.w(TAG, "Engine not ready. Analyzing transcript with heuristic backup.")
            return@withContext heuristicBackupAnalysis(transcript)
        }

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(0.2f)
            .setTopK(10)
            .build()

        var session: LlmInferenceSession? = null
        try {
            session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            val prompt = buildFunctionGemmaPrompt(transcript)

            session.addQueryChunk(prompt)
            val response = session.generateResponse()

            Log.d(TAG, "FunctionGemma Raw Response: $response")
            parseToolCallResponse(response, transcript)
        } catch (e: Exception) {
            Log.e(TAG, "Error during FunctionGemma inference: ${e.message}", e)
            heuristicBackupAnalysis(transcript)
        } finally {
            try {
                session?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Constructs prompt with system instruction and tool declaration in FunctionGemma control token format.
     */
    private fun buildFunctionGemmaPrompt(transcript: String): String {
        val toolDeclaration = """
            <start_function_declaration>
            {
              "name": "$FUNCTION_NAME",
              "description": "Trigger an emergency SMS alert to caregiver when a fall, medical distress, or explicit call for help is detected.",
              "parameters": {
                "type": "object",
                "properties": {
                  "emergency_type": {
                    "type": "string",
                    "enum": ["Fall", "Medical Distress", "Help Call"]
                  },
                  "trigger_phrase": {
                    "type": "string",
                    "description": "The exact words spoken that triggered the alert"
                  },
                  "confidence": {
                    "type": "number",
                    "description": "Confidence score from 0.0 to 1.0"
                  }
                },
                "required": ["emergency_type", "trigger_phrase", "confidence"]
              }
            }
            <end_function_declaration>
        """.trimIndent()

        return """
            $toolDeclaration
            <start_of_turn>user
            Analyze this speech transcript for elderly safety threats: "$transcript"
            If safety threat detected, call $FUNCTION_NAME. Otherwise reply NO_EMERGENCY.<end_of_turn>
            <start_of_turn>model
        """.trimIndent()
    }

    /**
     * Parses the model response for `<start_function_call>` or JSON formatted arguments.
     */
    private fun parseToolCallResponse(response: String, originalTranscript: String): ToolCallResult? {
        if (response.isBlank() || response.contains("NO_EMERGENCY")) return null

        try {
            // Check for control tokens or JSON blocks
            val jsonString = when {
                response.contains("<start_function_call>") -> {
                    response.substringAfter("<start_function_call>")
                        .substringBefore("<end_function_call>")
                        .trim()
                }
                response.contains("{") && response.contains("}") -> {
                    response.substring(response.indexOf('{'), response.lastIndexOf('}') + 1)
                }
                else -> null
            }

            if (jsonString != null) {
                val json = JSONObject(jsonString)
                val callName = json.optString("name", FUNCTION_NAME)
                val args = if (json.has("parameters")) json.getJSONObject("parameters")
                           else if (json.has("arguments")) json.getJSONObject("arguments")
                           else json

                val emergencyType = args.optString("emergency_type", "Help Call")
                val triggerPhrase = args.optString("trigger_phrase", originalTranscript)
                val confidence = args.optDouble("confidence", 0.95).toFloat()

                if (callName == FUNCTION_NAME || args.has("emergency_type")) {
                    return ToolCallResult(
                        emergencyType = sanitizeEmergencyType(emergencyType),
                        triggerPhrase = triggerPhrase,
                        confidence = confidence.coerceIn(0.1f, 1.0f)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON tool call: ${e.message}")
        }

        // Fallback check if response mentions emergency keyword
        return heuristicBackupAnalysis(originalTranscript)
    }

    /**
     * Lightweight heuristic backup so the emergency system remains functional even when
     * the LLM file is missing or responding in free-form text.
     */
    private fun heuristicBackupAnalysis(transcript: String): ToolCallResult? {
        val text = transcript.lowercase()
        return when {
            text.contains("fall") || text.contains("fell") || text.contains("can't get up") || text.contains("tripped") -> {
                ToolCallResult(
                    emergencyType = "Fall",
                    triggerPhrase = transcript,
                    confidence = 0.92f
                )
            }
            text.contains("chest pain") || text.contains("stroke") || text.contains("heart") || text.contains("breath") || text.contains("dizzy") -> {
                ToolCallResult(
                    emergencyType = "Medical Distress",
                    triggerPhrase = transcript,
                    confidence = 0.89f
                )
            }
            text.contains("help") || text.contains("emergency") || text.contains("sos") || text.contains("call 911") || text.contains("someone help") -> {
                ToolCallResult(
                    emergencyType = "Help Call",
                    triggerPhrase = transcript,
                    confidence = 0.95f
                )
            }
            else -> null
        }
    }

    /**
     * Sanitizes emergency type string into standard categories.
     */
    private fun sanitizeEmergencyType(type: String): String {
        val lower = type.lowercase()
        return when {
            lower.contains("fall") -> "Fall"
            lower.contains("medical") || lower.contains("distress") || lower.contains("pain") -> "Medical Distress"
            else -> "Help Call"
        }
    }

    /**
     * Releases model resources when app shuts down.
     */
    fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LlmInference: ${e.message}")
        } finally {
            llmInference = null
            isInitialized = false
        }
    }
}
