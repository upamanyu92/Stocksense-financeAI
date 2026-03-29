package com.stocksense.app.viewmodel

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.engine.BitNetModelDownloader
import com.stocksense.app.engine.QualityMode
import com.stocksense.app.preferences.UserPreferencesManager
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─── Setup model catalogue ───────────────────────────────────────────────────
// ModelOption data class is defined in LlmSettingsViewModel.kt (same package).
// No re-declaration here.

fun buildSetupModels(deviceRamGb: Int): List<ModelOption> {
    val all = listOf(
        ModelOption(
            name = "TinyLlama 1.1B (Q4_K_M)",
            description = "Ultra-light · fastest inference · 1.1B params",
            sizeLabel = "~0.8 GB",
            mode = QualityMode.LITE,
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            recommendedRamGb = 2,
            explanation = "TinyLlama 1.1B is purpose-built for constrained devices. With only 1.1 billion " +
                "parameters it loads in seconds and consumes minimal RAM, making it the go-to choice for " +
                "devices under 4 GB. For StockSense it handles quick price lookups, basic alert summaries, " +
                "and on-the-fly finance Q&A with impressive speed — ideal if you prioritise responsiveness " +
                "over analytical depth."
        ),
        ModelOption(
            name = "Phi-2 2.7B (Q4_K_M)",
            description = "Compact powerhouse · best speed-to-quality ratio · 2.7B params",
            sizeLabel = "~1.6 GB",
            mode = QualityMode.LITE,
            downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
            recommendedRamGb = 4,
            explanation = "Microsoft's Phi-2 punches well above its weight class. At 2.7B parameters with " +
                "Q4_K_M quantisation it delivers sharp reasoning comparable to models 3× its size. For " +
                "StockSense it powers nuanced market commentary, portfolio insight generation, and multi-step " +
                "financial reasoning — all fitting comfortably in 4–6 GB RAM. The sweet spot for most " +
                "Android flagships."
        ),
        ModelOption(
            name = "Llama-2 7B Chat (Q4_K_M)",
            description = "Rich contextual dialogue · comprehensive analysis · 7B params",
            sizeLabel = "~4.2 GB",
            mode = QualityMode.BALANCED,
            downloadUrl = "https://huggingface.co/TheBloke/Llama-2-7B-Chat-GGUF/resolve/main/llama-2-7b-chat.Q4_K_M.gguf",
            recommendedRamGb = 6,
            explanation = "Meta's Llama-2 7B Chat excels at multi-turn conversations and contextual reasoning. " +
                "For StockSense it generates detailed investment thesis breakdowns, sector analysis, and " +
                "portfolio narratives with far more nuance than smaller models. Requires 6 GB+ RAM. Response " +
                "latency is higher, but the quality of insight is substantially greater — recommended for " +
                "high-RAM devices where depth matters."
        ),
        ModelOption(
            name = "Mistral 7B Instruct v0.2 (Q4_K_M)",
            description = "State-of-the-art accuracy · best for power users · 7B params",
            sizeLabel = "~4.1 GB",
            mode = QualityMode.PRO,
            downloadUrl = "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf",
            recommendedRamGb = 6,
            explanation = "Mistral 7B Instruct v0.2 is one of the best open-source instruction-following LLMs " +
                "available and consistently outperforms Llama-2 7B on reasoning benchmarks. For StockSense it " +
                "enables precise financial modelling, earnings call summaries, risk assessment breakdowns, and " +
                "highly accurate AI-generated trading signals. Recommended for flagship devices with 8 GB+ RAM."
        )
    )
    val best = (all.filter { deviceRamGb >= it.recommendedRamGb }.maxByOrNull { it.recommendedRamGb }
        ?: all.first())
    return all.map { it.copy(recommended = it.name == best.name) }
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

class InitialSetupViewModel(
    private val downloader: BitNetModelDownloader,
    private val prefsManager: UserPreferencesManager,
    appContext: Context
) : ViewModel() {

    data class UiState(
        val availableModels: List<ModelOption> = emptyList(),
        val selectedModelIndex: Int = 0,
        val deviceRamGb: Int = 4,
        val isDownloading: Boolean = false,
        val downloadProgress: Float = 0f,
        val downloadSpeed: String = "—",
        val eta: String = "—",
        val downloadError: String? = null,
        val setupComplete: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        val ram = detectRamGb(appContext)
        val models = buildSetupModels(ram)
        val recIdx = models.indexOfFirst { it.recommended }.coerceAtLeast(0)
        _uiState.update { it.copy(availableModels = models, selectedModelIndex = recIdx, deviceRamGb = ram) }
    }

    fun selectModel(idx: Int) = _uiState.update { it.copy(selectedModelIndex = idx) }

    fun startDownload() {
        val state = _uiState.value
        if (state.isDownloading || state.availableModels.isEmpty()) return
        val model = state.availableModels[state.selectedModelIndex]

        _uiState.update {
            it.copy(isDownloading = true, downloadProgress = 0f,
                downloadSpeed = "—", eta = "—", downloadError = null)
        }

        viewModelScope.launch {
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            var lastSpeedDisplay = "—"
            var lastEtaDisplay = "—"

            val success = downloader.downloadWithProgress(model.downloadUrl) {
                progress: Float, bytesDownloaded: Long, totalBytes: Long ->

                val now = System.currentTimeMillis()
                val elapsedSec = (now - lastTime) / 1000.0
                if (elapsedSec >= 0.5) {
                    val speedMBps = (bytesDownloaded - lastBytes).toDouble() / elapsedSec / 1_048_576
                    val etaSec = if (speedMBps > 0.001 && totalBytes > 0)
                        ((totalBytes - bytesDownloaded) / (speedMBps * 1_048_576)).roundToInt() else -1
                    lastSpeedDisplay = if (speedMBps > 0.01) String.format(Locale.US, "%.2f", speedMBps) else "—"
                    lastEtaDisplay = formatEta(etaSec)
                    lastBytes = bytesDownloaded
                    lastTime = now
                }
                _uiState.update {
                    it.copy(downloadProgress = progress, downloadSpeed = lastSpeedDisplay, eta = lastEtaDisplay)
                }
            }

            if (success) {
                prefsManager.markInitialSetupComplete()
                _uiState.update { it.copy(isDownloading = false, downloadProgress = 1f, setupComplete = true) }
            } else {
                _uiState.update {
                    it.copy(isDownloading = false,
                        downloadError = "Download failed. Check your internet connection and try again.")
                }
            }
        }
    }

    fun retryDownload() = startDownload()

    fun skipSetup() {
        viewModelScope.launch {
            prefsManager.markInitialSetupComplete()
            _uiState.update { it.copy(setupComplete = true) }
        }
    }

    private fun detectRamGb(ctx: Context): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return (info.totalMem / 1_073_741_824L).toInt().coerceAtLeast(1)
    }

    private fun formatEta(seconds: Int): String = when {
        seconds <= 0 -> "—"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
