package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.database.dao.NseSecurityDao
import com.stocksense.app.data.database.dao.WatchlistDao
import com.stocksense.app.data.database.entities.WatchlistItem
import com.stocksense.app.data.model.StockData
import com.stocksense.app.data.repository.StockRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WatchlistUiState(
    val watchlistItems: List<WatchlistItem> = emptyList(),
    val stocks: Map<String, StockData> = emptyMap(),
    val displayNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class WatchlistViewModel(
    private val watchlistDao: WatchlistDao,
    private val stockRepository: StockRepository,
    private val nseSecurityDao: NseSecurityDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init {
        observeWatchlist()
    }

    private fun observeWatchlist() {
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
            val newItem = WatchlistItem(
                symbol = symbol.uppercase(),
                displayOrder = order
            )
            watchlistDao.insert(newItem)
            refreshStockData(_uiState.value.watchlistItems + newItem)
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
            val displayNameMap = mutableMapOf<String, String>()
            for (item in items) {
                stockRepository.getStock(item.symbol)?.let { stock ->
                    stockMap[item.symbol] = stock
                    displayNameMap[item.symbol] = stock.name
                } ?: run {
                    nseSecurityDao.getByCode(item.symbol)?.name?.let { displayName ->
                        displayNameMap[item.symbol] = displayName
                    }
                }
            }
            _uiState.update { it.copy(stocks = stockMap, displayNames = displayNameMap) }
        }
    }
}
