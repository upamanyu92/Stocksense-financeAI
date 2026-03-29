package com.stocksense.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.database.dao.PortfolioHoldingDao
import com.stocksense.app.data.database.dao.TradeDao
import com.stocksense.app.data.database.entities.PortfolioHolding
import com.stocksense.app.data.database.entities.Trade
import com.stocksense.app.data.database.entities.TradeType
import com.stocksense.app.data.model.ImportedMFHolding
import com.stocksense.app.data.model.ImportedPortfolioSummary
import com.stocksense.app.data.model.ImportedStockHolding
import com.stocksense.app.data.repository.StockRepository
import com.stocksense.app.engine.LLMInsightEngine
import com.stocksense.app.util.PortfolioXlsxParser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PortfolioUiState(
    val holdings: List<PortfolioHolding> = emptyList(),
    val trades: List<Trade> = emptyList(),
    val totalValue: Double = 0.0,
    val totalInvested: Double = 0.0,
    val totalPnl: Double = 0.0,
    val totalPnlPercent: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null,
    // Import state
    val importedStocks: List<ImportedStockHolding> = emptyList(),
    val importedMFs: List<ImportedMFHolding> = emptyList(),
    val importedSummary: ImportedPortfolioSummary? = null,
    val importedStockSummary: ImportedPortfolioSummary? = null,
    val isImporting: Boolean = false,
    val importError: String? = null,
    // AI analysis
    val analysisResult: String = "",
    val isAnalysing: Boolean = false,
    val selectedTab: Int = 0  // 0=Holdings, 1=Imported, 2=Analysis
)

class PortfolioViewModel(
    private val portfolioHoldingDao: PortfolioHoldingDao,
    private val tradeDao: TradeDao,
    private val stockRepository: StockRepository,
    private val llmEngine: LLMInsightEngine? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    init {
        observeHoldings()
        observeTrades()
    }

    fun selectTab(tab: Int) = _uiState.update { it.copy(selectedTab = tab) }

    @Suppress("unused")
    fun refreshPortfolio() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val holdings = _uiState.value.holdings
            for (holding in holdings) {
                val stock = stockRepository.getStock(holding.symbol) ?: continue
                val currentValue = holding.quantity * stock.currentPrice
                val pnl = currentValue - holding.investedValue
                val pnlPercent = if (holding.investedValue > 0) (pnl / holding.investedValue) * 100 else 0.0
                portfolioHoldingDao.insertOrUpdate(
                    holding.copy(
                        currentPrice = stock.currentPrice,
                        currentValue = currentValue,
                        pnl = pnl,
                        pnlPercent = pnlPercent,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun recordTrade(symbol: String, tradeType: TradeType, quantity: Double, price: Double) {
        if (symbol.isBlank() || quantity <= 0 || price <= 0) return
        viewModelScope.launch {
            val trade = Trade(
                symbol = symbol.uppercase(),
                tradeType = tradeType,
                quantity = quantity,
                price = price,
                totalValue = quantity * price
            )
            tradeDao.insertTrade(trade)
            updateHoldingAfterTrade(symbol.uppercase(), tradeType, quantity, price)
        }
    }

    // ── XLSX Import ───────────────────────────────────────────────────────────

    fun importFromXlsx(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }
            try {
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "holdings.xlsx"
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: run {
                        _uiState.update { it.copy(isImporting = false, importError = "Could not open file") }
                        return@launch
                    }
                val result = inputStream.use { PortfolioXlsxParser.parse(it, fileName) }
                when (result) {
                    is PortfolioXlsxParser.ParseResult.MutualFunds -> {
                        _uiState.update {
                            it.copy(
                                isImporting = false,
                                importedMFs = result.holdings,
                                importedSummary = result.summary,
                                selectedTab = 1
                            )
                        }
                    }
                    is PortfolioXlsxParser.ParseResult.Stocks -> {
                        _uiState.update {
                            it.copy(
                                isImporting = false,
                                importedStocks = result.holdings,
                                importedStockSummary = result.summary,
                                selectedTab = 1
                            )
                        }
                    }
                    is PortfolioXlsxParser.ParseResult.Error -> {
                        _uiState.update { it.copy(isImporting = false, importError = result.message) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, importError = "Import failed: ${e.message}") }
            }
        }
    }

    // ── AI Portfolio Analysis ─────────────────────────────────────────────────

    fun analyzePortfolio() {
        val state = _uiState.value
        if (state.isAnalysing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalysing = true, selectedTab = 2) }
            val summary = buildPortfolioSummary(state)
            if (summary.isBlank()) {
                _uiState.update {
                    it.copy(isAnalysing = false,
                        analysisResult = "Please import a holdings statement first to get AI analysis.")
                }
                return@launch
            }
            try {
                val engine = llmEngine
                val analysis = if (engine != null) {
                    engine.loadModel()
                    engine.analyzePortfolio(summary)
                } else {
                    buildTemplateAnalysis(state)
                }
                _uiState.update { it.copy(isAnalysing = false, analysisResult = analysis) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAnalysing = false,
                        analysisResult = "Analysis failed: ${e.message}\n\n${buildTemplateAnalysis(state)}")
                }
            }
        }
    }

    private fun buildPortfolioSummary(state: PortfolioUiState): String {
        val sb = StringBuilder()

        // Manual holdings
        if (state.holdings.isNotEmpty()) {
            sb.appendLine("=== Manual Holdings ===")
            sb.appendLine("Total Value: ₹${"%.2f".format(state.totalValue)}")
            sb.appendLine("Total Invested: ₹${"%.2f".format(state.totalInvested)}")
            sb.appendLine("P&L: ₹${"%.2f".format(state.totalPnl)} (${"%.2f".format(state.totalPnlPercent)}%)")
            sb.appendLine()
            state.holdings.forEach { h ->
                sb.appendLine("${h.symbol}: qty=${h.quantity}, avg=₹${h.avgBuyPrice}, current=₹${h.currentPrice}, P&L=${h.pnlPercent.let { "${"%.2f".format(it)}%" }}")
            }
            sb.appendLine()
        }

        // Imported stocks
        if (state.importedStocks.isNotEmpty()) {
            val s = state.importedStockSummary
            sb.appendLine("=== Imported Stock Holdings ===")
            if (s != null) {
                sb.appendLine("Invested: ₹${"%.2f".format(s.totalInvested)}, Current: ₹${"%.2f".format(s.currentValue)}, P&L: ₹${"%.2f".format(s.pnl)} (${"%.2f".format(s.pnlPercent)}%)")
            }
            state.importedStocks.forEach { h ->
                sb.appendLine("${h.stockName}: qty=${h.quantity}, avg=₹${h.avgBuyPrice}, closing=₹${h.closingPrice}, unrealised P&L=₹${h.unrealisedPnl} (${"%.2f".format(h.pnlPercent)}%)")
            }
            sb.appendLine()
        }

        // Imported MFs
        if (state.importedMFs.isNotEmpty()) {
            val s = state.importedSummary
            sb.appendLine("=== Imported Mutual Fund Holdings ===")
            if (s != null) {
                sb.appendLine("Invested: ₹${"%.2f".format(s.totalInvested)}, Current: ₹${"%.2f".format(s.currentValue)}, P&L: ₹${"%.2f".format(s.pnl)} (${"%.2f".format(s.pnlPercent)}%)")
            }
            // Group by category
            state.importedMFs.groupBy { it.category }.forEach { (cat, items) ->
                sb.appendLine("$cat:")
                items.forEach { mf ->
                    sb.appendLine("  ${mf.schemeName}: invested=₹${mf.investedValue}, current=₹${mf.currentValue}, returns=₹${mf.returns}, XIRR=${mf.xirr}")
                }
            }
        }
        return sb.toString()
    }

    private fun buildTemplateAnalysis(state: PortfolioUiState): String {
        val totalStocks = state.importedStocks.size + state.holdings.size
        val totalMFs = state.importedMFs.size
        val losers = state.importedStocks.filter { it.pnlPercent < -15 }
        val winners = state.importedStocks.filter { it.pnlPercent > 10 }
        val mfLosers = state.importedMFs.filter { it.pnlPercent < -10 }

        return buildString {
            appendLine("**SenseQuant Portfolio Analysis (Template Mode)**")
            appendLine()
            appendLine("Portfolio: $totalStocks stocks, $totalMFs mutual funds")
            appendLine()
            if (losers.isNotEmpty()) {
                appendLine("**Review for Exit (>15% loss):**")
                losers.take(3).forEach { appendLine("• ${it.stockName}: ${"%.1f".format(it.pnlPercent)}%") }
                appendLine()
            }
            if (winners.isNotEmpty()) {
                appendLine("**Strong Performers (>10% gain):**")
                winners.take(3).forEach { appendLine("• ${it.stockName}: +${"%.1f".format(it.pnlPercent)}%") }
                appendLine()
            }
            if (mfLosers.isNotEmpty()) {
                appendLine("**MF Underperformers:**")
                mfLosers.take(3).forEach { appendLine("• ${it.schemeName}: ${"%.1f".format(it.pnlPercent)}%") }
                appendLine()
            }
            appendLine("**Recommendation:** Install the SenseQuant AI model (LLM Settings) for in-depth personalised recommendations based on current market conditions.")
        }
    }

    // ── existing internal methods ─────────────────────────────────────────────

    private fun observeHoldings() {
        viewModelScope.launch {
            portfolioHoldingDao.getAll()
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { holdings ->
                    val totalValue = holdings.sumOf { it.currentValue }
                    val totalInvested = holdings.sumOf { it.investedValue }
                    val totalPnl = totalValue - totalInvested
                    val totalPnlPercent = if (totalInvested > 0) (totalPnl / totalInvested) * 100 else 0.0
                    _uiState.update {
                        it.copy(
                            holdings = holdings,
                            totalValue = totalValue,
                            totalInvested = totalInvested,
                            totalPnl = totalPnl,
                            totalPnlPercent = totalPnlPercent,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun observeTrades() {
        viewModelScope.launch {
            tradeDao.getAllTrades()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { trades -> _uiState.update { it.copy(trades = trades) } }
        }
    }

    private suspend fun updateHoldingAfterTrade(
        symbol: String, tradeType: TradeType, quantity: Double, price: Double
    ) {
        val existing = portfolioHoldingDao.getBySymbol(symbol)
        when (tradeType) {
            TradeType.BUY -> {
                if (existing != null) {
                    val newQty = existing.quantity + quantity
                    val newInvested = existing.investedValue + (quantity * price)
                    val newAvg = newInvested / newQty
                    val currentValue = newQty * existing.currentPrice
                    val pnl = currentValue - newInvested
                    val pnlPercent = if (newInvested > 0) (pnl / newInvested) * 100 else 0.0
                    portfolioHoldingDao.insertOrUpdate(
                        existing.copy(quantity = newQty, avgBuyPrice = newAvg, investedValue = newInvested,
                            currentValue = currentValue, pnl = pnl, pnlPercent = pnlPercent,
                            lastUpdated = System.currentTimeMillis())
                    )
                } else {
                    val currentStock = stockRepository.getStock(symbol)
                    val currentPrice = currentStock?.currentPrice ?: price
                    val invested = quantity * price
                    val currentValue = quantity * currentPrice
                    val pnl = currentValue - invested
                    val pnlPercent = if (invested > 0) (pnl / invested) * 100 else 0.0
                    portfolioHoldingDao.insertOrUpdate(
                        PortfolioHolding(symbol = symbol, quantity = quantity, avgBuyPrice = price,
                            currentPrice = currentPrice, investedValue = invested,
                            currentValue = currentValue, pnl = pnl, pnlPercent = pnlPercent)
                    )
                }
            }
            TradeType.SELL -> {
                if (existing != null && quantity <= existing.quantity) {
                    val newQty = existing.quantity - quantity
                    if (newQty <= 0) {
                        portfolioHoldingDao.deleteBySymbol(symbol)
                    } else {
                        val newInvested = newQty * existing.avgBuyPrice
                        val currentValue = newQty * existing.currentPrice
                        val pnl = currentValue - newInvested
                        val pnlPercent = if (newInvested > 0) (pnl / newInvested) * 100 else 0.0
                        portfolioHoldingDao.insertOrUpdate(
                            existing.copy(quantity = newQty, investedValue = newInvested,
                                currentValue = currentValue, pnl = pnl, pnlPercent = pnlPercent,
                                lastUpdated = System.currentTimeMillis())
                        )
                    }
                }
            }
        }
    }
}
