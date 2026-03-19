package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.model.PredictionResult
import com.stocksense.app.data.repository.StockRepository
import com.stocksense.app.engine.LLMInsightEngine
import com.stocksense.app.engine.ModelManager
import com.stocksense.app.engine.QualityMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class InsightsUiState(
    val symbol: String = "",
    val insight: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val qualityMode: QualityMode = QualityMode.BALANCED
)

class InsightsViewModel(
    private val stockRepository: StockRepository,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    fun generateInsight(symbol: String, prediction: PredictionResult) {
        _uiState.update { it.copy(symbol = symbol, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val history = stockRepository.getRecentHistory(symbol, 10)
                val prices = history.map { it.close }
                modelManager.ensureLoaded()
                val insight = modelManager.llmEngine.generateInsight(prediction, prices)
                modelManager.markUsed()
                val mode = modelManager.llmEngine.currentQualityMode()
                _uiState.update {
                    it.copy(insight = insight, isLoading = false, qualityMode = mode)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun setQualityMode(mode: QualityMode) {
        viewModelScope.launch {
            modelManager.llmEngine.loadModel(mode)
            _uiState.update { it.copy(qualityMode = mode) }
        }
    }
}
