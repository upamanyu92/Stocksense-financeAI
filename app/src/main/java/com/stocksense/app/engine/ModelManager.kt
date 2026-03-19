package com.stocksense.app.engine

import android.content.Context
import android.util.Log
import com.stocksense.app.data.model.PredictionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "ModelManager"
private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L  // 5 minutes

/**
 * ModelManager – central coordinator for ML model lifecycle.
 *
 * Responsibilities:
 *  - Lazy-load TFLite and LLM models on first use
 *  - Unload idle models to free memory
 *  - Select quality mode based on available device RAM
 */
class ModelManager(context: Context) {

    val predictionEngine = PredictionEngine(context)
    val llmEngine = LLMInsightEngine(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastUsedMs = 0L

    /** Ensure both models are loaded; call before inference. */
    fun ensureLoaded(mode: QualityMode = llmEngine.currentQualityMode()) {
        lastUsedMs = System.currentTimeMillis()
        predictionEngine.loadModel()
        scope.launch {
            llmEngine.loadModel(mode)
        }
    }

    /** Release models from memory (called when app goes to background or battery is low). */
    fun releaseAll() {
        predictionEngine.unloadModel()
        llmEngine.unloadModel()
        Log.i(TAG, "All models unloaded")
    }

    /**
     * Release models if they have been idle for [IDLE_TIMEOUT_MS].
     * Call this periodically from a WorkManager job or lifecycle observer.
     */
    fun releaseIfIdle() {
        if (lastUsedMs > 0 && System.currentTimeMillis() - lastUsedMs > IDLE_TIMEOUT_MS) {
            releaseAll()
            lastUsedMs = 0L
        }
    }

    fun markUsed() { lastUsedMs = System.currentTimeMillis() }
}
