package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.database.dao.NseSecurityDao
import com.stocksense.app.data.model.SearchResult
import com.stocksense.app.data.model.SearchResultType
import com.stocksense.app.data.model.StockData
import com.stocksense.app.data.repository.StockRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val totalCount: Int = 0,
    val isSearching: Boolean = false
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val nseSecurityDao: NseSecurityDao,
    private val stockRepository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var searchJob: Job? = null

    /** Filtered stocks from the stock table for the dashboard. */
    private val _filteredStocks = MutableStateFlow<List<StockData>>(emptyList())
    val filteredStocks: StateFlow<List<StockData>> = _filteredStocks.asStateFlow()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(query = "", results = emptyList(), totalCount = 0, isSearching = false) }
                        _filteredStocks.value = emptyList()
                    } else {
                        performSearch(query)
                    }
                }
        }
    }

    fun updateQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(query = query) }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.update { SearchUiState() }
        _filteredStocks.value = emptyList()
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isSearching = true) }

        val localStocks = stockRepository.observeAllStocks().first()
        val localSymbolLookup = buildLocalSymbolLookup(localStocks)
        val allResults = mutableListOf<SearchResult>()

        // Search NSE securities
        try {
            val nseResults = nseSecurityDao.search(query, limit = 50)
            val nseSearchResults = nseResults.map { nse ->
                val type = classifySecurityType(nse.name, nse.code)
                val matchSource = when {
                    nse.name.contains(query, ignoreCase = true) -> "Company Name"
                    nse.code.contains(query, ignoreCase = true) -> "Security Code"
                    nse.symbol.contains(query, ignoreCase = true) -> "Symbol"
                    else -> "Match"
                }
                val resolvedSymbol = resolveResultSymbol(
                    localSymbolLookup = localSymbolLookup,
                    fallbackSymbol = nse.code,
                    nseSymbol = nse.symbol,
                    displayName = nse.name
                )
                SearchResult(
                    displayName = nse.name,
                    symbol = resolvedSymbol,
                    code = nse.code,
                    type = type,
                    matchSource = matchSource
                )
            }
            allResults.addAll(nseSearchResults)
        } catch (_: Exception) { }

        // Also search stocks table
        try {
            localStocks.filter { stock ->
                stock.symbol.contains(query, ignoreCase = true) ||
                    stock.name.contains(query, ignoreCase = true)
            }.let { stocks ->
                _filteredStocks.value = stocks
                stocks.forEach { stock ->
                    // Avoid duplicate if already in NSE results
                    if (allResults.none { it.displayName.equals(stock.name, ignoreCase = true) }) {
                        allResults.add(
                            SearchResult(
                                displayName = stock.name,
                                symbol = stock.symbol,
                                code = stock.symbol,
                                type = SearchResultType.STOCK_SYMBOL,
                                matchSource = "Stock Data"
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) { }

        // Sort: exact match first, then prefix, then contains
        val sorted = allResults.sortedWith(
            compareByDescending<SearchResult> { it.displayName.equals(query, ignoreCase = true) }
                .thenByDescending { it.displayName.startsWith(query, ignoreCase = true) || it.code.startsWith(query, ignoreCase = true) }
                .thenBy { it.displayName }
        )

        val totalCount = sorted.size

        _uiState.update {
            it.copy(
                results = sorted.take(5),
                totalCount = totalCount,
                isSearching = false
            )
        }
    }

    /** Load full results for the SearchResultsScreen. */
    fun loadFullResults(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, query = query) }

            val localStocks = stockRepository.observeAllStocks().first()
            val localSymbolLookup = buildLocalSymbolLookup(localStocks)
            val allResults = mutableListOf<SearchResult>()
            try {
                val nseResults = nseSecurityDao.search(query, limit = 500)
                allResults.addAll(nseResults.map { nse ->
                    SearchResult(
                        displayName = nse.name,
                        symbol = resolveResultSymbol(
                            localSymbolLookup = localSymbolLookup,
                            fallbackSymbol = nse.code,
                            nseSymbol = nse.symbol,
                            displayName = nse.name
                        ),
                        code = nse.code,
                        type = classifySecurityType(nse.name, nse.code),
                        matchSource = when {
                            nse.name.contains(query, ignoreCase = true) -> "Company Name"
                            nse.code.contains(query, ignoreCase = true) -> "Security Code"
                            else -> "Match"
                        }
                    )
                })
            } catch (_: Exception) { }

            _uiState.update { it.copy(results = allResults, totalCount = allResults.size, isSearching = false) }
        }
    }

    private fun classifySecurityType(name: String, code: String): SearchResultType {
        val upper = name.uppercase()
        return when {
            upper.contains("ETF") || upper.contains("EXCHANGE TRADED") -> SearchResultType.ETF
            upper.contains("NIFTY") || upper.contains("SENSEX") || upper.contains("INDEX") -> SearchResultType.INDEX
            upper.contains("FUND") || upper.contains("MUTUAL") -> SearchResultType.OTHER
            else -> SearchResultType.COMPANY
        }
    }

    private fun resolveResultSymbol(
        localSymbolLookup: Map<String, String>,
        fallbackSymbol: String,
        nseSymbol: String,
        displayName: String
    ): String {
        if (nseSymbol.isNotBlank()) {
            return nseSymbol
        }

        return localSymbolLookup[displayName.uppercase()]
            ?: localSymbolLookup[fallbackSymbol.uppercase()]
            ?: fallbackSymbol
    }

    private fun buildLocalSymbolLookup(localStocks: List<StockData>): Map<String, String> =
        buildMap {
            localStocks.forEach { stock ->
                put(stock.name.uppercase(), stock.symbol)
                put(stock.symbol.uppercase(), stock.symbol)
            }
        }
}
