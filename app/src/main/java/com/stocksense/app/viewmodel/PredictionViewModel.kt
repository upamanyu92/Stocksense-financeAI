package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.model.HistoryPoint
import com.stocksense.app.data.model.PredictionResult
import com.stocksense.app.data.repository.StockRepository
import com.stocksense.app.engine.ModelManager
import com.stocksense.app.workers.PredictionWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PredictionUiState(
    val symbol: String = "",
    val history: List<HistoryPoint> = emptyList(),
    val prediction: PredictionResult? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val warning: String? = null
)

class PredictionViewModel(
    private val stockRepository: StockRepository,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PredictionUiState())
    val uiState: StateFlow<PredictionUiState> = _uiState.asStateFlow()

    fun loadStock(symbol: String) {
        _uiState.update { it.copy(symbol = symbol, isLoading = true, error = null, warning = null) }
        viewModelScope.launch {
            val history = stockRepository.getRecentHistory(symbol, 60)
            _uiState.update { it.copy(history = history, isLoading = false) }
        }
    }

    fun runPrediction(symbol: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val history = stockRepository.getRecentHistory(symbol, 60)
                if (history.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            history = emptyList(),
                            prediction = null,
                            error = "No price history available for $symbol yet.",
                            warning = null,
                            isLoading = false
                        )
                    }
                    return@launch
                }

                modelManager.ensureLoaded()
                val result = modelManager.predictionEngine.predict(symbol, history)
                val warning = when {
                    !modelManager.predictionEngine.isBundledModelAvailable() ->
                        "On-device prediction model is not bundled in this build, so a heuristic estimate is shown."
                    !modelManager.predictionEngine.isModelLoaded() ->
                        "Prediction model failed to load, so a heuristic estimate is shown."
                    else -> null
                }
                modelManager.markUsed()
                _uiState.update {
                    it.copy(
                        prediction = result,
                        history = history,
                        warning = warning,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
}
