package com.example.ai

import android.content.Context
import android.util.Log
import com.example.model.PriorityLevel
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

object LiteRtInferenceEngine {
    private const val TAG = "LiteRtInferenceEngine"

    const val SYSTEM_INSTRUCTION =
        "You are an automated notification router. Output ONLY one uppercase word: UNIMPORTANT, ROUTINE, URGENT, or CRITICAL.\n" +
        "- If App Type is 'MESSENGER' AND Channel Type is 'DIRECT_1_ON_1' AND the Message conveys severe distress or an emergency, classify as CRITICAL or URGENT.\n" +
        "- If Channel Type is 'GROUP_CHAT_BROADCAST', always classify as ROUTINE, as it is a public mass chat.\n" +
        "- If App Type is 'UTILITY_OR_GAME', always classify as UNIMPORTANT. Treat all game hooks and promotions as noise."

    private var llmInference: LlmInference? = null
    private val inferenceMutex = Mutex()

    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    private val _isEngineError = MutableStateFlow(false)
    val isEngineError: StateFlow<Boolean> = _isEngineError.asStateFlow()

    private val _engineStatusDescription = MutableStateFlow("Initializing English Linguistic AI Engine...")
    val engineStatusDescription: StateFlow<String> = _engineStatusDescription.asStateFlow()

    private var isShieldModeActive = false

    suspend fun loadModel(context: Context, modelFilePath: String): Boolean = withContext(Dispatchers.Default) {
        val modelFile = File(modelFilePath)
        if (!modelFile.exists() || modelFile.length() < 480_000_000L) {
            _isEngineReady.value = false
            _isEngineError.value = true
            _engineStatusDescription.value = "AI Engine: Configuration Error"
            Log.e(TAG, "Model file not found or incomplete (<480MB, actual: ${modelFile.length()} bytes) at $modelFilePath")
            return@withContext false
        }

        // Native Canary Shield for Android 15/16 16KB kernels & HyperOS devices
        if (NativeCanaryShield.isShieldActive(context)) {
            isShieldModeActive = true
            _isEngineReady.value = true
            _isEngineError.value = false
            val reason = NativeCanaryShield.getShieldReason(context)
            _engineStatusDescription.value = "100% Offline AI (Linguistic Engine • $reason)"
            Log.i(TAG, "NativeCanaryShield active ($reason). Algorithmic Linguistic Sentence Analyzer enabled.")
            return@withContext true
        }

        _isEngineError.value = false
        _engineStatusDescription.value = "Initializing LiteRT Engine..."

        inferenceMutex.withLock {
            try {
                llmInference?.close()
            } catch (ignored: Throwable) {
                Log.w(TAG, "Error closing previous LlmInference session: ${ignored.message}")
            }
            llmInference = null
        }

        try {
            Log.i(TAG, "Attempting LiteRT stable session initialization for model: $modelFilePath")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFilePath)
                .setMaxTokens(16)
                .setTopK(40)
                .build()

            NativeCanaryShield.markInitStarted(context)
            val created = LlmInference.createFromOptions(context.applicationContext, options)
            NativeCanaryShield.markInitFinished(context, success = true)

            inferenceMutex.withLock {
                llmInference = created
            }
            isShieldModeActive = false
            _isEngineReady.value = true
            _isEngineError.value = false
            _engineStatusDescription.value = "LiteRT-LM Ready (100% Offline Qwen 0.5B)"
            return@withContext true
        } catch (e: Throwable) {
            NativeCanaryShield.markInitFinished(context, success = false)
            Log.e(TAG, "LiteRT-LM session creation failed: ${e.message}. Activating Linguistic Sentence Analyzer.", e)
            inferenceMutex.withLock {
                llmInference = null
            }
            isShieldModeActive = true
            _isEngineReady.value = true
            _isEngineError.value = false
            _engineStatusDescription.value = "100% Offline AI (Dynamic Linguistic Analyzer)"
            return@withContext true
        }
    }

    fun loadModelSync(context: Context, modelFilePath: String): Boolean {
        return try {
            runBlocking(Dispatchers.Default) {
                loadModel(context, modelFilePath)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "loadModelSync error: ${e.message}", e)
            _isEngineError.value = true
            _engineStatusDescription.value = "AI Engine: Configuration Error"
            false
        }
    }

    private suspend fun executeIsolatedNativeInference(context: Context, prompt: String): String = withContext(Dispatchers.Default) {
        try {
            inferenceMutex.withLock {
                val inference = llmInference ?: return@withContext "FALLBACK_TO_LINGUISTIC"
                NativeCanaryShield.markInferenceStarted(context)
                val response = try {
                    inference.generateResponse(prompt)
                } finally {
                    NativeCanaryShield.markInferenceFinished(context)
                }
                return@withContext response
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Isolated shield caught native AI execution fault: ${t.message}. Routing to Linguistic engine.", t)
            return@withContext "FALLBACK_TO_LINGUISTIC"
        }
    }

    /**
     * Classifies an intercepted notification using deep algorithmic sentence analysis
     * (Porter Stemmer, Soundex/Levenshtein typo tolerance, 5-word proximity relief clause).
     */
    suspend fun classifyNotification(
        context: Context,
        formattedPrompt: String,
        appType: String,
        channelType: String,
        title: String,
        body: String
    ): ClassificationResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        // Execute deep linguistic & phonetic sentence analysis
        val linguisticAnalysis = LinguisticSentenceAnalyzer.analyze(
            appType = appType,
            channelType = channelType,
            title = title,
            body = body
        )

        // If shield mode is active (16KB kernel, POCO rodin, or previous native abort) or native LLM not initialized,
        // instantly return the algorithmic linguistic analysis result
        if (isShieldModeActive || NativeCanaryShield.isShieldActive(context) || llmInference == null) {
            return@withContext ClassificationResult(
                priority = linguisticAnalysis.priority,
                rawOutput = linguisticAnalysis.diagnosticSummary,
                inferenceTimeMs = linguisticAnalysis.inferenceTimeMs,
                isFromLiteRt = false,
                sourceDescription = "Offline Linguistic Engine (Typo-Shielded)",
                syntacticScore = linguisticAnalysis.syntacticScore,
                diagnosticSummary = linguisticAnalysis.diagnosticSummary
            )
        }

        // Native inference execution for compatible hardware
        try {
            val fullPrompt = buildString {
                append(SYSTEM_INSTRUCTION)
                append("\n\n")
                append(formattedPrompt)
                append("\n\nOutput ONLY one word (UNIMPORTANT, ROUTINE, URGENT, or CRITICAL):")
            }

            if (_isEngineReady.value && !_isEngineError.value && llmInference != null) {
                val nativeOutput = withTimeoutOrNull(800L) {
                    executeIsolatedNativeInference(context, fullPrompt)
                }

                if (nativeOutput != null && nativeOutput != "FALLBACK_TO_LINGUISTIC") {
                    val priority = PriorityLevel.fromModelToken(nativeOutput)
                    val elapsed = System.currentTimeMillis() - startTime
                    return@withContext ClassificationResult(
                        priority = priority,
                        rawOutput = "[${priority.name}] LiteRT Neural Output",
                        inferenceTimeMs = elapsed,
                        isFromLiteRt = true,
                        sourceDescription = "LiteRT-LM (100% Offline Qwen 0.5B)",
                        syntacticScore = linguisticAnalysis.syntacticScore,
                        diagnosticSummary = linguisticAnalysis.diagnosticSummary
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Native shield caught prediction fault (${t.message}). Routing through Linguistic engine.", t)
        }

        val elapsed = System.currentTimeMillis() - startTime
        ClassificationResult(
            priority = linguisticAnalysis.priority,
            rawOutput = linguisticAnalysis.diagnosticSummary,
            inferenceTimeMs = elapsed,
            isFromLiteRt = false,
            sourceDescription = "Offline Linguistic Engine",
            syntacticScore = linguisticAnalysis.syntacticScore,
            diagnosticSummary = linguisticAnalysis.diagnosticSummary
        )
    }

    data class ClassificationResult(
        val priority: PriorityLevel,
        val rawOutput: String,
        val inferenceTimeMs: Long,
        val isFromLiteRt: Boolean,
        val sourceDescription: String = "",
        val syntacticScore: Int = 0,
        val diagnosticSummary: String = ""
    )
}
