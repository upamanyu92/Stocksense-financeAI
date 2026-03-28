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
            val symbols = items.map { it.symbol }
            val stocks = stockRepository.getStocks(symbols)
            val stockMap = stocks.associateBy { it.symbol }.toMutableMap()
            val displayNameMap = stocks.associate { it.symbol to it.name }.toMutableMap()
            val unresolvedSymbols = symbols.filterNot(stockMap::containsKey)
            val nseMatches = if (unresolvedSymbols.isEmpty()) {
                emptyList()
            } else {
                nseSecurityDao.getByCodes(unresolvedSymbols)
            }
            nseMatches.forEach { security ->
                displayNameMap.putIfAbsent(security.code, security.name)
            }
            _uiState.update { it.copy(stocks = stockMap, displayNames = displayNameMap) }
        }
    }
}
