package com.stocksense.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.engine.BitNetModelDownloader
import com.stocksense.app.engine.LLMInsightEngine
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.engine.QualityMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ModelOption(
    val name: String,
    val description: String,
    val sizeLabel: String,
    val mode: QualityMode,
    val fileName: String,
    val downloadUrl: String,
    val recommendedRamGb: Int,
    val recommended: Boolean = false,
    val explanation: String = ""
)

data class LlmSettingsUiState(
    val status: LlmStatus = LlmStatus.NATIVE_UNAVAILABLE,
    val currentModelName: String = "",
    val selectedModelIndex: Int = defaultSelectedModelIndex(),
    val activeDownloadModelName: String = "",
    val activeDownloadFileName: String = "",
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
    ModelOption(
        name = "SmolLM2 135M (Q4_K_M) · Nano",
        description = "Bundleable with APK · no download needed · 135M params",
        sizeLabel = "~80 MB",
        mode = QualityMode.LITE,
        fileName = "smollm2-135m-instruct-v0.2-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf",
        recommendedRamGb = 1,
        explanation = "Smallest model that can be bundled inside the APK (~80 MB). " +
            "Place the GGUF at app/src/main/assets/models/ for zero-download local testing."
    ),
    ModelOption(
        name = "Phi-2 (Q4_K_M)",
        description = "Efficient, strong reasoning (2.7B, Q4_K_M)",
        sizeLabel = "~1.6 GB",
        mode = QualityMode.LITE,
        fileName = "phi-2.Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
        recommendedRamGb = 4,
        recommended = true
    ),
    ModelOption(
        name = "TinyLlama-1.1B (Q4_K_M)",
        description = "Ultra-light, fast (1.1B, Q4_K_M)",
        sizeLabel = "~0.8 GB",
        mode = QualityMode.LITE,
        fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        recommendedRamGb = 2
    ),
    ModelOption(
        name = "Llama-2 7B (Q4_K_M)",
        description = "Richer dialogue, larger (7B, Q4_K_M)",
        sizeLabel = "~4.2 GB",
        mode = QualityMode.BALANCED,
        fileName = "llama-2-7b-chat.Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/Llama-2-7B-Chat-GGUF/resolve/main/llama-2-7b-chat.Q4_K_M.gguf",
        recommendedRamGb = 6
    ),
    ModelOption(
        name = "Mistral-7B (Q4_K_M)",
        description = "Efficient, accurate (7B, Q4_K_M)",
        sizeLabel = "~4.1 GB",
        mode = QualityMode.PRO,
        fileName = "mistral-7b-instruct-v0.2.Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf",
        recommendedRamGb = 6
    )
)

private fun defaultSelectedModelIndex(): Int = defaultModels().indexOfFirst { it.recommended }
    .let { if (it >= 0) it else 0 }

class LlmSettingsViewModel(
    private val downloader: BitNetModelDownloader,
    private val llmEngine: LLMInsightEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(LlmSettingsUiState())
    val uiState: StateFlow<LlmSettingsUiState> = _uiState.asStateFlow()
    private var downloadJob: Job? = null
    private var activeForegroundDownloadSessionId: Long? = null

    init {
        refreshStatus()
    }

    private fun nativeRuntimeMessage(): String =
        "This build was packaged without the on-device llama runtime. Install a native-enabled build to import, download, or validate GGUF models."

    private fun showNativeRuntimeError(showInImportSection: Boolean = false, showInLiveCheck: Boolean = false) {
        _uiState.update {
            it.copy(
                error = if (!showInImportSection && !showInLiveCheck) nativeRuntimeMessage() else null,
                importError = if (showInImportSection) nativeRuntimeMessage() else null,
                liveCheckMessage = if (showInLiveCheck) nativeRuntimeMessage() else null,
                isRunningLiveCheck = false
            )
        }
    }

    fun refreshStatus() {
        val metrics = llmEngine.getMetrics()
        _uiState.update {
            it.copy(
                status = metrics.status,
                currentModelName = metrics.displayModelName,
                isNativeAvailable = metrics.isNativeAvailable,
                isModelDownloaded = metrics.isModelDownloaded,
                lastInferenceTimeMs = metrics.lastInferenceTimeMs
            )
        }
    }

    fun selectModel(index: Int) {
        val state = _uiState.value
        if (index !in state.availableModels.indices || index == state.selectedModelIndex) return

        cancelActiveDownload(reason = "model_switch")
        downloader.cleanupTemporaryModelFiles()
        _uiState.update {
            it.copy(
                selectedModelIndex = index,
                downloadProgress = 0f,
                error = null,
                liveCheckMessage = null
            )
        }
        refreshStatus()
    }

    fun downloadSelectedModel() {
        val state = _uiState.value
        val model = state.availableModels.getOrNull(state.selectedModelIndex) ?: return
        if (!_uiState.value.isNativeAvailable) {
            showNativeRuntimeError()
            return
        }
        cancelActiveDownload(reason = "restart_download")
        val sessionId = downloader.prepareForegroundDownload(model.name, model.fileName)
        activeForegroundDownloadSessionId = sessionId
        _uiState.update {
            it.copy(
                isDownloading = true,
                downloadProgress = 0f,
                activeDownloadModelName = model.name,
                activeDownloadFileName = model.fileName,
                error = null,
                liveCheckMessage = null
            )
        }

        downloadJob = viewModelScope.launch {
            try {
                val success = downloader.downloadToFile(
                    fileName = model.fileName,
                    url = model.downloadUrl,
                    onProgress = { progress ->
                        if (activeForegroundDownloadSessionId == sessionId) {
                            _uiState.update {
                                it.copy(
                                    downloadProgress = progress,
                                    activeDownloadModelName = model.name,
                                    activeDownloadFileName = model.fileName
                                )
                            }
                        }
                    },
                    foregroundSessionId = sessionId
                )
                if (activeForegroundDownloadSessionId != sessionId) return@launch
                if (success) {
                    llmEngine.loadModel(File(downloader.modelsDir, model.fileName), model.mode)
                    refreshStatus()
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
                            activeDownloadModelName = "",
                            activeDownloadFileName = ""
                        )
                    }
                } else {
                    refreshStatus()
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            activeDownloadModelName = "",
                            activeDownloadFileName = "",
                            error = "Model download failed. The model file may be unavailable online. Please use 'Import Local Model' below to add a compatible GGUF file from your device."
                        )
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                if (activeForegroundDownloadSessionId != sessionId) return@launch
                val is404 = e.message?.contains("404") == true
                val msg = if (is404) {
                    "Model download failed (404 Not Found). The model file is not available online. Please use 'Import Local Model' below to add a compatible GGUF file from your device."
                } else {
                    "Download error: ${e.message}"
                }
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        activeDownloadModelName = "",
                        activeDownloadFileName = "",
                        error = msg
                    )
                }
            } finally {
                if (activeForegroundDownloadSessionId == sessionId) {
                    activeForegroundDownloadSessionId = null
                }
            }
        }
    }

    fun importLocalModel(context: Context, uri: Uri) {
        if (!_uiState.value.isNativeAvailable) {
            showNativeRuntimeError(showInImportSection = true)
            return
        }
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
        viewModelScope.launch {
            cancelActiveDownload(reason = "delete_models")
            llmEngine.unloadModel()
            downloader.clearModels()
            refreshStatus()
        }
    }

    fun reloadModel() {
        if (!_uiState.value.isNativeAvailable) {
            showNativeRuntimeError()
            return
        }
        viewModelScope.launch {
            llmEngine.loadModel()
            refreshStatus()
        }
    }

    fun runLiveCheck() {
        if (!_uiState.value.isNativeAvailable) {
            showNativeRuntimeError(showInLiveCheck = true)
            return
        }
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

    private fun cancelActiveDownload(reason: String) {
        downloadJob?.cancel()
        downloadJob = null
        activeForegroundDownloadSessionId?.let { downloader.cancelForegroundDownload(it, reason) }
        activeForegroundDownloadSessionId = null
        _uiState.update {
            it.copy(
                isDownloading = false,
                downloadProgress = 0f,
                activeDownloadModelName = "",
                activeDownloadFileName = ""
            )
        }
    }
}
