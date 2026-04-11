package com.stocksense.app

import com.stocksense.app.data.model.credence.*
import com.stocksense.app.engine.credence.*
import com.stocksense.app.viewmodel.CredenceFormState
import org.junit.Assert.*
import org.junit.Test
/**
 * Unit tests for the CredenceAI Tatva Ank credit-scoring engine.
 *
 * All tests are pure JVM — no Android context required.
 * Every critical path is covered with explicit [Assert] assertions.
 *
 * Test categories:
 *  1.  FinancialProfile computed properties
 *  2.  Ingestion Agent validation
 *  3.  Altman Z-Score formula correctness
 *  4.  Debt-to-Equity edge cases
 *  5.  P&L forecast direction
 *  6.  Tatva Ank score formula + clamping
 *  7.  Risk label thresholds
 *  8.  NLP sentiment heuristic
 *  9.  Legal risk detection
 *  10. Evaluation framework — 22 metrics, 6 categories
 *  11. Score-to-risk-label mapping (via companion)
 *  12. CreditAnalysisRequest validity
 *  13. CredenceFormState.canSubmit + buildRequest
 *  14. TatvaAnkReport convenience properties
 *  15. CredenceAIEngine companion-object parser helpers
 */
class CredenceAITest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sampleProfile(
        revenue: Double = 100.0,
        ebit: Double = 15.0,
        totalAssets: Double = 80.0,
        totalLiabilities: Double = 30.0,
        workingCapital: Double = 20.0,
        retainedEarnings: Double = 10.0,
        marketValueEquity: Double = 60.0,
        totalDebt: Double = 25.0,
        shareholdersEquity: Double = 50.0,
        priorRevenue: Double = 90.0,
        priorEbit: Double = 12.0
    ) = FinancialProfile(
        totalRevenue       = revenue,
        ebit               = ebit,
        totalAssets        = totalAssets,
        totalLiabilities   = totalLiabilities,
        workingCapital     = workingCapital,
        retainedEarnings   = retainedEarnings,
        marketValueEquity  = marketValueEquity,
        totalDebt          = totalDebt,
        shareholdersEquity = shareholdersEquity,
        priorYearRevenue   = priorRevenue,
        priorYearEbit      = priorEbit
    )

    private fun sampleRequest(profile: FinancialProfile = sampleProfile()) = CreditAnalysisRequest(
        companyName       = "Acme Corp",
        industry          = "Manufacturing",
        sector            = "Industrials",
        description       = "Leading manufacturer with strong export growth.",
        financialProfile  = profile
    )

    // ── 1. FinancialProfile computed properties ───────────────────────────────

    @Test
    fun `isQuantifiable is true when revenue and assets are positive`() {
        val p = sampleProfile()
        assertTrue("isQuantifiable should be true with positive assets and revenue", p.isQuantifiable)
    }

    @Test
    fun `isQuantifiable is false when totalAssets is zero`() {
        val p = sampleProfile(totalAssets = 0.0)
        assertFalse("isQuantifiable should be false when assets = 0", p.isQuantifiable)
    }

    @Test
    fun `isQuantifiable is false when revenue is zero`() {
        val p = sampleProfile(revenue = 0.0)
        assertFalse("isQuantifiable should be false when revenue = 0", p.isQuantifiable)
    }

    @Test
    fun `revenueGrowthRate is correct`() {
        val p = sampleProfile(revenue = 110.0, priorRevenue = 100.0)
        assertEquals(0.1, p.revenueGrowthRate, 1e-9)
    }

    @Test
    fun `revenueGrowthRate is zero when priorYearRevenue is zero`() {
        val p = sampleProfile(priorRevenue = 0.0)
        assertEquals(0.0, p.revenueGrowthRate, 1e-9)
    }

    @Test
    fun `ebitMargin is correct`() {
        val p = sampleProfile(revenue = 200.0, ebit = 40.0)
        assertEquals(0.2, p.ebitMargin, 1e-9)
    }

    @Test
    fun `ebitMargin is zero when revenue is zero`() {
        val p = sampleProfile(revenue = 0.0, ebit = 10.0)
        assertEquals(0.0, p.ebitMargin, 1e-9)
    }

    // ── 2. Ingestion Agent ────────────────────────────────────────────────────

    @Test
    fun `IngestionAgent passes valid request`() {
        val result = IngestionAgent.run(sampleRequest())
        assertTrue("Ingestion should pass for valid request", result.passed)
        assertEquals("Ingestion", result.agentName)
        assertTrue("Confidence should be positive for valid request", result.confidence > 0.0)
    }

    @Test
    fun `IngestionAgent fails when companyName is blank`() {
        val req = sampleRequest().copy(companyName = "")
        val result = IngestionAgent.run(req)
        assertFalse("Ingestion should fail when company name is blank", result.passed)
        assertEquals(0.0, result.confidence, 1e-9)
    }

    @Test
    fun `IngestionAgent fails when totalAssets is zero`() {
        val req = sampleRequest(profile = sampleProfile(totalAssets = 0.0))
        val result = IngestionAgent.run(req)
        assertFalse("Ingestion should fail when total assets = 0", result.passed)
    }

    @Test
    fun `IngestionAgent fails when totalRevenue is zero`() {
        val req = sampleRequest(profile = sampleProfile(revenue = 0.0))
        val result = IngestionAgent.run(req)
        assertFalse("Ingestion should fail when revenue = 0", result.passed)
    }

    @Test
    fun `IngestionAgent fails when all financial fields are zero`() {
        val zeroProfile = FinancialProfile()  // all defaults are 0.0
        val req = sampleRequest(profile = zeroProfile)
        val result = IngestionAgent.run(req)
        assertFalse("Ingestion should fail with all-zero financials", result.passed)
        assertEquals(0.0, result.confidence, 1e-9)
    }

    @Test
    fun `IngestionAgent confidence increases with richer data`() {
        val sparseProfile = sampleProfile().copy(
            ebit = 0.0, workingCapital = 0.0, retainedEarnings = 0.0,
            marketValueEquity = 0.0, totalDebt = 0.0, shareholdersEquity = 0.0
        )
        val richProfile = sampleProfile()
        val sparse = IngestionAgent.run(sampleRequest(sparseProfile))
        val rich   = IngestionAgent.run(sampleRequest(richProfile))
        assertTrue("Rich data should yield higher confidence than sparse", rich.confidence > sparse.confidence)
    }

    // ── 3. Altman Z-Score ─────────────────────────────────────────────────────

    @Test
    fun `Altman Z-Score is correct for known inputs`() {
        // X1=20/80=0.25, X2=10/80=0.125, X3=15/80=0.1875, X4=60/30=2.0, X5=100/80=1.25
        // Z = 1.2*0.25 + 1.4*0.125 + 3.3*0.1875 + 0.6*2.0 + 1.0*1.25
        //   = 0.3 + 0.175 + 0.619 + 1.2 + 1.25 = 3.544
        val z = QuantAgent.computeAltmanZ(sampleProfile())
        assertEquals(3.544, z, 0.001)
    }

    @Test
    fun `Altman Z-Score returns zero when totalAssets is zero`() {
        val p = sampleProfile(totalAssets = 0.0)
        assertEquals(0.0, QuantAgent.computeAltmanZ(p), 1e-9)
    }

    @Test
    fun `Altman Z-Score is in safe zone for healthy company`() {
        val z = QuantAgent.computeAltmanZ(sampleProfile())
        assertTrue("Healthy company Z should be > 2.99", z > 2.99)
    }

    @Test
    fun `Altman Z-Score is in distress zone for distressed company`() {
        // Distressed company: negative retained earnings, high liabilities, low revenue
        val distressed = sampleProfile(
            revenue            = 50.0,
            ebit               = -10.0,
            totalAssets        = 100.0,
            totalLiabilities   = 90.0,
            workingCapital     = -20.0,
            retainedEarnings   = -30.0,
            marketValueEquity  = 10.0
        )
        val z = QuantAgent.computeAltmanZ(distressed)
        assertTrue("Distressed company Z should be < 1.81; was $z", z < 1.81)
    }

    @Test
    fun `Altman Z-Score is finite for all-positive inputs`() {
        val z = QuantAgent.computeAltmanZ(sampleProfile())
        assertTrue("Z-Score must be finite", z.isFinite())
    }

    @Test
    fun `Altman Z-Score uses all 5 Altman variables`() {
        // Changing each variable should change Z
        val base = QuantAgent.computeAltmanZ(sampleProfile())
        val withWcChange = QuantAgent.computeAltmanZ(sampleProfile(workingCapital = 40.0))
        val withReChange = QuantAgent.computeAltmanZ(sampleProfile(retainedEarnings = 30.0))
        val withEbitChange = QuantAgent.computeAltmanZ(sampleProfile(ebit = 30.0))
        val withMveChange = QuantAgent.computeAltmanZ(sampleProfile(marketValueEquity = 120.0))
        val withRevChange = QuantAgent.computeAltmanZ(sampleProfile(revenue = 200.0))
        assertNotEquals("Working capital change should change Z", base, withWcChange, 0.001)
        assertNotEquals("Retained earnings change should change Z", base, withReChange, 0.001)
        assertNotEquals("EBIT change should change Z", base, withEbitChange, 0.001)
        assertNotEquals("MVE change should change Z", base, withMveChange, 0.001)
        assertNotEquals("Revenue change should change Z", base, withRevChange, 0.001)
    }

    // ── 4. Debt-to-Equity ─────────────────────────────────────────────────────

    @Test
    fun `debtToEquity is correct`() {
        val p = sampleProfile(totalDebt = 25.0, shareholdersEquity = 50.0)
        assertEquals(0.5, QuantAgent.computeDeRatio(p), 1e-9)
    }

    @Test
    fun `debtToEquity returns MAX_VALUE when equity is zero`() {
        val p = sampleProfile(shareholdersEquity = 0.0)
        assertEquals(Double.MAX_VALUE, QuantAgent.computeDeRatio(p), 0.0)
    }

    @Test
    fun `debtToEquity is zero when debt is zero`() {
        val p = sampleProfile(totalDebt = 0.0, shareholdersEquity = 50.0)
        assertEquals(0.0, QuantAgent.computeDeRatio(p), 1e-9)
    }

    @Test
    fun `debtToEquity MAX_VALUE is handled by scoring without NaN`() {
        val score = OrchestratorAgent.computeTatvaAnkScore(
            altmanZ        = 2.5,
            sentimentScore = 0.0,
            deRatio        = Double.MAX_VALUE
        )
        assertTrue("Score with infinite D/E should be finite", score.isFinite())
        assertTrue("Score with infinite D/E should be in [0,100]", score in 0.0..100.0)
    }

    // ── 5. P&L Forecast ───────────────────────────────────────────────────────

    @Test
    fun `pnlForecast shows improving trend`() {
        val p = sampleProfile(priorEbit = 10.0, ebit = 15.0)
        val forecast = QuantAgent.computePnlForecast(p)
        assertTrue("Forecast should show improving trend", forecast.contains("improving"))
    }

    @Test
    fun `pnlForecast shows declining trend`() {
        val p = sampleProfile(priorEbit = 20.0, ebit = 12.0)
        val forecast = QuantAgent.computePnlForecast(p)
        assertTrue("Forecast should show declining trend", forecast.contains("declining"))
    }

    @Test
    fun `pnlForecast shows flat trend when ebit unchanged`() {
        val p = sampleProfile(priorEbit = 15.0, ebit = 15.0)
        val forecast = QuantAgent.computePnlForecast(p)
        assertTrue("Forecast should show flat trend", forecast.contains("flat"))
    }

    @Test
    fun `pnlForecast returns NA when both ebit values are zero`() {
        val p = sampleProfile(priorEbit = 0.0, ebit = 0.0)
        val forecast = QuantAgent.computePnlForecast(p)
        assertEquals("N/A", forecast)
    }

    // ── 6. Tatva Ank Score formula ────────────────────────────────────────────

    @Test
    fun `computeTatvaAnkScore is clamped and does not exceed 100`() {
        // Max achievable: base(50) + altman≥3(+20) + D/E≤0.5(+5) + sentiment×10(+10) = 85
        val score = OrchestratorAgent.computeTatvaAnkScore(
            altmanZ        = 10.0,
            sentimentScore = 1.0,
            deRatio        = 0.1
        )
        assertEquals(85.0, score, 0.001)
        assertTrue("Score must never exceed 100", score <= 100.0)
    }

    @Test
    fun `computeTatvaAnkScore is clamped and does not go below 0`() {
        // Min achievable: base(50) + altman<1.0(-15) + D/E>3.0(-10) + sentiment×10(-10) = 15
        val score = OrchestratorAgent.computeTatvaAnkScore(
            altmanZ        = 0.5,
            sentimentScore = -1.0,
            deRatio        = 4.0
        )
        assertEquals(15.0, score, 0.001)
        assertTrue("Score must never go below 0", score >= 0.0)
    }

    @Test
    fun `computeTatvaAnkScore baseline is 50 for neutral inputs`() {
        // altmanZ = 1.5 → -5, deRatio = 1.5 → 0, sentiment = 0 → 0 => 50-5 = 45
        val score = OrchestratorAgent.computeTatvaAnkScore(1.5, 0.0, 1.5)
        assertEquals(45.0, score, 0.001)
    }

    @Test
    fun `positive sentiment increases score`() {
        val base     = OrchestratorAgent.computeTatvaAnkScore(2.5, 0.0, 1.0)
        val withPos  = OrchestratorAgent.computeTatvaAnkScore(2.5, 0.5, 1.0)
        assertTrue("Positive sentiment should increase score", withPos > base)
    }

    @Test
    fun `negative sentiment decreases score`() {
        val base     = OrchestratorAgent.computeTatvaAnkScore(2.5, 0.0, 1.0)
        val withNeg  = OrchestratorAgent.computeTatvaAnkScore(2.5, -0.5, 1.0)
        assertTrue("Negative sentiment should decrease score", withNeg < base)
    }

    @Test
    fun `high Altman Z increases score vs low Z`() {
        val low  = OrchestratorAgent.computeTatvaAnkScore(0.5, 0.0, 1.5)
        val high = OrchestratorAgent.computeTatvaAnkScore(3.5, 0.0, 1.5)
        assertTrue("High Z should yield higher score than low Z", high > low)
    }

    @Test
    fun `score is always in range 0 to 100`() {
        val inputs = listOf(
            Triple(-10.0, -1.0, Double.MAX_VALUE),
            Triple(0.0, 0.0, 0.0),
            Triple(5.0, 1.0, 0.1),
            Triple(3.0, 0.5, 1.5)
        )
        for ((z, sent, de) in inputs) {
            val score = OrchestratorAgent.computeTatvaAnkScore(z, sent, de)
            assertTrue("Score must be in [0,100] for z=$z, sent=$sent, de=$de; got $score",
                score in 0.0..100.0)
        }
    }

    // ── 7. Risk label thresholds ──────────────────────────────────────────────

    @Test
    fun `score 70 or above maps to LOW risk`() {
        assertEquals(RiskLabel.LOW,    CredenceAIEngine.scoreToRiskLabel(70.0))
        assertEquals(RiskLabel.LOW,    CredenceAIEngine.scoreToRiskLabel(85.0))
        assertEquals(RiskLabel.LOW,    CredenceAIEngine.scoreToRiskLabel(100.0))
    }

    @Test
    fun `score between 40 and 69 maps to MEDIUM risk`() {
        assertEquals(RiskLabel.MEDIUM, CredenceAIEngine.scoreToRiskLabel(40.0))
        assertEquals(RiskLabel.MEDIUM, CredenceAIEngine.scoreToRiskLabel(55.0))
        assertEquals(RiskLabel.MEDIUM, CredenceAIEngine.scoreToRiskLabel(69.9))
    }

    @Test
    fun `score below 40 maps to HIGH risk`() {
        assertEquals(RiskLabel.HIGH,   CredenceAIEngine.scoreToRiskLabel(39.9))
        assertEquals(RiskLabel.HIGH,   CredenceAIEngine.scoreToRiskLabel(20.0))
        assertEquals(RiskLabel.HIGH,   CredenceAIEngine.scoreToRiskLabel(0.0))
    }

    @Test
    fun `risk label boundary at exactly 40 is MEDIUM`() {
        assertEquals(RiskLabel.MEDIUM, CredenceAIEngine.scoreToRiskLabel(40.0))
    }

    @Test
    fun `risk label boundary at exactly 70 is LOW`() {
        assertEquals(RiskLabel.LOW,    CredenceAIEngine.scoreToRiskLabel(70.0))
    }

    // ── 8. NLP Risk Agent sentiment heuristic ─────────────────────────────────

    @Test
    fun `positive keywords yield positive sentiment`() {
        val text = "Company shows strong growth and record revenue with high profit margins"
        val sentiment = NlpRiskAgent.computeHeuristicSentiment(text)
        assertTrue("Positive text should yield positive sentiment; got $sentiment", sentiment > 0)
    }

    @Test
    fun `negative keywords yield negative sentiment`() {
        val text = "Company facing bankruptcy fraud and insolvency investigation by SEBI"
        val sentiment = NlpRiskAgent.computeHeuristicSentiment(text)
        assertTrue("Negative text should yield negative sentiment; got $sentiment", sentiment < 0)
    }

    @Test
    fun `blank text yields zero sentiment`() {
        val sentiment = NlpRiskAgent.computeHeuristicSentiment("")
        assertEquals(0.0, sentiment, 1e-9)
    }

    @Test
    fun `neutral text yields zero sentiment`() {
        val text = "The company produces widgets in a factory."
        val sentiment = NlpRiskAgent.computeHeuristicSentiment(text)
        assertEquals(0.0, sentiment, 1e-9)
    }

    @Test
    fun `sentiment is bounded to -1 and +1`() {
        val heavilyPositive = "growth profit expansion award upgrade dividend " +
                "market leader record revenue strong outperform ipo"
        val s = NlpRiskAgent.computeHeuristicSentiment(heavilyPositive)
        assertTrue("Sentiment must be ≤ 1.0; got $s", s <= 1.0)
        assertTrue("Sentiment must be ≥ -1.0; got $s", s >= -1.0)
    }

    // ── 9. Legal risk detection ───────────────────────────────────────────────

    @Test
    fun `legal risk count is zero for clean text`() {
        val flags = NlpRiskAgent.detectLegalRisk("Revenue grew by 20 percent this year")
        assertEquals(0, flags)
    }

    @Test
    fun `legal risk count detects fraud keyword`() {
        val flags = NlpRiskAgent.detectLegalRisk("Company under investigation for fraud by SEBI")
        assertTrue("Should detect at least 2 flags (fraud + sebi + investigation)", flags >= 2)
    }

    @Test
    fun `legal risk count detects NPA keyword`() {
        val flags = NlpRiskAgent.detectLegalRisk("Bank reported high NPA ratio in Q3")
        assertTrue("Should detect NPA", flags >= 1)
    }

    // ── 10. Evaluation Framework ──────────────────────────────────────────────

    @Test
    fun `EvaluationFramework returns exactly 22 metrics`() {
        val metrics = EvaluationFramework.evaluate(
            request      = sampleRequest(),
            ingestion    = AgentResult("Ingestion", "ok", 0.9, true),
            quant        = AgentResult("Quant", "ok", 0.85, true, "altmanZ=3.5;deRatio=0.5;forecast=improving"),
            nlp          = AgentResult("NLP Risk", "ok", 0.7, true, "sentiment=0.3;legalFlags=0"),
            orchestrator = AgentResult("Orchestrator", "ok", 72.0, true),
            tatvaAnkScore = 72.0,
            processingMs = 3000L
        )
        assertEquals("Must return exactly 22 metrics", 22, metrics.size)
    }

    @Test
    fun `EvaluationFramework metric IDs are 1 through 22`() {
        val metrics = EvaluationFramework.evaluate(
            request      = sampleRequest(),
            ingestion    = AgentResult("Ingestion", "ok", 0.9, true),
            quant        = AgentResult("Quant", "ok", 0.85, true, "altmanZ=3.5;deRatio=0.5;forecast=improving"),
            nlp          = AgentResult("NLP Risk", "ok", 0.7, true, "sentiment=0.3;legalFlags=0"),
            orchestrator = AgentResult("Orchestrator", "ok", 72.0, true),
            tatvaAnkScore = 72.0,
            processingMs = 3000L
        )
        val ids = metrics.map { it.id }.sorted()
        assertEquals("First ID must be 1", 1, ids.first())
        assertEquals("Last ID must be 22", 22, ids.last())
        assertEquals("All IDs must be unique", 22, ids.toSet().size)
    }

    @Test
    fun `EvaluationFramework covers all 6 categories`() {
        val metrics = EvaluationFramework.evaluate(
            request      = sampleRequest(),
            ingestion    = AgentResult("Ingestion", "ok", 0.9, true),
            quant        = AgentResult("Quant", "ok", 0.85, true, "altmanZ=3.5;deRatio=0.5;forecast=improving"),
            nlp          = AgentResult("NLP Risk", "ok", 0.7, true, "sentiment=0.3;legalFlags=0"),
            orchestrator = AgentResult("Orchestrator", "ok", 72.0, true),
            tatvaAnkScore = 72.0,
            processingMs = 3000L
        )
        val categories = metrics.map { it.category }.toSet()
        assertEquals("Must cover all 6 taxonomy categories", 6, categories.size)
        assertTrue(categories.contains(EvaluationCategory.FACTUAL_ACCURACY))
        assertTrue(categories.contains(EvaluationCategory.RELEVANCE))
        assertTrue(categories.contains(EvaluationCategory.SAFETY_COMPLIANCE))
        assertTrue(categories.contains(EvaluationCategory.SYSTEM_PERFORMANCE))
        assertTrue(categories.contains(EvaluationCategory.REASONING_QUALITY))
        assertTrue(categories.contains(EvaluationCategory.UX_DELIVERY))
    }

    @Test
    fun `EvaluationFramework category counts are correct`() {
        val metrics = EvaluationFramework.evaluate(
            request      = sampleRequest(),
            ingestion    = AgentResult("Ingestion", "ok", 0.9, true),
            quant        = AgentResult("Quant", "ok", 0.85, true, "altmanZ=3.5;deRatio=0.5;forecast=improving"),
            nlp          = AgentResult("NLP Risk", "ok", 0.7, true, "sentiment=0.3;legalFlags=0"),
            orchestrator = AgentResult("Orchestrator", "ok", 72.0, true),
            tatvaAnkScore = 72.0,
            processingMs = 3000L
        )
        assertEquals(4, metrics.count { it.category == EvaluationCategory.FACTUAL_ACCURACY })
        assertEquals(3, metrics.count { it.category == EvaluationCategory.RELEVANCE })
        assertEquals(4, metrics.count { it.category == EvaluationCategory.SAFETY_COMPLIANCE })
        assertEquals(4, metrics.count { it.category == EvaluationCategory.SYSTEM_PERFORMANCE })
        assertEquals(4, metrics.count { it.category == EvaluationCategory.REASONING_QUALITY })
        assertEquals(3, metrics.count { it.category == EvaluationCategory.UX_DELIVERY })
    }

    @Test
    fun `EvaluationFramework all scores are in 0 to 1 range`() {
        val metrics = EvaluationFramework.evaluate(
            request      = sampleRequest(),
            ingestion    = AgentResult("Ingestion", "ok", 0.9, true),
            quant        = AgentResult("Quant", "ok", 0.85, true, "altmanZ=3.5;deRatio=0.5;forecast=improving"),
            nlp          = AgentResult("NLP Risk", "ok", 0.7, true, "sentiment=0.3;legalFlags=0"),
            orchestrator = AgentResult("Orchestrator", "ok", 72.0, true),
            tatvaAnkScore = 72.0,
            processingMs = 3000L
        )
        metrics.forEach { metric ->
            assertTrue("Score for '${metric.name}' must be in [0,1]; was ${metric.score}",
                metric.score in 0.0..1.0)
        }
    }

    @Test
    fun `failedEvaluation returns exactly 22 metrics`() {
        val metrics = EvaluationFramework.failedEvaluation()
        assertEquals("failedEvaluation must return 22 metrics", 22, metrics.size)
    }

    @Test
    fun `fast pipeline yields high latency score`() {
        val metrics = EvaluationFramework.evaluate(
            request      = sampleRequest(),
            ingestion    = AgentResult("Ingestion", "ok", 0.9, true),
            quant        = AgentResult("Quant", "ok", 0.85, true, "altmanZ=3.5;deRatio=0.5;forecast=improving"),
            nlp          = AgentResult("NLP Risk", "ok", 0.7, true, "sentiment=0.3;legalFlags=0"),
            orchestrator = AgentResult("Orchestrator", "ok", 72.0, true),
            tatvaAnkScore = 72.0,
            processingMs = 1000L   // Very fast
        )
        val latencyMetric = metrics.first { it.name == "Latency" }
        assertEquals(1.0, latencyMetric.score, 0.001)
        assertTrue("Fast pipeline latency metric should pass", latencyMetric.passed)
    }

    @Test
    fun `slow pipeline yields lower latency score`() {
        val fast = EvaluationFramework.evaluate(
            request      = sampleRequest(),
            ingestion    = AgentResult("Ingestion", "ok", 0.9, true),
            quant        = AgentResult("Quant", "ok", 0.85, true, "altmanZ=3.5;deRatio=0.5;forecast=improving"),
            nlp          = AgentResult("NLP Risk", "ok", 0.7, true, "sentiment=0.3;legalFlags=0"),
            orchestrator = AgentResult("Orchestrator", "ok", 72.0, true),
            tatvaAnkScore = 72.0, processingMs = 2000L
        ).first { it.name == "Latency" }.score

        val slow = EvaluationFramework.evaluate(
            request      = sampleRequest(),
            ingestion    = AgentResult("Ingestion", "ok", 0.9, true),
            quant        = AgentResult("Quant", "ok", 0.85, true, "altmanZ=3.5;deRatio=0.5;forecast=improving"),
            nlp          = AgentResult("NLP Risk", "ok", 0.7, true, "sentiment=0.3;legalFlags=0"),
            orchestrator = AgentResult("Orchestrator", "ok", 72.0, true),
            tatvaAnkScore = 72.0, processingMs = 35_000L
        ).first { it.name == "Latency" }.score

        assertTrue("Slow pipeline should have lower latency score than fast", slow < fast)
    }

    // ── 11. Score-to-risk-label mapping ──────────────────────────────────────

    @Test
    fun `RiskLabel enum has exactly 3 values`() {
        assertEquals(3, RiskLabel.entries.size)
        assertTrue(RiskLabel.entries.contains(RiskLabel.LOW))
        assertTrue(RiskLabel.entries.contains(RiskLabel.MEDIUM))
        assertTrue(RiskLabel.entries.contains(RiskLabel.HIGH))
    }

    @Test
    fun `RiskLabel display names are correct`() {
        assertEquals("Low Risk",    RiskLabel.LOW.displayName)
        assertEquals("Medium Risk", RiskLabel.MEDIUM.displayName)
        assertEquals("High Risk",   RiskLabel.HIGH.displayName)
    }

    @Test
    fun `RiskLabel emojis are correct`() {
        assertEquals("🟢", RiskLabel.LOW.emoji)
        assertEquals("🟡", RiskLabel.MEDIUM.emoji)
        assertEquals("🔴", RiskLabel.HIGH.emoji)
    }

    // ── 12. CreditAnalysisRequest validity ────────────────────────────────────

    @Test
    fun `isValid is true for complete request`() {
        assertTrue("Complete request should be valid", sampleRequest().isValid)
    }

    @Test
    fun `isValid is false when companyName is blank`() {
        assertFalse("Blank name makes request invalid", sampleRequest().copy(companyName = "").isValid)
    }

    @Test
    fun `isValid is false when totalAssets is zero`() {
        val req = sampleRequest(profile = sampleProfile(totalAssets = 0.0))
        assertFalse("Zero assets makes request invalid", req.isValid)
    }

    @Test
    fun `isValid is false when totalRevenue is zero`() {
        val req = sampleRequest(profile = sampleProfile(revenue = 0.0))
        assertFalse("Zero revenue makes request invalid", req.isValid)
    }

    // ── 13. CredenceFormState ─────────────────────────────────────────────────

    @Test
    fun `CredenceFormState canSubmit is true with valid minimum fields`() {
        val form = CredenceFormState(
            companyName  = "Test Co",
            totalRevenue = "100.0",
            totalAssets  = "80.0"
        )
        assertTrue("Form with name, revenue, assets should allow submit", form.canSubmit)
    }

    @Test
    fun `CredenceFormState canSubmit is false when companyName is blank`() {
        val form = CredenceFormState(
            companyName  = "",
            totalRevenue = "100.0",
            totalAssets  = "80.0"
        )
        assertFalse("Empty company name should prevent submit", form.canSubmit)
    }

    @Test
    fun `CredenceFormState canSubmit is false when revenue is non-numeric`() {
        val form = CredenceFormState(
            companyName  = "Test Co",
            totalRevenue = "abc",
            totalAssets  = "80.0"
        )
        assertFalse("Non-numeric revenue should prevent submit", form.canSubmit)
    }

    @Test
    fun `CredenceFormState canSubmit is false when revenue is zero`() {
        val form = CredenceFormState(
            companyName  = "Test Co",
            totalRevenue = "0.0",
            totalAssets  = "80.0"
        )
        assertFalse("Zero revenue should prevent submit", form.canSubmit)
    }

    @Test
    fun `CredenceFormState buildRequest maps fields correctly`() {
        val form = CredenceFormState(
            companyName        = "Acme Corp",
            industry           = "Tech",
            sector             = "IT",
            description        = "Leading tech firm",
            totalRevenue       = "500.0",
            ebit               = "75.0",
            totalAssets        = "300.0",
            totalLiabilities   = "120.0",
            workingCapital     = "80.0",
            retainedEarnings   = "50.0",
            marketValueEquity  = "200.0",
            totalDebt          = "100.0",
            shareholdersEquity = "180.0"
        )
        val req = form.buildRequest()
        assertEquals("Acme Corp", req.companyName)
        assertEquals("Tech", req.industry)
        assertEquals(500.0, req.financialProfile.totalRevenue, 1e-9)
        assertEquals(75.0,  req.financialProfile.ebit, 1e-9)
        assertEquals(300.0, req.financialProfile.totalAssets, 1e-9)
        assertEquals(80.0,  req.financialProfile.workingCapital, 1e-9)
        assertEquals(200.0, req.financialProfile.marketValueEquity, 1e-9)
    }

    @Test
    fun `CredenceFormState buildRequest uses zero for missing numeric fields`() {
        val form = CredenceFormState(
            companyName  = "Minimal Co",
            totalRevenue = "100.0",
            totalAssets  = "50.0"
            // All other fields left empty → default ""
        )
        val req = form.buildRequest()
        assertEquals(0.0, req.financialProfile.ebit, 1e-9)
        assertEquals(0.0, req.financialProfile.totalDebt, 1e-9)
        assertEquals(0.0, req.financialProfile.shareholdersEquity, 1e-9)
    }

    // ── 14. TatvaAnkReport convenience properties ─────────────────────────────

    @Test
    fun `TatvaAnkReport evaluationPassCount is correct`() {
        val metrics = (1..22).map { i ->
            EvaluationMetric(i, EvaluationCategory.FACTUAL_ACCURACY, "M$i", "", 0.8, i <= 18)
        }
        val report = TatvaAnkReport(
            request            = sampleRequest(),
            tatvaAnkScore      = 75.0,
            riskLabel          = RiskLabel.LOW,
            altmanZScore       = 3.5,
            debtToEquity       = 0.5,
            sentimentScore     = 0.3,
            ingestionResult    = AgentResult("Ingestion", "ok", 0.9, true),
            quantResult        = AgentResult("Quant", "ok", 0.85, true),
            nlpResult          = AgentResult("NLP Risk", "ok", 0.7, true),
            orchestratorResult = AgentResult("Orchestrator", "ok", 75.0, true),
            evaluationMetrics  = metrics,
            processingTimeMs   = 5000L
        )
        assertEquals("Pass count should be 18", 18, report.evaluationPassCount)
    }

    @Test
    fun `TatvaAnkReport meanEvaluationScore is correct`() {
        val metrics = (1..22).map { i ->
            EvaluationMetric(i, EvaluationCategory.FACTUAL_ACCURACY, "M$i", "", 0.8, true)
        }
        val report = TatvaAnkReport(
            request            = sampleRequest(),
            tatvaAnkScore      = 75.0,
            riskLabel          = RiskLabel.LOW,
            altmanZScore       = 3.5,
            debtToEquity       = 0.5,
            sentimentScore     = 0.3,
            ingestionResult    = AgentResult("Ingestion", "ok", 0.9, true),
            quantResult        = AgentResult("Quant", "ok", 0.85, true),
            nlpResult          = AgentResult("NLP Risk", "ok", 0.7, true),
            orchestratorResult = AgentResult("Orchestrator", "ok", 75.0, true),
            evaluationMetrics  = metrics,
            processingTimeMs   = 5000L
        )
        assertEquals(0.8, report.meanEvaluationScore, 1e-9)
    }

    @Test
    fun `TatvaAnkReport meanEvaluationScore is zero for empty metrics`() {
        val report = TatvaAnkReport(
            request            = sampleRequest(),
            tatvaAnkScore      = 75.0,
            riskLabel          = RiskLabel.LOW,
            altmanZScore       = 3.5,
            debtToEquity       = 0.5,
            sentimentScore     = 0.3,
            ingestionResult    = AgentResult("Ingestion", "ok", 0.9, true),
            quantResult        = AgentResult("Quant", "ok", 0.85, true),
            nlpResult          = AgentResult("NLP Risk", "ok", 0.7, true),
            orchestratorResult = AgentResult("Orchestrator", "ok", 75.0, true),
            evaluationMetrics  = emptyList(),
            processingTimeMs   = 5000L
        )
        assertEquals(0.0, report.meanEvaluationScore, 1e-9)
    }

    // ── 15. CredenceAIEngine parser helpers ───────────────────────────────────

    @Test
    fun `parseAltmanZ extracts value from detail string`() {
        val z = CredenceAIEngine.parseAltmanZ("altmanZ=3.5440;deRatio=0.5000;forecast=improving")
        assertEquals(3.544, z, 0.001)
    }

    @Test
    fun `parseAltmanZ returns 0 when key missing`() {
        assertEquals(0.0, CredenceAIEngine.parseAltmanZ("nokey=1.0"), 1e-9)
    }

    @Test
    fun `parseDERatio extracts value from detail string`() {
        val de = CredenceAIEngine.parseDERatio("altmanZ=3.5;deRatio=0.5000;forecast=improving")
        assertEquals(0.5, de, 0.001)
    }

    @Test
    fun `parseSentiment extracts value from detail string`() {
        val s = CredenceAIEngine.parseSentiment("sentiment=0.3500;legalFlags=0")
        assertEquals(0.35, s, 0.001)
    }

    @Test
    fun `parseSentiment handles negative value`() {
        val s = CredenceAIEngine.parseSentiment("sentiment=-0.4200;legalFlags=2")
        assertEquals(-0.42, s, 0.001)
    }

    // ── EvaluationCategory enum ───────────────────────────────────────────────

    @Test
    fun `EvaluationCategory has exactly 6 values`() {
        assertEquals(6, EvaluationCategory.entries.size)
    }

    @Test
    fun `EvaluationCategory display names are non-blank`() {
        EvaluationCategory.entries.forEach { cat ->
            assertTrue("Display name for $cat must be non-blank", cat.displayName.isNotBlank())
        }
    }
}







