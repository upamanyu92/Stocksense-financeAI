package com.stocksense.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.engine.BitNetModelDownloader
import com.stocksense.app.engine.LLMInsightEngine
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.engine.QualityMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ModelOption(
    val name: String,
    val description: String,
    val sizeLabel: String,
    val mode: QualityMode
)

data class LlmSettingsUiState(
    val status: LlmStatus = LlmStatus.NATIVE_UNAVAILABLE,
    val currentModelName: String = "",
    val isNativeAvailable: Boolean = false,
    val isModelDownloaded: Boolean = false,
    val lastInferenceTimeMs: Long = 0L,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isImporting: Boolean = false,
    val isRunningLiveCheck: Boolean = false,
    val liveCheckMessage: String? = null,
    val importError: String? = null,
    val error: String? = null,
    val availableModels: List<ModelOption> = defaultModels()
)

private fun defaultModels() = listOf(
    ModelOption("BitNet b1.58 2B TQ1_0 (Lite)", "Low RAM usage, fastest inference", "~400 MB", QualityMode.LITE),
    ModelOption("BitNet b1.58 2B TQ2_0 (Balanced)", "Good quality/speed balance", "~600 MB", QualityMode.BALANCED),
    ModelOption("BitNet b1.58 2B Q4_0 (Pro)", "Best quality, requires ≥8 GB RAM", "~1.2 GB", QualityMode.PRO)
)

class LlmSettingsViewModel(
    private val downloader: BitNetModelDownloader,
    private val llmEngine: LLMInsightEngine,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LlmSettingsUiState())
    val uiState: StateFlow<LlmSettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        val metrics = llmEngine.getMetrics()
        _uiState.update {
            it.copy(
                status = metrics.status,
                currentModelName = metrics.modelFileName,
                isNativeAvailable = metrics.isNativeAvailable,
                isModelDownloaded = metrics.isModelDownloaded,
                lastInferenceTimeMs = metrics.lastInferenceTimeMs
            )
        }
    }

    fun downloadModel(mode: QualityMode) {
        if (_uiState.value.isDownloading) return
        _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, error = null) }

        viewModelScope.launch {
            try {
                val success = downloader.download(mode) { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
                if (success) {
                    llmEngine.loadModel(mode)
                    refreshStatus()
                } else {
                    _uiState.update { it.copy(error = "Download failed. Check your internet connection.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Download error: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isDownloading = false) }
            }
        }
    }

    fun importLocalModel(uri: Uri) {
        _uiState.update { it.copy(isImporting = true, importError = null, error = null) }

        viewModelScope.launch {
            try {
                val targetDir = downloader.modelsDir
                val targetFile = File(targetDir, BitNetModelDownloader.IMPORTED_MODEL_FILE_NAME)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    // Validate GGUF magic bytes: 0x47 0x47 0x55 0x46 ("GGUF")
                    val header = ByteArray(4)
                    val bytesRead = input.read(header)
                    val isGguf = bytesRead == 4 &&
                        header[0] == 0x47.toByte() &&
                        header[1] == 0x47.toByte() &&
                        header[2] == 0x55.toByte() &&
                        header[3] == 0x46.toByte()
                    if (!isGguf) {
                        _uiState.update {
                            it.copy(isImporting = false, importError = "Invalid file: not a GGUF model file")
                        }
                        return@launch
                    }

                    // Copy file
                    val tmpFile = File(targetDir, "${BitNetModelDownloader.IMPORTED_MODEL_FILE_NAME}.tmp")
                    tmpFile.outputStream().use { output ->
                        output.write(header)
                        input.copyTo(output, bufferSize = 8192)
                    }
                    if (!tmpFile.renameTo(targetFile)) {
                        tmpFile.delete()
                        _uiState.update {
                            it.copy(isImporting = false, importError = "Failed to save model file")
                        }
                        return@launch
                    }
                } ?: run {
                    _uiState.update {
                        it.copy(isImporting = false, importError = "Could not open selected file")
                    }
                    return@launch
                }

                llmEngine.loadModel()
                refreshStatus()
                _uiState.update { it.copy(isImporting = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isImporting = false, importError = "Import failed: ${e.message}")
                }
            }
        }
    }

    fun deleteModels() {
        llmEngine.unloadModel()
        downloader.clearModels()
        refreshStatus()
    }

    fun reloadModel() {
        viewModelScope.launch {
            llmEngine.loadModel()
            refreshStatus()
        }
    }

    fun runLiveCheck() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRunningLiveCheck = true, liveCheckMessage = null, error = null) }
                val ok = llmEngine.runLiveCheck()
                refreshStatus()
                _uiState.update {
                    it.copy(
                        isRunningLiveCheck = false,
                        liveCheckMessage = if (ok) {
                            "Agent responded successfully."
                        } else {
                            "Agent is not live yet. Check runtime and model status."
                        }
                    )
                }
            } catch (e: Exception) {
                refreshStatus()
                _uiState.update {
                    it.copy(
                        isRunningLiveCheck = false,
                        error = e.message
                    )
                }
            }
        }
    }
}
