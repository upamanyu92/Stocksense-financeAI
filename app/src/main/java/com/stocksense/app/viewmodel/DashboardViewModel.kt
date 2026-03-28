package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.model.*
import com.stocksense.app.data.repository.StockRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

data class DashboardUiState(
    val stocks: List<StockData> = emptyList(),
    val filteredStocks: List<StockData> = emptyList(),
    val searchQuery: String = "",
    val portfolio: PortfolioSnapshot? = null,
    val predictions: List<AiPredictionCard> = emptyList(),
    val sentiment: SentimentSummary? = null,
    val debate: List<DebateMessage> = emptyList(),
    val indicatorTranslations: List<IndicatorTranslation> = emptyList(),
    val killNotices: List<KillCriteriaNotice> = emptyList(),
    val weightingRules: List<WeightingRule> = emptyList(),
    val stopWords: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class DashboardViewModel(
    private val stockRepository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        observeStocks()
        seedStaticContent()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(filteredStocks = it.stocks) }
        } else {
            viewModelScope.launch {
                stockRepository.searchStocks(query)
                    .collect { results ->
                        _uiState.update { it.copy(filteredStocks = results) }
                    }
            }
        }
    }

    private fun observeStocks() {
        viewModelScope.launch {
            stockRepository.observeAllStocks()
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { stocks ->
                    val snapshot = buildPortfolioSnapshot(stocks)
                    val predictions = buildPredictions(stocks)
                    _uiState.update {
                        it.copy(
                            stocks = stocks,
                            filteredStocks = if (it.searchQuery.isBlank()) stocks else it.filteredStocks,
                            portfolio = snapshot,
                            predictions = predictions,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    fun refresh() {
        observeStocks()
    }

    private fun seedStaticContent() {
        _uiState.update {
            it.copy(
                sentiment = SentimentSummary(
                    score = 72,
                    stance = SentimentStance.BULLISH,
                    headline = "Bullish momentum building",
                    detail = "Earnings beats in large-caps and softer inflation prints are lifting risk appetite."
                ),
                debate = listOf(
                    DebateMessage(DebateRole.BULL, "Banks leading breakout; credit growth resilient.", EvidenceGrade("High confidence", 5)),
                    DebateMessage(DebateRole.BEAR, "Valuations stretched; FIIs still cautious.", EvidenceGrade("Medium confidence", 3)),
                    DebateMessage(DebateRole.SKEPTIC, "Watch liquidity taper risk near RBI meet.", EvidenceGrade("Low confidence", 2))
                ),
                indicatorTranslations = translateIndicators(
                    mapOf(
                        "RSI" to "Overbought",
                        "MACD" to "Bullish cross",
                        "BOLL" to "Upper band touch"
                    )
                ),
                killNotices = buildKillNotices(),
                weightingRules = buildWeightingSchema(),
                stopWords = contextStopWords()
            )
        }
    }

    private fun buildPortfolioSnapshot(stocks: List<StockData>): PortfolioSnapshot? {
        if (stocks.isEmpty()) return null
        val total = stocks.sumOf { it.currentPrice }
        val dailyPnl = stocks.sumOf { (it.currentPrice - it.previousClose) }
        val base = max(total - dailyPnl, 1e-6)
        val percent = if (total == 0.0) 0.0 else (dailyPnl / base) * 100
        val level = when {
            percent > 5 -> "AI Trader"
            percent > 0 -> "Smart Investor"
            else -> "Market Learner"
        }
        return PortfolioSnapshot(
            totalValue = total,
            dailyPnl = dailyPnl,
            dailyPercent = percent,
            level = level,
            streakDays = 3
        )
    }

    private fun buildPredictions(stocks: List<StockData>): List<AiPredictionCard> {
        if (stocks.isEmpty()) return emptyList()
        return stocks.take(4).mapIndexed { index, stock ->
            val movement = when {
                stock.changePercent > 0.5 -> Movement.UP
                stock.changePercent < -0.5 -> Movement.DOWN
                else -> Movement.NEUTRAL
            }
            val confidence = (60 + abs(stock.changePercent) * 8).toInt().coerceIn(30, 95)
            AiPredictionCard(
                symbol = stock.symbol,
                name = stock.name,
                movement = movement,
                confidence = confidence,
                rationale = if (index % 2 == 0) {
                    "Momentum strengthening with steady volumes."
                } else {
                    "Range-bound; watch for breakout near resistance."
                }
            )
        }
    }

    private fun translateIndicators(source: Map<String, String>): List<IndicatorTranslation> {
        return source.map { (key, value) ->
            val plain = when (value.lowercase()) {
                "overbought" -> "Crowd is hyped; price could cool off soon."
                "oversold" -> "Market dumped it; a bounce could form."
                "bullish cross" -> "Fast line just crossed up; upside momentum brewing."
                "bearish cross" -> "Fast line crossed down; sellers gaining heat."
                "upper band touch" -> "Price is kissing the top band; move might be stretched."
                "lower band touch" -> "Price chilling at lower band; reversal chances rise."
                else -> "Indicator nudging a move; watch the next candles."
            }
            val why = when (value.lowercase()) {
                "overbought" -> "Protect gains; consider trailing stops."
                "oversold" -> "Risky bounce opportunity; size small."
                "bullish cross" -> "Momentum shift can start a short rally."
                "bearish cross" -> "Weakness may accelerate if volume jumps."
                "upper band touch" -> "Signals possible pullback after a sprint."
                "lower band touch" -> "Could mark exhaustion of sellers."
                else -> "Adds context so you react, not chase."
            }
            IndicatorTranslation(
                key = key,
                plainEnglish = plain,
                whyItMatters = why
            )
        }
    }

    private fun buildWeightingSchema(): List<WeightingRule> = listOf(
        WeightingRule("SEBI filings", 1.5),
        WeightingRule("Exchange releases (NSE/BSE)", 1.3),
        WeightingRule("Tier-1 financial media", 1.2),
        WeightingRule("Broker research", 1.0),
        WeightingRule("Social chatter (verified handles)", 0.7),
        WeightingRule("Generic social buzz", 0.5)
    )

    private fun contextStopWords(): List<String> = listOf(
        "rally", "strike", "election", "cabinet", "ipl", "bollywood", "budget rumor"
    )

    private fun buildKillNotices(): List<KillCriteriaNotice> = listOf(
        KillCriteriaNotice(
            title = "Thesis flipped on RELIANCE",
            body = "Hey, sentiment just turned negative. Want to review the Bear agent's take?",
            trustSignal = "Verified via NSE real-time feed"
        ),
        KillCriteriaNotice(
            title = "Cooldown active",
            body = "We’re holding orders for 15 minutes to avoid revenge trades during volatility.",
            trustSignal = "Risk control by StockSense AI"
        )
    )

    /** Basic sarcasm detector tailored to finance forums. */
    fun isSarcastic(text: String): Boolean {
        val lowered = text.lowercase()
        val sarcasmHints = listOf("/s", "sure, what a deal", "to the moon...not", "great, lost it all")
        return sarcasmHints.any { lowered.contains(it) }
    }
}
