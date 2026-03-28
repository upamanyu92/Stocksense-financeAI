package com.stocksense.app.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.stocksense.app.data.model.PredictionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LLMInsightEngine"
private const val LIVE_CHECK_PROMPT = "Reply with OK only."

/**
 * Status of the local LLM agent.
 */
enum class LlmStatus {
    /** LLM native library is not available; using template fallback. */
    NATIVE_UNAVAILABLE,
    /** Model file not downloaded yet. */
    MODEL_NOT_DOWNLOADED,
    /** Model is currently loading into memory. */
    LOADING,
    /** Model is loaded and ready for inference. */
    READY,
    /** Model failed to load; using template fallback. */
    LOAD_FAILED,
    /** Using deterministic template fallback (no LLM). */
    TEMPLATE_FALLBACK
}

/**
 * Metrics about the LLM agent's inference performance.
 */
data class AgenticMetrics(
    val status: LlmStatus = LlmStatus.NATIVE_UNAVAILABLE,
    val qualityMode: QualityMode = QualityMode.BALANCED,
    val isNativeAvailable: Boolean = false,
    val isModelDownloaded: Boolean = false,
    val modelFileName: String = "",
    val lastInferenceTimeMs: Long = 0L,
    val cacheHits: Int = 0,
    val totalInferences: Int = 0
)

/**
 * Quality modes that adapt model size to device capabilities.
 */
enum class QualityMode {
    /** Smaller 1-bit quantized model – fastest, lowest accuracy. */
    LITE,
    /** Default balanced 1-bit model. */
    BALANCED,
    /** Largest quantized model – best quality, requires ≥8 GB RAM. */
    PRO
}

/**
 * LLMInsightEngine – generates natural-language stock insights using a local
 * Microsoft BitNet 1-bit LLM running via [LlamaCpp] JNI.
 *
 * Model files are downloaded automatically by [BitNetModelDownloader] /
 * [com.stocksense.app.workers.ModelDownloadWorker] on first launch and stored
 * under `<internal-storage>/models/`.
 *
 * When the native library or model file is absent the engine falls back to a
 * deterministic template-based response so the app remains functional.
 *
 * ### Adaptive Quality Mode
 * The engine selects the model automatically based on available RAM, but the
 * user can also force a specific [QualityMode].
 *
 * | RAM     | Mode     | Model                              |
 * |---------|----------|------------------------------------|
 * | ≥ 8 GB  | PRO      | bitnet-b1.58-2B-4T-Q4_0.gguf      |
 * | 6–8 GB  | BALANCED | bitnet-b1.58-2B-4T-TQ2_0.gguf     |
 * | < 6 GB  | LITE     | bitnet-b1.58-2B-4T-TQ1_0.gguf     |
 */
class LLMInsightEngine(private val context: Context) {

    private var modelHandle: Long = 0L          // llama.cpp model pointer (0 = unloaded)
    private var contextHandle: Long = 0L        // llama.cpp context pointer
    private var currentMode: QualityMode? = null
    private var isNativeAvailable = false

    /** Current status of the LLM agent. */
    @Volatile
    var status: LlmStatus = LlmStatus.NATIVE_UNAVAILABLE
        private set

    /** Tracks inference performance metrics. */
    private var lastInferenceTimeMs: Long = 0L
    private var cacheHitCount: Int = 0
    private var totalInferenceCount: Int = 0

    /** Helper used to locate downloaded model files. */
    private val downloader = BitNetModelDownloader(context)

    /** Simple LRU-like response cache (symbol → insight). */
    private val responseCache = ConcurrentHashMap<String, CachedInsight>()
    private val cacheTtlMs = 5 * 60 * 1000L    // 5 minutes

    init {
        isNativeAvailable = tryLoadNativeLib()
        status = if (isNativeAvailable) LlmStatus.MODEL_NOT_DOWNLOADED else LlmStatus.NATIVE_UNAVAILABLE
    }

    /** Get current agentic metrics snapshot. */
    fun getMetrics(): AgenticMetrics {
        val mode = currentMode ?: autoSelectMode()
        val modelFile = preferredModelFile(mode)
        return AgenticMetrics(
            status = status,
            qualityMode = mode,
            isNativeAvailable = isNativeAvailable,
            isModelDownloaded = modelFile.exists(),
            modelFileName = modelFile.name,
            lastInferenceTimeMs = lastInferenceTimeMs,
            cacheHits = cacheHitCount,
            totalInferences = totalInferenceCount
        )
    }

    // ---------- Public API ----------

    /**
     * Lazily load the LLM for the given [mode].
     * Call this on a background thread (e.g., from a coroutine scope).
     */
    suspend fun loadModel(mode: QualityMode = autoSelectMode()) = withContext(Dispatchers.IO) {
        if (currentMode == mode && modelHandle != 0L) return@withContext
        unloadModel()
        currentMode = mode
        if (!isNativeAvailable) {
            Log.i(TAG, "Native library not available – using template fallback")
            status = LlmStatus.NATIVE_UNAVAILABLE
            return@withContext
        }
        val modelFile = preferredModelFile(mode)
        if (!modelFile.exists()) {
            Log.w(TAG, "Model file not found: ${modelFile.absolutePath} – using fallback")
            status = LlmStatus.MODEL_NOT_DOWNLOADED
            return@withContext
        }
        status = LlmStatus.LOADING
        try {
            val loadedModelHandle = LlamaCpp.loadModel(modelFile.absolutePath, nGpuLayers(mode))
            val loadedContextHandle = if (loadedModelHandle != 0L) {
                LlamaCpp.createContext(loadedModelHandle, contextSizeFor(mode))
            } else {
                0L
            }
            if (loadedModelHandle == 0L || loadedContextHandle == 0L) {
                status = LlmStatus.LOAD_FAILED
                Log.e(TAG, "Model load returned an invalid native handle")
                return@withContext
            }
            modelHandle = loadedModelHandle
            contextHandle = loadedContextHandle
            status = LlmStatus.READY
            Log.i(TAG, "BitNet LLM loaded: ${modelFile.name} (mode=$mode)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LLM: ${e.message}")
            modelHandle = 0L
            contextHandle = 0L
            status = LlmStatus.LOAD_FAILED
        }
    }

    /**
     * Unload the model and free native memory.
     */
    fun unloadModel() {
        if (modelHandle != 0L && isNativeAvailable) {
            try {
                LlamaCpp.freeContext(contextHandle)
                LlamaCpp.freeModel(modelHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading model: ${e.message}")
            }
        }
        modelHandle = 0L
        contextHandle = 0L
        currentMode = null
    }

    /**
     * Generate a natural-language insight for [prediction].
     * Returns cached result if still fresh.
     */
    suspend fun generateInsight(
        prediction: PredictionResult,
        recentPrices: List<Double>
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = "${prediction.symbol}_${prediction.direction}"
        responseCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < cacheTtlMs) {
                cacheHitCount++
                return@withContext cached.text
            }
        }

        val startTime = System.currentTimeMillis()

        val insight = if (modelHandle != 0L && isNativeAvailable) {
            val prompt = buildPrompt(prediction, recentPrices)
            val truncated = truncatePromptIfNeeded(prompt)
            try {
                val result = LlamaCpp.runInference(contextHandle, truncated, maxTokens = 200)
                totalInferenceCount++
                result
            } catch (e: Exception) {
                Log.e(TAG, "Inference error: ${e.message}")
                status = LlmStatus.TEMPLATE_FALLBACK
                templateInsight(prediction)
            }
        } else {
            templateInsight(prediction)
        }

        lastInferenceTimeMs = System.currentTimeMillis() - startTime
        responseCache[cacheKey] = CachedInsight(insight, System.currentTimeMillis())
        insight
    }

    /**
     * Generate a response to a free-form user question about a stock.
     * Falls back to template if LLM is not loaded.
     */
    suspend fun chat(
        userMessage: String,
        symbol: String,
        recentPrices: List<Double>
    ): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val response = if (modelHandle != 0L && isNativeAvailable) {
            val prompt = buildChatPrompt(userMessage, symbol, recentPrices)
            val truncated = truncatePromptIfNeeded(prompt)
            try {
                val result = LlamaCpp.runInference(contextHandle, truncated, maxTokens = 300)
                totalInferenceCount++
                result
            } catch (e: Exception) {
                Log.e(TAG, "Chat inference error: ${e.message}")
                status = LlmStatus.TEMPLATE_FALLBACK
                templateChatResponse(userMessage, symbol)
            }
        } else {
            templateChatResponse(userMessage, symbol)
        }

        lastInferenceTimeMs = System.currentTimeMillis() - startTime
        response
    }

    fun isModelLoaded(): Boolean = modelHandle != 0L || !isNativeAvailable

    fun currentQualityMode(): QualityMode = currentMode ?: autoSelectMode()

    /** @return true if the BitNet model file for the auto-selected mode is on disk. */
    fun isModelDownloaded(): Boolean = preferredModelFile(autoSelectMode()).exists()

    suspend fun runLiveCheck(mode: QualityMode = currentQualityMode()): Boolean = withContext(Dispatchers.IO) {
        loadModel(mode)
        if (status != LlmStatus.READY || contextHandle == 0L) {
            return@withContext false
        }

        val startTime = System.currentTimeMillis()
        return@withContext try {
            val output = LlamaCpp.runInference(contextHandle, LIVE_CHECK_PROMPT, maxTokens = 8).trim()
            lastInferenceTimeMs = System.currentTimeMillis() - startTime
            totalInferenceCount++
            val isHealthy = output.isNotBlank()
            status = if (isHealthy) LlmStatus.READY else LlmStatus.LOAD_FAILED
            isHealthy
        } catch (e: Exception) {
            Log.e(TAG, "Live check failed: ${e.message}")
            status = LlmStatus.LOAD_FAILED
            false
        }
    }

    // ---------- Private helpers ----------

    private fun autoSelectMode(): QualityMode {
        val ramGb = getDeviceRamGb()
        return when {
            ramGb >= 8 -> QualityMode.PRO
            ramGb >= 6 -> QualityMode.BALANCED
            else -> QualityMode.LITE
        }
    }

    private fun getDeviceRamGb(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / (1024L * 1024L * 1024L)).toInt()
    }

    private fun contextSizeFor(mode: QualityMode) = when (mode) {
        QualityMode.LITE -> 512
        QualityMode.BALANCED -> 1024
        QualityMode.PRO -> 2048
    }

    private fun nGpuLayers(mode: QualityMode) = when (mode) {
        QualityMode.LITE -> 0
        QualityMode.BALANCED -> 20
        QualityMode.PRO -> 35
    }

    private fun buildPrompt(prediction: PredictionResult, recentPrices: List<Double>): String {
        val priceStr = recentPrices.takeLast(5).joinToString(", ") { "%.2f".format(it) }
        return """
You are a financial analyst. Provide a concise 2-sentence insight.

Stock: ${prediction.symbol}
Recent prices: $priceStr
Predicted direction: ${prediction.direction}
Predicted price: ${"%.2f".format(prediction.predictedPrice)}
Confidence: ${"%.0f".format(prediction.confidence * 100)}%

Insight:""".trimIndent()
    }

    /** Keep prompt within context window to avoid OOM on low-end devices. */
    private fun truncatePromptIfNeeded(prompt: String, maxChars: Int = 800): String =
        if (prompt.length > maxChars) prompt.takeLast(maxChars) else prompt

    /** Deterministic template used when no LLM is loaded. */
    private fun templateInsight(prediction: PredictionResult): String {
        val directionText = when (prediction.direction) {
            "UP" -> "bullish upward movement"
            "DOWN" -> "bearish downward pressure"
            else -> "sideways consolidation"
        }
        val confidence = "%.0f".format(prediction.confidence * 100)
        return "${prediction.symbol} shows signs of $directionText with $confidence% model confidence. " +
               "The predicted price target is ${"%.2f".format(prediction.predictedPrice)}. " +
               "Consider monitoring volume and market conditions before acting."
    }

    private fun tryLoadNativeLib(): Boolean = try {
        System.loadLibrary("llama")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.i(TAG, "llama native library not linked – template mode active")
        false
    }

    private fun buildChatPrompt(userMessage: String, symbol: String, recentPrices: List<Double>): String {
        val priceStr = recentPrices.takeLast(5).joinToString(", ") { "%.2f".format(it) }
        return """
You are a financial analyst AI assistant. Answer the user's question concisely.

Stock: $symbol
Recent prices: $priceStr

User: $userMessage

Answer:""".trimIndent()
    }

    /** Template chat response when LLM is not available. */
    private fun templateChatResponse(userMessage: String, symbol: String): String {
        val lower = userMessage.lowercase()
        return when {
            lower.contains("buy") || lower.contains("sell") ->
                "Based on available data for $symbol, I recommend monitoring key indicators like RSI and MACD before making buy/sell decisions. Always consider your risk tolerance and investment horizon."
            lower.contains("price") || lower.contains("target") ->
                "For $symbol price targets, the prediction engine uses historical momentum and adaptive learning weights. Check the Prediction tab for the latest ML-based price forecast."
            lower.contains("risk") || lower.contains("safe") ->
                "$symbol risk assessment depends on volatility, sector trends, and market conditions. Diversification and position sizing are key risk management strategies."
            lower.contains("news") || lower.contains("event") ->
                "For the latest news on $symbol, I recommend checking verified financial sources. The sentiment engine weighs SEBI filings (1.5x) and exchange releases (1.3x) most heavily."
            else ->
                "Regarding $symbol: I can help with price analysis, predictions, risk assessment, and market insights. The local LLM agent provides deeper analysis when the BitNet model is loaded."
        }
    }

    private fun preferredModelFile(mode: QualityMode): File {
        val bundled = downloader.modelFileFor(mode)
        if (bundled.exists()) {
            return bundled
        }

        val imported = File(downloader.modelsDir, BitNetModelDownloader.IMPORTED_MODEL_FILE_NAME)
        return if (imported.exists()) imported else bundled
    }

    private data class CachedInsight(val text: String, val timestamp: Long)
}
