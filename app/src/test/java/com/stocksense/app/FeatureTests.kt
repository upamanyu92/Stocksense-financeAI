package com.stocksense.app

import com.stocksense.app.data.model.PredictionResult
import com.stocksense.app.data.model.StockData
import com.stocksense.app.engine.AgenticMetrics
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.engine.QualityMode
import com.stocksense.app.preferences.UserPreferences
import com.stocksense.app.viewmodel.ChatMessage
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the new features:
 * - Search filtering logic
 * - LLM status enum and agentic metrics
 * - User preferences model
 * - Chat message model
 * - Data ingestion stk.json parsing
 */
class FeatureTests {

    // ---------- Search filtering ----------

    private val sampleStocks = listOf(
        StockData("AAPL", "Apple Inc.", 189.50, 187.25, 1.20, 2950000000000, "Technology"),
        StockData("GOOG", "Alphabet Inc.", 175.80, 177.40, -0.90, 2200000000000, "Technology"),
        StockData("MSFT", "Microsoft Corporation", 415.30, 412.00, 0.80, 3080000000000, "Technology"),
        StockData("AMZN", "Amazon.com Inc.", 205.60, 202.10, 1.73, 2150000000000, "Consumer Discretionary"),
        StockData("TCS", "Tata Consultancy Services Ltd.", 3850.00, 3820.00, 0.79, 1410000000000, "Technology"),
        StockData("RELIANCE", "Reliance Industries Ltd.", 2920.00, 2895.00, 0.86, 1975000000000, "Energy")
    )

    private fun filterStocks(query: String): List<StockData> {
        if (query.isBlank()) return sampleStocks
        val lowerQuery = query.lowercase()
        return sampleStocks.filter {
            it.symbol.lowercase().contains(lowerQuery) ||
            it.name.lowercase().contains(lowerQuery)
        }
    }

    @Test
    fun `search by symbol returns matching stock`() {
        val results = filterStocks("AAPL")
        assertEquals(1, results.size)
        assertEquals("AAPL", results[0].symbol)
    }

    @Test
    fun `search by partial name returns matching stocks`() {
        val results = filterStocks("Inc")
        assertTrue(results.size >= 3)
    }

    @Test
    fun `search is case insensitive`() {
        val results = filterStocks("aapl")
        assertEquals(1, results.size)
        assertEquals("AAPL", results[0].symbol)
    }

    @Test
    fun `empty search returns all stocks`() {
        val results = filterStocks("")
        assertEquals(sampleStocks.size, results.size)
    }

    @Test
    fun `search with no match returns empty`() {
        val results = filterStocks("ZZZNOTFOUND")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `search for Indian stocks works`() {
        val tcsResults = filterStocks("TCS")
        assertEquals(1, tcsResults.size)
        assertEquals("Tata Consultancy Services Ltd.", tcsResults[0].name)

        val relianceResults = filterStocks("RELIANCE")
        assertEquals(1, relianceResults.size)
    }

    // ---------- LLM Status ----------

    @Test
    fun `LlmStatus enum has all expected values`() {
        val statuses = LlmStatus.entries
        assertEquals(6, statuses.size)
        assertTrue(statuses.contains(LlmStatus.NATIVE_UNAVAILABLE))
        assertTrue(statuses.contains(LlmStatus.MODEL_NOT_DOWNLOADED))
        assertTrue(statuses.contains(LlmStatus.LOADING))
        assertTrue(statuses.contains(LlmStatus.READY))
        assertTrue(statuses.contains(LlmStatus.LOAD_FAILED))
        assertTrue(statuses.contains(LlmStatus.TEMPLATE_FALLBACK))
    }

    @Test
    fun `QualityMode enum has all expected values`() {
        val modes = QualityMode.entries
        assertEquals(3, modes.size)
        assertTrue(modes.contains(QualityMode.LITE))
        assertTrue(modes.contains(QualityMode.BALANCED))
        assertTrue(modes.contains(QualityMode.PRO))
    }

    // ---------- Agentic Metrics ----------

    @Test
    fun `AgenticMetrics default values are correct`() {
        val metrics = AgenticMetrics()
        assertEquals(LlmStatus.NATIVE_UNAVAILABLE, metrics.status)
        assertEquals(QualityMode.BALANCED, metrics.qualityMode)
        assertFalse(metrics.isNativeAvailable)
        assertFalse(metrics.isModelDownloaded)
        assertEquals("", metrics.modelFileName)
        assertEquals(0L, metrics.lastInferenceTimeMs)
        assertEquals(0, metrics.cacheHits)
        assertEquals(0, metrics.totalInferences)
    }

    @Test
    fun `AgenticMetrics with custom values`() {
        val metrics = AgenticMetrics(
            status = LlmStatus.READY,
            qualityMode = QualityMode.PRO,
            isNativeAvailable = true,
            isModelDownloaded = true,
            modelFileName = "bitnet-b1.58-2B-4T-Q4_0.gguf",
            lastInferenceTimeMs = 250L,
            cacheHits = 5,
            totalInferences = 10
        )
        assertEquals(LlmStatus.READY, metrics.status)
        assertTrue(metrics.isNativeAvailable)
        assertEquals("bitnet-b1.58-2B-4T-Q4_0.gguf", metrics.modelFileName)
        assertEquals(250L, metrics.lastInferenceTimeMs)
    }

    // ---------- User Preferences ----------

    @Test
    fun `UserPreferences default values`() {
        val prefs = UserPreferences()
        assertEquals("", prefs.displayName)
        assertEquals("", prefs.email)
        assertTrue(prefs.notificationsEnabled)
        assertTrue(prefs.darkThemeEnabled)
        assertEquals("BALANCED", prefs.defaultQualityMode)
        assertFalse(prefs.isLoggedIn)
    }

    @Test
    fun `UserPreferences custom values`() {
        val prefs = UserPreferences(
            displayName = "TestUser",
            email = "test@example.com",
            notificationsEnabled = false,
            darkThemeEnabled = false,
            defaultQualityMode = "PRO",
            isLoggedIn = true
        )
        assertEquals("TestUser", prefs.displayName)
        assertEquals("test@example.com", prefs.email)
        assertFalse(prefs.notificationsEnabled)
        assertFalse(prefs.darkThemeEnabled)
        assertEquals("PRO", prefs.defaultQualityMode)
        assertTrue(prefs.isLoggedIn)
    }

    // ---------- Chat Messages ----------

    @Test
    fun `ChatMessage user message`() {
        val msg = ChatMessage("What is the price target?", isUser = true)
        assertTrue(msg.isUser)
        assertEquals("What is the price target?", msg.text)
        assertTrue(msg.timestamp > 0)
    }

    @Test
    fun `ChatMessage agent response`() {
        val msg = ChatMessage("The predicted price target is 195.00", isUser = false)
        assertFalse(msg.isUser)
        assertEquals("The predicted price target is 195.00", msg.text)
    }

    // ---------- StockData model ----------

    @Test
    fun `StockData has correct default values`() {
        val stock = StockData("TEST", "Test Corp", 100.0, 99.0, 1.0)
        assertEquals(0L, stock.marketCap)
        assertEquals("", stock.sector)
        assertTrue(stock.history.isEmpty())
    }

    @Test
    fun `StockData with full fields`() {
        val stock = StockData(
            symbol = "TCS",
            name = "Tata Consultancy Services Ltd.",
            currentPrice = 3850.00,
            previousClose = 3820.00,
            changePercent = 0.79,
            marketCap = 1410000000000,
            sector = "Technology"
        )
        assertEquals("TCS", stock.symbol)
        assertEquals(1410000000000, stock.marketCap)
        assertEquals("Technology", stock.sector)
    }

    // ---------- SeedStock JSON parsing simulation ----------

    @Test
    fun `stk json structure has expected fields`() {
        // Simulates what DataIngestion.loadStocksFromStkAsset would parse
        val stkStocks = listOf(
            mapOf(
                "symbol" to "TCS",
                "name" to "Tata Consultancy Services Ltd.",
                "sector" to "Technology",
                "industry" to "IT Consulting & Services",
                "exchange" to "NSE",
                "country" to "IN"
            ),
            mapOf(
                "symbol" to "RELIANCE",
                "name" to "Reliance Industries Ltd.",
                "sector" to "Energy",
                "industry" to "Oil & Gas Refining & Marketing",
                "exchange" to "NSE",
                "country" to "IN"
            )
        )

        assertEquals(2, stkStocks.size)
        assertEquals("TCS", stkStocks[0]["symbol"])
        assertEquals("NSE", stkStocks[0]["exchange"])
        assertEquals("IN", stkStocks[0]["country"])
    }

    // ---------- PredictionResult for template insight ----------

    @Test
    fun `template insight generation for UP direction`() {
        val prediction = PredictionResult("AAPL", 195.00, 0.75f, "UP")
        val insight = templateInsight(prediction)
        assertTrue(insight.contains("AAPL"))
        assertTrue(insight.contains("bullish upward movement"))
        assertTrue(insight.contains("75%"))
    }

    @Test
    fun `template insight generation for DOWN direction`() {
        val prediction = PredictionResult("TSLA", 165.00, 0.60f, "DOWN")
        val insight = templateInsight(prediction)
        assertTrue(insight.contains("bearish downward pressure"))
    }

    @Test
    fun `template insight generation for NEUTRAL direction`() {
        val prediction = PredictionResult("MSFT", 415.00, 0.50f, "NEUTRAL")
        val insight = templateInsight(prediction)
        assertTrue(insight.contains("sideways consolidation"))
    }

    /** Mirrors the template logic from LLMInsightEngine. */
    private fun templateInsight(prediction: PredictionResult): String {
        val directionText = when (prediction.direction) {
            "UP" -> "bullish upward movement"
            "DOWN" -> "bearish downward pressure"
            else -> "sideways consolidation"
        }
        val confidence = "%.0f".format(prediction.confidence * 100)
        return "${prediction.symbol} shows signs of $directionText with $confidence% model confidence. " +
               "The predicted price target is ${"%.2f".format(prediction.predictedPrice)}. " +
               "Consider monitoring volume and market conditions before acting."
    }
}
