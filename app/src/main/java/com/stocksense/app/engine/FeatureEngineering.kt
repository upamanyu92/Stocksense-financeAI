package com.stocksense.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-Kotlin feature engineering module that ports the web app's feature_factory.py.
 * Computes technical indicators used by the agentic prediction pipeline (Section 3.4).
 */
object FeatureEngineering {

    // ── Configuration Constants ─────────────────────────────────────────

    private val SMA_EMA_PERIODS = intArrayOf(20, 50)

    // Market regime thresholds
    private const val HIGH_VOLATILITY_THRESHOLD = 0.03   // ATR/price top-quartile for equities
    private const val LOW_ADX_THRESHOLD = 25.0            // Below → range-bound market

    // Data quality scoring
    private const val RECENCY_FULL_SCORE_DAYS = 30.0
    private const val RECENCY_ZERO_SCORE_DAYS = 120.0
    private const val RECENCY_DECAY_SPAN = RECENCY_ZERO_SCORE_DAYS - RECENCY_FULL_SCORE_DAYS
    private const val OPTIMAL_DATA_POINT_COUNT = 1000.0
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val COMPLETENESS_WEIGHT = 0.4
    private const val RECENCY_WEIGHT = 0.35
    private const val VOLUME_WEIGHT = 0.25

    // ── Moving Averages ─────────────────────────────────────────────────

    /** Simple Moving Average over [period] bars. */
    fun calcSma(close: DoubleArray, period: Int): DoubleArray {
        if (close.isEmpty() || period <= 0) return doubleArrayOf()
        val result = DoubleArray(close.size) { Double.NaN }
        if (close.size < period) return result
        var windowSum = 0.0
        for (i in 0 until period) windowSum += close[i]
        result[period - 1] = windowSum / period
        for (i in period until close.size) {
            windowSum += close[i] - close[i - period]
            result[i] = windowSum / period
        }
        return result
    }

    /** Exponential Moving Average with multiplier = 2 / (period + 1). */
    fun calcEma(close: DoubleArray, period: Int): DoubleArray {
        if (close.isEmpty() || period <= 0) return doubleArrayOf()
        val result = DoubleArray(close.size) { Double.NaN }
        if (close.size < period) return result
        val multiplier = 2.0 / (period + 1)
        // Seed with the SMA of the first [period] values
        var ema = 0.0
        for (i in 0 until period) ema += close[i]
        ema /= period
        result[period - 1] = ema
        for (i in period until close.size) {
            ema = (close[i] - ema) * multiplier + ema
            result[i] = ema
        }
        return result
    }

    // ── Momentum ────────────────────────────────────────────────────────

    /** Relative Strength Index using Wilder's smoothing method. */
    fun calcRsi(close: DoubleArray, period: Int = 14): DoubleArray {
        if (close.isEmpty() || period <= 0) return doubleArrayOf()
        val result = DoubleArray(close.size) { Double.NaN }
        if (close.size < period + 1) return result

        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            val delta = close[i] - close[i - 1]
            if (delta > 0) avgGain += delta else avgLoss += -delta
        }
        avgGain /= period
        avgLoss /= period

        result[period] = if (avgLoss == 0.0) 100.0
                         else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)

        for (i in (period + 1) until close.size) {
            val delta = close[i] - close[i - 1]
            val gain = if (delta > 0) delta else 0.0
            val loss = if (delta < 0) -delta else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            result[i] = if (avgLoss == 0.0) 100.0
                         else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        }
        return result
    }

    /** MACD (12, 26, 9). Returns (macd, signal, histogram). */
    fun calcMacd(close: DoubleArray): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val empty = Triple(doubleArrayOf(), doubleArrayOf(), doubleArrayOf())
        if (close.size < 26) return empty

        val ema12 = calcEma(close, 12)
        val ema26 = calcEma(close, 26)

        val macd = DoubleArray(close.size) { Double.NaN }
        // MACD line is valid from index 25 onward (where EMA26 is first valid)
        for (i in 25 until close.size) {
            macd[i] = ema12[i] - ema26[i]
        }

        // Signal = EMA(9) of the valid MACD segment
        val validMacd = macd.drop(25).toDoubleArray()
        val signalRaw = calcEma(validMacd, 9)
        val signal = DoubleArray(close.size) { Double.NaN }
        for (i in signalRaw.indices) {
            if (!signalRaw[i].isNaN()) signal[i + 25] = signalRaw[i]
        }

        val histogram = DoubleArray(close.size) { Double.NaN }
        for (i in close.indices) {
            if (!macd[i].isNaN() && !signal[i].isNaN()) {
                histogram[i] = macd[i] - signal[i]
            }
        }
        return Triple(macd, signal, histogram)
    }

    /** Stochastic Oscillator (%K, %D). %D = SMA(3) of %K. */
    fun calcStochastic(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 14
    ): Pair<DoubleArray, DoubleArray> {
        val n = close.size
        if (n == 0 || n < period) return Pair(doubleArrayOf(), doubleArrayOf())

        val k = DoubleArray(n) { Double.NaN }
        for (i in (period - 1) until n) {
            var hh = Double.MIN_VALUE
            var ll = Double.MAX_VALUE
            for (j in (i - period + 1)..i) {
                if (high[j] > hh) hh = high[j]
                if (low[j] < ll) ll = low[j]
            }
            val range = hh - ll
            k[i] = if (range == 0.0) 50.0 else (close[i] - ll) / range * 100.0
        }
        val d = smaOfNonNan(k, 3)
        return Pair(k, d)
    }

    // ── Volatility ──────────────────────────────────────────────────────

    /** Bollinger Bands: (upper, middle, lower). middle = SMA, bands = ±2σ. */
    fun calcBollingerBands(
        close: DoubleArray,
        period: Int = 20
    ): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val n = close.size
        val upper = DoubleArray(n) { Double.NaN }
        val middle = calcSma(close, period)
        val lower = DoubleArray(n) { Double.NaN }
        if (n < period) return Triple(upper, middle, lower)

        for (i in (period - 1) until n) {
            val mean = middle[i]
            var sumSq = 0.0
            for (j in (i - period + 1)..i) {
                val diff = close[j] - mean
                sumSq += diff * diff
            }
            val sd = sqrt(sumSq / period)
            upper[i] = mean + 2.0 * sd
            lower[i] = mean - 2.0 * sd
        }
        return Triple(upper, middle, lower)
    }

    /** Average True Range over [period] bars. */
    fun calcAtr(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 14
    ): DoubleArray {
        val n = close.size
        if (n < 2 || n < period + 1) return DoubleArray(n) { Double.NaN }

        val tr = DoubleArray(n) { Double.NaN }
        tr[0] = high[0] - low[0]
        for (i in 1 until n) {
            val hl = high[i] - low[i]
            val hc = abs(high[i] - close[i - 1])
            val lc = abs(low[i] - close[i - 1])
            tr[i] = max(hl, max(hc, lc))
        }

        val atr = DoubleArray(n) { Double.NaN }
        // First ATR = average of first [period] TRs (starting from index 1)
        var sum = 0.0
        for (i in 1..period) sum += tr[i]
        atr[period] = sum / period
        // Wilder's smoothing
        for (i in (period + 1) until n) {
            atr[i] = (atr[i - 1] * (period - 1) + tr[i]) / period
        }
        return atr
    }

    /** Average Directional Index (ADX) over [period] bars. */
    fun calcAdx(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 14
    ): DoubleArray {
        val n = close.size
        val result = DoubleArray(n) { Double.NaN }
        // Need at least 2*period + 1 bars for meaningful ADX
        if (n < 2 * period + 1) return result

        val plusDm = DoubleArray(n)
        val minusDm = DoubleArray(n)
        val tr = DoubleArray(n)

        for (i in 1 until n) {
            val upMove = high[i] - high[i - 1]
            val downMove = low[i - 1] - low[i]
            plusDm[i] = if (upMove > downMove && upMove > 0) upMove else 0.0
            minusDm[i] = if (downMove > upMove && downMove > 0) downMove else 0.0
            val hl = high[i] - low[i]
            val hc = abs(high[i] - close[i - 1])
            val lc = abs(low[i] - close[i - 1])
            tr[i] = max(hl, max(hc, lc))
        }

        // Initial smoothed sums over [period]
        var smoothTr = 0.0
        var smoothPlusDm = 0.0
        var smoothMinusDm = 0.0
        for (i in 1..period) {
            smoothTr += tr[i]
            smoothPlusDm += plusDm[i]
            smoothMinusDm += minusDm[i]
        }

        val dx = DoubleArray(n) { Double.NaN }
        for (i in period until n) {
            if (i > period) {
                smoothTr = smoothTr - smoothTr / period + tr[i]
                smoothPlusDm = smoothPlusDm - smoothPlusDm / period + plusDm[i]
                smoothMinusDm = smoothMinusDm - smoothMinusDm / period + minusDm[i]
            }
            val plusDi = if (smoothTr == 0.0) 0.0 else 100.0 * smoothPlusDm / smoothTr
            val minusDi = if (smoothTr == 0.0) 0.0 else 100.0 * smoothMinusDm / smoothTr
            val diSum = plusDi + minusDi
            dx[i] = if (diSum == 0.0) 0.0 else 100.0 * abs(plusDi - minusDi) / diSum
        }

        // ADX = Wilder-smoothed DX over [period]
        var adxSum = 0.0
        for (i in period until (2 * period)) adxSum += dx[i]
        result[2 * period - 1] = adxSum / period
        for (i in (2 * period) until n) {
            result[i] = (result[i - 1] * (period - 1) + dx[i]) / period
        }
        return result
    }

    // ── Volume ──────────────────────────────────────────────────────────

    /** On-Balance Volume: cumulative volume guided by price direction. */
    fun calcObv(close: DoubleArray, volume: DoubleArray): DoubleArray {
        val n = close.size
        if (n == 0 || volume.size != n) return DoubleArray(n) { Double.NaN }
        val obv = DoubleArray(n)
        obv[0] = volume[0]
        for (i in 1 until n) {
            obv[i] = obv[i - 1] + when {
                close[i] > close[i - 1] ->  volume[i]
                close[i] < close[i - 1] -> -volume[i]
                else -> 0.0
            }
        }
        return obv
    }

    // ── Price Patterns ──────────────────────────────────────────────────

    /** Fibonacci retracement levels (38.2 %, 50 %, 61.8 %) over the last [window] bars. */
    fun calcFibonacciLevels(
        high: DoubleArray,
        low: DoubleArray,
        window: Int = 50
    ): Map<String, Double> {
        if (high.isEmpty() || low.isEmpty()) return emptyMap()
        val lookback = min(window, high.size)
        val startIdx = high.size - lookback
        var hh = Double.MIN_VALUE
        var ll = Double.MAX_VALUE
        for (i in startIdx until high.size) {
            if (high[i] > hh) hh = high[i]
            if (low[i] < ll) ll = low[i]
        }
        val range = hh - ll
        return mapOf(
            "fib_38_2" to (hh - range * 0.382),
            "fib_50"   to (hh - range * 0.500),
            "fib_61_8" to (hh - range * 0.618)
        )
    }

    // ── Higher-Level Aggregation ────────────────────────────────────────

    /**
     * Compute all features at once, returning a flat map ready for the
     * prediction pipeline. Keys match those consumed by PredictionEngine.
     */
    fun computeAllFeatures(
        close: DoubleArray,
        high: DoubleArray,
        low: DoubleArray,
        volume: DoubleArray
    ): Map<String, Double> {
        if (close.isEmpty()) return emptyMap()

        val features = mutableMapOf<String, Double>()
        val last = close.size - 1

        // Moving averages
        for (p in SMA_EMA_PERIODS) {
            val sma = calcSma(close, p)
            val ema = calcEma(close, p)
            features["sma_$p"] = sma.lastValid()
            features["ema_$p"] = ema.lastValid()
        }

        // Momentum
        val rsi = calcRsi(close)
        features["rsi"] = rsi.lastValid()

        val (macd, signal, hist) = calcMacd(close)
        features["macd"]           = macd.lastValid()
        features["macd_signal"]    = signal.lastValid()
        features["macd_histogram"] = hist.lastValid()

        val (stochK, stochD) = calcStochastic(high, low, close)
        features["stochastic_k"] = stochK.lastValid()
        features["stochastic_d"] = stochD.lastValid()

        // Volatility
        val (bbUpper, bbMid, bbLower) = calcBollingerBands(close)
        features["bollinger_upper"] = bbUpper.lastValid()
        features["bollinger_mid"]   = bbMid.lastValid()
        features["bollinger_lower"] = bbLower.lastValid()

        val atr = calcAtr(high, low, close)
        features["atr"] = atr.lastValid()

        // Trend
        val adx = calcAdx(high, low, close)
        features["adx"] = adx.lastValid()

        // Volume
        val obv = calcObv(close, volume)
        features["obv"] = if (obv.isNotEmpty()) obv[last] else Double.NaN

        // Fibonacci
        val fib = calcFibonacciLevels(high, low)
        features["fib_38_2"] = fib["fib_38_2"] ?: Double.NaN
        features["fib_50"]   = fib["fib_50"]   ?: Double.NaN
        features["fib_61_8"] = fib["fib_61_8"] ?: Double.NaN

        // Market regime score (numeric encoding: bull=1, sideways=0.5, volatile=0.25, bear=0)
        val regime = detectMarketRegime(close, high, low)
        features["market_regime_score"] = when (regime) {
            "bull"     -> 1.0
            "sideways" -> 0.5
            "volatile" -> 0.25
            else       -> 0.0 // bear
        }

        return features
    }

    // ── Market Regime Detection (Section 2.4 – Agent 2) ─────────────────

    /**
     * Classifies the current market into one of four regimes based on
     * trend (SMA-50), momentum, volatility (ATR/price), and ADX.
     */
    fun detectMarketRegime(
        close: DoubleArray,
        high: DoubleArray,
        low: DoubleArray
    ): String {
        if (close.size < 51) return "sideways"

        val last = close.size - 1
        val sma50 = calcSma(close, 50)
        val sma50Val = sma50.lastValid()

        val atr = calcAtr(high, low, close)
        val atrVal = atr.lastValid()

        val adx = calcAdx(high, low, close)
        val adxVal = adx.lastValid()

        val priceAboveSma = close[last] > sma50Val

        // Short-term momentum (10-bar rate of change)
        val lookback = min(10, last)
        val momentum = (close[last] - close[last - lookback]) / close[last - lookback]

        // Volatility ratio: ATR as fraction of price
        val volRatio = if (close[last] != 0.0 && !atrVal.isNaN()) atrVal / close[last] else 0.0

        // High volatility threshold (≈ top quartile of typical equities)
        if (volRatio > HIGH_VOLATILITY_THRESHOLD) return "volatile"

        if (!adxVal.isNaN() && adxVal < LOW_ADX_THRESHOLD) return "sideways"

        return if (priceAboveSma && momentum > 0) "bull" else "bear"
    }

    // ── Data Quality Score (Section 2.4 – Agent 1) ──────────────────────

    /**
     * Returns a composite quality score in [0.0, 1.0] combining:
     * - completeness (NaN ratio)
     * - recency (penalty when data is > 30 days old)
     * - volume (optimal ≥ 1 000 data points)
     */
    fun computeDataQualityScore(close: DoubleArray, lastTimestamp: Long): Double {
        if (close.isEmpty()) return 0.0

        // Completeness: fraction of non-NaN values
        val validCount = close.count { !it.isNaN() }
        val completeness = validCount.toDouble() / close.size

        // Recency: full score if ≤ 30 days old, linear decay over 90 days
        val nowMs = System.currentTimeMillis()
        val ageMs = nowMs - lastTimestamp
        val ageDays = ageMs / MILLIS_PER_DAY
        val recency = when {
            ageDays <= RECENCY_FULL_SCORE_DAYS -> 1.0
            ageDays >= RECENCY_ZERO_SCORE_DAYS -> 0.0
            else -> 1.0 - (ageDays - RECENCY_FULL_SCORE_DAYS) / RECENCY_DECAY_SPAN
        }.coerceIn(0.0, 1.0)

        // Volume: linear ramp — 0 at 0 points, 1.0 at OPTIMAL_DATA_POINT_COUNT+
        val volume = min(1.0, validCount / OPTIMAL_DATA_POINT_COUNT)

        return (completeness * COMPLETENESS_WEIGHT +
                recency * RECENCY_WEIGHT +
                volume * VOLUME_WEIGHT).coerceIn(0.0, 1.0)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Return the last non-NaN value, or NaN if none exists. */
    private fun DoubleArray.lastValid(): Double {
        for (i in lastIndex downTo 0) {
            if (!this[i].isNaN()) return this[i]
        }
        return Double.NaN
    }

    /** SMA applied only over non-NaN entries, preserving NaN gaps. */
    private fun smaOfNonNan(data: DoubleArray, period: Int): DoubleArray {
        val result = DoubleArray(data.size) { Double.NaN }
        if (data.size < period) return result
        for (i in data.indices) {
            if (data[i].isNaN()) continue
            // Collect up to [period] non-NaN predecessors (inclusive)
            var count = 0
            var sum = 0.0
            var j = i
            while (j >= 0 && count < period) {
                if (!data[j].isNaN()) { sum += data[j]; count++ }
                j--
            }
            if (count == period) result[i] = sum / period
        }
        return result
    }
}
