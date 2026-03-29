package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.model.PredictionResult
import com.stocksense.app.data.repository.StockRepository
import com.stocksense.app.engine.AgenticMetrics
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.engine.ModelManager
import com.stocksense.app.engine.QualityMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** A single message in the chat conversation. */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class InsightsUiState(
    val symbol: String = "",
    val insight: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val qualityMode: QualityMode = QualityMode.BALANCED,
    val llmStatus: LlmStatus = LlmStatus.NATIVE_UNAVAILABLE,
    val metrics: AgenticMetrics = AgenticMetrics(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val isChatLoading: Boolean = false
)

class InsightsViewModel(
    private val stockRepository: StockRepository,
    private val modelManager: ModelManager
) : ViewModel() {

    companion object {
        private const val MAX_CHAT_HISTORY = 50
    }

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        refreshMetrics()
    }

    fun generateInsight(symbol: String, prediction: PredictionResult) {
        _uiState.update { it.copy(symbol = symbol, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val history = stockRepository.getRecentHistory(symbol, 10)
                if (history.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            insight = "",
                            isLoading = false,
                            error = "Recent price history is unavailable for $symbol."
                        )
                    }
                    return@launch
                }
                val prices = history.map { it.close }
                modelManager.ensureLoaded()
                val mode = modelManager.llmEngine.currentQualityMode()
                // Always call generateInsight() — it uses template fallback internally
                // when the model is not loaded, so the user always gets a useful response.
                val insight = modelManager.llmEngine.generateInsight(prediction, prices)
                val metrics = modelManager.llmEngine.getMetrics()
                val status = metrics.status
                modelManager.markUsed()
                _uiState.update {
                    it.copy(
                        insight = insight,
                        isLoading = false,
                        qualityMode = mode,
                        llmStatus = status,
                        metrics = metrics
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun sendChatMessage(userMessage: String) {
        if (userMessage.isBlank()) return
        val symbol = _uiState.value.symbol
        val updatedMessages = (_uiState.value.chatMessages + ChatMessage(userMessage, isUser = true)).takeLast(MAX_CHAT_HISTORY)
        _uiState.update { it.copy(chatMessages = updatedMessages, isChatLoading = true) }

        viewModelScope.launch {
            try {
                val history = stockRepository.getRecentHistory(symbol, 10)
                if (history.isEmpty()) {
                    val errorMsg = ChatMessage("Recent market data for $symbol is unavailable right now.", isUser = false)
                    val withError = (_uiState.value.chatMessages + errorMsg).takeLast(MAX_CHAT_HISTORY)
                    _uiState.update { it.copy(chatMessages = withError, isChatLoading = false) }
                    return@launch
                }
                val prices = history.map { it.close }
                modelManager.ensureLoaded()
                // Always call llmEngine.chat() — it uses template fallback internally
                // when the model is not loaded, so the user always gets a useful response.
                val response = modelManager.llmEngine.chat(userMessage, symbol, prices)
                modelManager.markUsed()
                val metrics = modelManager.llmEngine.getMetrics()
                val status = metrics.status
                val withResponse = (_uiState.value.chatMessages + ChatMessage(response, isUser = false)).takeLast(MAX_CHAT_HISTORY)
                _uiState.update {
                    it.copy(
                        chatMessages = withResponse,
                        isChatLoading = false,
                        llmStatus = status,
                        metrics = metrics
                    )
                }
            } catch (e: Exception) {
                val errorMsg = ChatMessage("Sorry, I couldn't process that: ${e.message}", isUser = false)
                val withError = (_uiState.value.chatMessages + errorMsg).takeLast(MAX_CHAT_HISTORY)
                _uiState.update { it.copy(chatMessages = withError, isChatLoading = false) }
            }
        }
    }

    fun setQualityMode(mode: QualityMode) {
        viewModelScope.launch {
            modelManager.llmEngine.loadModel(mode)
            val status = modelManager.llmEngine.status
            val metrics = modelManager.llmEngine.getMetrics()
            _uiState.update { it.copy(qualityMode = mode, llmStatus = status, metrics = metrics) }
        }
    }

    fun refreshMetrics() {
        val status = modelManager.llmEngine.status
        val metrics = modelManager.llmEngine.getMetrics()
        _uiState.update { it.copy(llmStatus = status, metrics = metrics) }
    }
}

