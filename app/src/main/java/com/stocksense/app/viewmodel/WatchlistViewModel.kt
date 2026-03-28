package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.database.dao.WatchlistDao
import com.stocksense.app.data.database.entities.WatchlistItem
import com.stocksense.app.data.model.StockData
import com.stocksense.app.data.repository.StockRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WatchlistUiState(
    val watchlistItems: List<WatchlistItem> = emptyList(),
    val stocks: Map<String, StockData> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class WatchlistViewModel(
    private val watchlistDao: WatchlistDao,
    private val stockRepository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init {
        observeWatchlist()
    }

    fun observeWatchlist() {
        viewModelScope.launch {
            watchlistDao.getAll()
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { items ->
                    _uiState.update { it.copy(watchlistItems = items, isLoading = false) }
                    refreshStockData(items)
                }
        }
    }

    fun addToWatchlist(symbol: String) {
        if (symbol.isBlank()) return
        viewModelScope.launch {
            val existing = watchlistDao.getBySymbol(symbol.uppercase())
            if (existing != null) return@launch
            val order = watchlistDao.count()
            watchlistDao.insert(
                WatchlistItem(
                    symbol = symbol.uppercase(),
                    displayOrder = order
                )
            )
        }
    }

    fun removeFromWatchlist(symbol: String) {
        viewModelScope.launch {
            watchlistDao.deleteBySymbol(symbol)
        }
    }

    private fun refreshStockData(items: List<WatchlistItem>) {
        viewModelScope.launch {
            val stockMap = mutableMapOf<String, StockData>()
            for (item in items) {
                stockRepository.getStock(item.symbol)?.let { stockMap[item.symbol] = it }
            }
            _uiState.update { it.copy(stocks = stockMap) }
        }
    }
}
