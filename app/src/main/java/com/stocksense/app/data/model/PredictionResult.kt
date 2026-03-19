package com.stocksense.app.data.model

/** Result from the PredictionEngine for a given stock symbol. */
data class PredictionResult(
    val symbol: String,
    val predictedPrice: Double,
    val confidence: Float,    // 0.0 – 1.0
    val direction: String,    // "UP" | "DOWN" | "NEUTRAL"
    val horizon: Int = 1,     // days ahead
    val features: Map<String, Float> = emptyMap()  // feature importances for explainability
)
