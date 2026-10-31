package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.engine.LLMInsightEngine
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.engine.QualityMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    val gatewayUrl: String = "",
    val hasGatewayToken: Boolean = false,
    val connectionError: String? = null,
    val status: LlmStatus = LlmStatus.TEMPLATE_FALLBACK,
    val currentModelName: String = "",
    val isConfigured: Boolean = false,
    val isRunningLiveCheck: Boolean = false,
    val liveCheckMessage: String? = null
)

class LlmSettingsViewModel(private val llmEngine: LLMInsightEngine) : ViewModel() {
    private val _uiState = MutableStateFlow(LlmSettingsUiState())
    val uiState: StateFlow<LlmSettingsUiState> = _uiState.asStateFlow()
    init { refreshStatus() }
    fun refreshStatus() {
        _uiState.update { it.copy(gatewayUrl = llmEngine.gatewayUrl(), hasGatewayToken = llmEngine.hasGatewayToken(),
            status = llmEngine.status,
            currentModelName = llmEngine.getMetrics().displayModelName,
            isConfigured = llmEngine.isConfigured) }
    }
    fun saveConnection(url: String, token: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                llmEngine.configureGateway(url, token)
                _uiState.update { it.copy(connectionError = null, liveCheckMessage = "Connection saved on this device.") }
                refreshStatus()
            } catch (_: Exception) {
                _uiState.update { it.copy(connectionError = "Use a valid gateway URL and an access token of at least 32 characters.") }
            }
        }
    }
    fun runLiveCheck() {
        if (_uiState.value.isRunningLiveCheck) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningLiveCheck = true, liveCheckMessage = null) }
            try {
                val ok = llmEngine.runLiveCheck()
                refreshStatus()
                _uiState.update { it.copy(liveCheckMessage = if (ok) "AI service responded successfully."
                    else "AI service unavailable. Check gateway configuration and provider access.") }
            } finally {
                _uiState.update { it.copy(isRunningLiveCheck = false) }
            }
        }
    }
}
