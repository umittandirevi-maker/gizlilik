package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.ModelDownloadManager
import com.example.model.PriorityLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AiClassificationEngine {
    private const val TAG = "AiClassificationEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isEngineReady: StateFlow<Boolean> = LiteRtInferenceEngine.isEngineReady
    val activeEngineDescription: StateFlow<String> = LiteRtInferenceEngine.engineStatusDescription
    val isEngineError: StateFlow<Boolean> = LiteRtInferenceEngine.isEngineError

    fun initialize(context: Context) {
        val modelFile = ModelDownloadManager.getModelFile(context)
        if (ModelDownloadManager.isModelFileFullyVerified(modelFile)) {
            tryLoadLocalModel(context, modelFile.absolutePath)
        } else {
            Log.i(TAG, "Model file not present or incomplete on disk yet.")
        }
    }

    fun tryLoadLocalModel(context: Context, modelPath: String) {
        scope.launch(Dispatchers.Default) {
            Log.i(TAG, "Launching isolated model loading for $modelPath")
            LiteRtInferenceEngine.loadModel(context, modelPath)
        }
    }

    suspend fun classifyNotification(
        context: Context,
        formattedPrompt: String,
        appType: String,
        channelType: String,
        title: String,
        body: String
    ): ClassificationResult = withContext(Dispatchers.Default) {
        val localResult = LiteRtInferenceEngine.classifyNotification(
            context = context,
            formattedPrompt = formattedPrompt,
            appType = appType,
            channelType = channelType,
            title = title,
            body = body
        )

        ClassificationResult(
            priority = localResult.priority,
            rawOutput = localResult.rawOutput,
            inferenceTimeMs = localResult.inferenceTimeMs,
            source = if (localResult.sourceDescription.isNotEmpty()) {
                localResult.sourceDescription
            } else if (localResult.isFromLiteRt) {
                "LiteRT-LM (100% Offline Qwen 0.5B)"
            } else {
                "Offline Linguistic Engine"
            },
            syntacticScore = localResult.syntacticScore,
            diagnosticSummary = localResult.diagnosticSummary
        )
    }

    data class ClassificationResult(
        val priority: PriorityLevel,
        val rawOutput: String,
        val inferenceTimeMs: Long,
        val source: String,
        val syntacticScore: Int = 0,
        val diagnosticSummary: String = ""
    )
}
