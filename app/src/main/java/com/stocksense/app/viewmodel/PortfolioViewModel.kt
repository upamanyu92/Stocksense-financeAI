package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.database.dao.PortfolioHoldingDao
import com.stocksense.app.data.database.dao.TradeDao
import com.stocksense.app.data.database.entities.PortfolioHolding
import com.stocksense.app.data.database.entities.Trade
import com.stocksense.app.data.database.entities.TradeType
import com.stocksense.app.data.repository.StockRepository
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
    val error: String? = null
)

class PortfolioViewModel(
    private val portfolioHoldingDao: PortfolioHoldingDao,
    private val tradeDao: TradeDao,
    private val stockRepository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    init {
        observeHoldings()
        observeTrades()
    }

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
                .collect { trades ->
                    _uiState.update { it.copy(trades = trades) }
                }
        }
    }

    private suspend fun updateHoldingAfterTrade(
        symbol: String,
        tradeType: TradeType,
        quantity: Double,
        price: Double
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
                        existing.copy(
                            quantity = newQty,
                            avgBuyPrice = newAvg,
                            investedValue = newInvested,
                            currentValue = currentValue,
                            pnl = pnl,
                            pnlPercent = pnlPercent,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                } else {
                    val currentStock = stockRepository.getStock(symbol)
                    val currentPrice = currentStock?.currentPrice ?: price
                    val invested = quantity * price
                    val currentValue = quantity * currentPrice
                    val pnl = currentValue - invested
                    val pnlPercent = if (invested > 0) (pnl / invested) * 100 else 0.0
                    portfolioHoldingDao.insertOrUpdate(
                        PortfolioHolding(
                            symbol = symbol,
                            quantity = quantity,
                            avgBuyPrice = price,
                            currentPrice = currentPrice,
                            investedValue = invested,
                            currentValue = currentValue,
                            pnl = pnl,
                            pnlPercent = pnlPercent
                        )
                    )
                }
            }
            TradeType.SELL -> {
                if (existing != null) {
                    val newQty = existing.quantity - quantity
                    if (newQty <= 0) {
                        portfolioHoldingDao.deleteBySymbol(symbol)
                    } else {
                        val newInvested = newQty * existing.avgBuyPrice
                        val currentValue = newQty * existing.currentPrice
                        val pnl = currentValue - newInvested
                        val pnlPercent = if (newInvested > 0) (pnl / newInvested) * 100 else 0.0
                        portfolioHoldingDao.insertOrUpdate(
                            existing.copy(
                                quantity = newQty,
                                investedValue = newInvested,
                                currentValue = currentValue,
                                pnl = pnl,
                                pnlPercent = pnlPercent,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }
}
