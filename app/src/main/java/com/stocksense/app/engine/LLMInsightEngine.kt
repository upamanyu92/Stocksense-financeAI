package com.stocksense.app.engine

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.stocksense.app.data.model.PredictionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
) {
    /** User-facing model name derived from the raw GGUF filename. */
    val displayModelName: String
        get() = friendlyModelName(modelFileName)

    companion object {
        /** Map known GGUF filenames to friendly names. */
        fun friendlyModelName(fileName: String): String {
            if (fileName.isBlank()) return ""
            val lower = fileName.lowercase()
            return when {
                lower.contains("smollm2-135m")   -> "SmolLM2 135M"
                lower.contains("smollm2")        -> "SmolLM2"
                lower.contains("bitnet")
                    || lower == "ggml-model-i2_s.gguf" -> "BitNet b1.58 2B-4T"
                lower.contains("phi-3")          -> "Phi-3 Mini"
                lower.contains("phi-2")          -> "Phi-2"
                lower.contains("llama-3")        -> "Llama 3"
                lower.contains("llama-2")        -> "Llama 2"
                lower.contains("mistral")        -> "Mistral"
                lower.contains("gemma")          -> "Gemma"
                lower.contains("qwen")           -> "Qwen"
                lower.contains("tinyllama")      -> "TinyLlama"
                else -> fileName.removeSuffix(".gguf")
                    .replace(Regex("[-_]"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
        }
    }
}

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

    @Volatile
    private var modelHandle: Long = 0L          // llama.cpp model pointer (0 = unloaded)
    @Volatile
    private var contextHandle: Long = 0L        // llama.cpp context pointer
    @Volatile
    private var currentMode: QualityMode? = null
    /** Filename of the actually loaded model (set after successful load). */
    @Volatile
    private var loadedModelFileName: String = ""
    private var isNativeAvailable = false

    /** Serialises all JNI calls — llama.cpp context is not thread-safe. */
    private val inferenceMutex = Mutex()

    /** Current status of the LLM agent. */
    @Volatile
    var status: LlmStatus = LlmStatus.NATIVE_UNAVAILABLE
        private set

    /** Tracks inference performance metrics. */
    @Volatile
    private var lastInferenceTimeMs: Long = 0L
    @Volatile
    private var cacheHitCount: Int = 0
    @Volatile
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

    /**
     * Ensure a model is loaded before inference.
     * No-op if the model is already [LlmStatus.READY].
     * Call sites that don't explicitly manage model lifecycle (e.g. CredenceAI
     * agents) use this to auto-load on first inference.
     */
    suspend fun ensureModelLoaded(mode: QualityMode = autoSelectMode()) {
        if (status == LlmStatus.READY && modelHandle != 0L && currentMode == mode) return
        loadModel(mode)
    }

    /** Get current agentic metrics snapshot. */
    fun getMetrics(): AgenticMetrics {
        val mode = currentMode ?: autoSelectMode()
        val modelFile = preferredModelFile(mode)
        // Use the actually loaded model name when available; fall back to preferred.
        val effectiveFileName = loadedModelFileName.ifBlank { modelFile.name }
        return AgenticMetrics(
            status = status,
            qualityMode = mode,
            isNativeAvailable = isNativeAvailable,
            isModelDownloaded = modelFile.exists(),
            modelFileName = effectiveFileName,
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
        inferenceMutex.withLock {
            if (currentMode == mode && modelHandle != 0L) return@withContext
            unloadModelInternal()
            currentMode = mode
            if (!isNativeAvailable) {
                Log.i(TAG, "Native library not available – using template fallback")
                status = LlmStatus.NATIVE_UNAVAILABLE
                return@withContext
            }
            val candidates = candidateModelFiles(mode)
            if (candidates.isEmpty()) {
                Log.w(TAG, "No model files found on disk – using fallback")
                status = LlmStatus.MODEL_NOT_DOWNLOADED
                return@withContext
            }
            status = LlmStatus.LOADING
            for (modelFile in candidates) {
                try {
                    Log.i(TAG, "Attempting to load model: ${modelFile.name}")
                    val loadedModelHandle = LlamaCpp.loadModel(modelFile.absolutePath, nGpuLayers(mode))
                    val loadedContextHandle = if (loadedModelHandle != 0L) {
                        LlamaCpp.createContext(loadedModelHandle, contextSizeFor(mode))
                    } else {
                        0L
                    }
                    if (loadedModelHandle == 0L || loadedContextHandle == 0L) {
                        Log.w(TAG, "Failed to load ${modelFile.name} – trying next candidate")
                        if (loadedModelHandle != 0L) {
                            try { LlamaCpp.freeModel(loadedModelHandle) } catch (_: Exception) {}
                        }
                        continue
                    }
                    modelHandle = loadedModelHandle
                    contextHandle = loadedContextHandle
                    loadedModelFileName = modelFile.name
                    status = LlmStatus.READY
                    Log.i(TAG, "BitNet LLM loaded: ${modelFile.name} (mode=$mode)")
                    return@withContext
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load ${modelFile.name}: ${e.message}")
                }
            }
            // All candidates exhausted
            modelHandle = 0L
            contextHandle = 0L
            status = LlmStatus.LOAD_FAILED
            Log.e(TAG, "All model candidates failed to load")
        }
    }

    /**
     * Unload the model and free native memory.
     */
    suspend fun unloadModel() {
        inferenceMutex.withLock { unloadModelInternal() }
    }

    /** Internal unload — caller MUST hold [inferenceMutex]. */
    private fun unloadModelInternal() {
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
        loadedModelFileName = ""
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

        ensureModelLoaded()
        val startTime = System.currentTimeMillis()

        val insight = if (modelHandle != 0L && isNativeAvailable) {
            val prompt = buildPrompt(prediction, recentPrices)
            val truncated = truncatePromptIfNeeded(prompt)
            inferenceMutex.withLock {
                try {
                    val result = LlamaCpp.runInference(contextHandle, truncated, maxTokens = 200)
                    totalInferenceCount++
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Inference error: ${e.message}")
                    status = LlmStatus.TEMPLATE_FALLBACK
                    templateInsight(prediction)
                }
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
        ensureModelLoaded()
        val startTime = System.currentTimeMillis()

        val response = if (modelHandle != 0L && isNativeAvailable) {
            val prompt = buildChatPrompt(userMessage, symbol, recentPrices)
            val truncated = truncatePromptIfNeeded(prompt)
            inferenceMutex.withLock {
                try {
                    val result = LlamaCpp.runInference(contextHandle, truncated, maxTokens = 300)
                    totalInferenceCount++
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Chat inference error: ${e.message}")
                    status = LlmStatus.TEMPLATE_FALLBACK
                    templateChatResponse(userMessage, symbol)
                }
            }
        } else {
            templateChatResponse(userMessage, symbol)
        }

        lastInferenceTimeMs = System.currentTimeMillis() - startTime
        response
    }

    fun currentQualityMode(): QualityMode = currentMode ?: autoSelectMode()

    /**
     * Run an arbitrary prompt through the LLM and return the response.
     * Falls back to [fallback] if the model is not loaded.
     */
    suspend fun generate(prompt: String, maxTokens: Int = 400, fallback: () -> String): String =
        withContext(Dispatchers.IO) {
            ensureModelLoaded()
            val startTime = System.currentTimeMillis()
            val result = if (modelHandle != 0L && isNativeAvailable) {
                val truncated = truncatePromptIfNeeded(prompt, maxChars = 1200)
                inferenceMutex.withLock {
                    try {
                        val r = LlamaCpp.runInference(contextHandle, truncated, maxTokens = maxTokens)
                        totalInferenceCount++
                        r
                    } catch (e: Exception) {
                        Log.e(TAG, "generate() inference error: ${e.message}")
                        status = LlmStatus.TEMPLATE_FALLBACK
                        fallback()
                    }
                }
            } else {
                fallback()
            }
            lastInferenceTimeMs = System.currentTimeMillis() - startTime
            result
        }

    /**
     * Analyse a user's imported portfolio and return actionable recommendations.
     * [portfolioSummary] is a compact text describing holdings, P&L, and categories.
     */
    suspend fun analyzePortfolio(portfolioSummary: String): String = withContext(Dispatchers.IO) {
        val prompt = """
You are SenseQuant, an expert financial advisor AI. Analyse the following portfolio and provide:
1. TOP 3 recommended exits (sell reasons)
2. TOP 3 holds (why to keep)
3. TOP 2 rebalancing suggestions
4. Overall risk rating (Low/Medium/High) with reason
Be concise. Use ₹ for Indian currency.

PORTFOLIO SUMMARY:
$portfolioSummary

ANALYSIS:""".trimIndent()

        generate(prompt, maxTokens = 500) {
            templatePortfolioAnalysis()
        }
    }

    private fun templatePortfolioAnalysis(): String {
        return """
Portfolio Analysis by SenseQuant AI (Template Mode – install LLM model for deeper analysis):

**Recommended Exits:**
• Review holdings with unrealised P&L below -20% — evaluate if thesis has changed.
• Sectoral/thematic funds with negative returns over 12 months may need rebalancing.
• Small-cap positions >20% of portfolio add concentrated risk.

**Strong Holds:**
• Gold ETF holdings act as inflation hedge — maintain allocation.
• Diversified large-cap / flexi-cap funds with positive XIRR — continue SIPs.
• Holdings with positive 1Y return trajectory — stay the course.

**Rebalancing Suggestions:**
• Reduce small-cap allocation if >30% of equity; shift some to flexi-cap.
• Increase gold/commodity exposure to 10-15% for better downside protection.

**Risk Rating: Medium**
Mixed equity + commodity allocation. Several sectoral bets increase concentration risk.
Install the SenseQuant AI model for personalised, data-driven recommendations.
""".trimIndent()
    }


    suspend fun runLiveCheck(mode: QualityMode = currentQualityMode()): Boolean = withContext(Dispatchers.IO) {
        loadModel(mode)
        if (status != LlmStatus.READY || contextHandle == 0L) {
            return@withContext false
        }

        val startTime = System.currentTimeMillis()
        return@withContext inferenceMutex.withLock {
            try {
                val output = LlamaCpp.runInference(contextHandle, LIVE_CHECK_PROMPT, maxTokens = 8).trim()
                lastInferenceTimeMs = System.currentTimeMillis() - startTime
                totalInferenceCount++
                val isHealthy = output.contains("OK", ignoreCase = true)
                status = if (isHealthy) LlmStatus.READY else LlmStatus.LOAD_FAILED
                isHealthy
            } catch (e: Exception) {
                Log.e(TAG, "Live check failed: ${e.message}")
                status = LlmStatus.LOAD_FAILED
                false
            }
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
        QualityMode.LITE -> 1024
        QualityMode.BALANCED -> 2048
        QualityMode.PRO -> 4096
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
        System.loadLibrary("llama_jni")
        true
    } catch (_: UnsatisfiedLinkError) {
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

    /**
     * Return the best available model file on disk, checked in priority order:
     *   1. Catalogue model for the requested [mode] (e.g. ggml-model-i2_s.gguf)
     *   2. User-imported GGUF
     *   3. Bundled SmolLM2 nano model (always works with llama.cpp Q4_K_M)
     */
    private fun preferredModelFile(mode: QualityMode): File {
        val catalogue = downloader.modelFileFor(mode)
        if (catalogue.exists()) return catalogue

        val imported = File(downloader.modelsDir, BitNetModelDownloader.IMPORTED_MODEL_FILE_NAME)
        if (imported.exists()) return imported

        val bundledSmolLm = File(downloader.modelsDir, BitNetModelDownloader.BUNDLED_MODEL_FILE)
        return if (bundledSmolLm.exists()) bundledSmolLm else catalogue
    }

    /**
     * Return all candidate model files in priority order for fallback loading.
     */
    private fun candidateModelFiles(mode: QualityMode): List<File> {
        val candidates = mutableListOf<File>()
        val catalogue = downloader.modelFileFor(mode)
        if (catalogue.exists()) candidates.add(catalogue)

        val imported = File(downloader.modelsDir, BitNetModelDownloader.IMPORTED_MODEL_FILE_NAME)
        if (imported.exists()) candidates.add(imported)

        val bundledSmolLm = File(downloader.modelsDir, BitNetModelDownloader.BUNDLED_MODEL_FILE)
        if (bundledSmolLm.exists() && bundledSmolLm.absolutePath != catalogue.absolutePath) {
            candidates.add(bundledSmolLm)
        }

        return candidates
    }

    private data class CachedInsight(val text: String, val timestamp: Long)
}
