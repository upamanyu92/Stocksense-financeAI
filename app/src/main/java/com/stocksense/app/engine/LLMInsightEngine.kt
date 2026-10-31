package com.stocksense.app.engine

import android.content.Context
import com.stocksense.app.data.model.PredictionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import com.stocksense.app.BuildConfig
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit

/**
 * Inference status. Legacy enum names are retained for call-site compatibility.
 */
enum class LlmStatus {
    /** Legacy local-runtime status. */
    NATIVE_UNAVAILABLE,
    /** Legacy download status. */
    MODEL_NOT_DOWNLOADED,
    /** An API request is in progress. */
    LOADING,
    /** The last API request succeeded. */
    READY,
    /** The AI service failed. */
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
                lower.contains("phi-2")
                    || lower.contains("phi2")    -> "Phi-2 2.7B"
                lower.contains("mistral-7b-instruct-v0.2") -> "Mistral 7B Instruct v0.2"
                lower.contains("mistral")        -> "Mistral 7B"
                lower.contains("bitnet")
                    || lower == "ggml-model-i2_s.gguf" -> "BitNet b1.58 2B-4T"
                lower.contains("phi-3")          -> "Phi-3 Mini"
                lower.contains("llama-3")        -> "Llama 3"
                lower.contains("llama-2")        -> "Llama 2"
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
 * Legacy quality preferences retained for compatibility; gateway selects provider models.
 */
enum class QualityMode {
    /** Smaller 1-bit quantized model – fastest, lowest accuracy. */
    LITE,
    /** Default balanced 1-bit model. */
    BALANCED,
    /** Largest quantized model – best quality, requires ≥8 GB RAM. */
    PRO
}

/** NVIDIA/Hugging Face inference through our credential-holding gateway. */
class LLMInsightEngine(context: Context) {
    private val settings = GatewaySettings(context)
    fun gatewayUrl(): String = settings.read().url
    fun hasGatewayToken(): Boolean = settings.read().token.isNotBlank()
    fun configureGateway(url: String, token: String) {
        settings.save(url.trim(), token.ifBlank { settings.read().token })
        _statusFlow.value = LlmStatus.TEMPLATE_FALLBACK
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(100, TimeUnit.SECONDS)
        .callTimeout(110, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private var mode = QualityMode.BALANCED
    private var modelName = "Nemotron 3 Super (NVIDIA)"
    private var lastMs = 0L
    private var count = 0
    val isConfigured: Boolean get() = settings.read().let { it.url.isNotBlank() && it.token.isNotBlank() }
    private val _statusFlow = MutableStateFlow(LlmStatus.TEMPLATE_FALLBACK)
    val statusFlow: StateFlow<LlmStatus> = _statusFlow.asStateFlow()
    val status: LlmStatus get() = _statusFlow.value

    suspend fun ensureModelLoaded(mode: QualityMode = currentQualityMode()) { this.mode = mode }
    suspend fun loadModel(mode: QualityMode = currentQualityMode()) { this.mode = mode }
    suspend fun loadModel(@Suppress("UNUSED_PARAMETER") modelFile: File, mode: QualityMode = currentQualityMode()) {
        this.mode = mode
    }
    suspend fun unloadModel() { /* No local language model is retained. */ }
    fun currentQualityMode() = mode
    fun getMetrics() = AgenticMetrics(
        status = status, qualityMode = mode, modelFileName = modelName,
        lastInferenceTimeMs = lastMs, totalInferences = count
    )

    suspend fun generate(prompt: String, maxTokens: Int = 400, fallback: () -> String): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val start = System.currentTimeMillis()
                try {
                    check(isConfigured) { "AI service is not configured" }
                    val connection = settings.read()
                    val url = connection.url.trimEnd('/') + "/v1/chat/completions"
                    require(GatewayEndpoint.isAllowed(url, BuildConfig.DEBUG)) { "Invalid AI service endpoint" }
                    _statusFlow.value = LlmStatus.LOADING
                    val payload = buildJsonObject {
                        put("messages", buildJsonArray {
                            add(buildJsonObject {
                                put("role", "user")
                                put("content", prompt)
                            })
                        })
                        put("max_tokens", maxTokens.coerceIn(1, 2048))
                    }
                    val request = Request.Builder().url(url)
                        .header("Authorization", "Bearer " + connection.token)
                        .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                    client.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "AI service unavailable" }
                        val json = Json.parseToJsonElement(response.body.string()).jsonObject
                        val text = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                        check(!text.isNullOrBlank()) { "Empty AI response" }
                        modelName = json["model"]?.jsonPrimitive?.content ?: "Nemotron"
                        count++
                        _statusFlow.value = LlmStatus.READY
                        text
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Never expose request headers, provider responses, or credentials to UI/logs.
                    _statusFlow.value = LlmStatus.TEMPLATE_FALLBACK
                    "AI service unavailable. General template guidance:\n\n" + fallback()
                } finally {
                    lastMs = System.currentTimeMillis() - start
                }
            }
        }

    suspend fun generateInsight(prediction: PredictionResult, recentPrices: List<Double>): String =
        generate("""
            Explain this stock prediction in two sentences using only supplied data.
            Symbol: ${prediction.symbol}
            Closing prices: ${recentPrices.takeLast(30)}
            Direction: ${prediction.direction}; estimate: ${prediction.predictedPrice}
            Model confidence: ${prediction.confidence}. Do not describe confidence as proven accuracy.
        """.trimIndent()) {
            "${prediction.symbol}: ${prediction.direction}, estimated price ${prediction.predictedPrice}. " +
                "This estimate is uncertain; review current market data."
        }

    suspend fun chat(userMessage: String, symbol: String, recentPrices: List<Double>): String =
        generate("""
            Answer the finance question using the supplied context. Do not invent news or prices.
            Stock: $symbol; recent closing prices: ${recentPrices.takeLast(30)}
            Question: $userMessage
        """.trimIndent(), 800) { "Current analysis is unavailable for $symbol. Review prices, diversification and risk tolerance." }

    suspend fun analyzePortfolio(portfolioSummary: String): String = generate("""
        Analyse the supplied portfolio: concentration, diversification, risk and possible rebalancing.
        Ground every holding-specific statement in the data. Identify missing data; do not invent it.
        Use INR where appropriate. Explain uncertainty rather than promising returns.
        PORTFOLIO:
        $portfolioSummary
    """.trimIndent(), 1200) {
        "Review position sizes, sector concentration and investment horizons. Personalised analysis is currently unavailable."
    }

    suspend fun runLiveCheck(mode: QualityMode = currentQualityMode()): Boolean {
        this.mode = mode
        generate("Reply with OK only.", 128) { "" }
        return status == LlmStatus.READY
    }
}
