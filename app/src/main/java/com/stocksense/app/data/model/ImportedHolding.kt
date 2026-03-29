package com.stocksense.app.data.model

/**
 * Represents one row from the mutual-fund Holdings Statement XLSX.
 * Headers (row 20, 0-indexed): Scheme Name, AMC, Category, Sub-category,
 * Folio No., Source, Units, Invested Value, Current Value, Returns, XIRR
 */
data class ImportedMFHolding(
    val schemeName: String,
    val amc: String,
    val category: String,
    val subCategory: String,
    val folioNo: String,
    val source: String,
    val units: Double,
    val investedValue: Double,
    val currentValue: Double,
    val returns: Double,
    val xirr: String
) {
    val pnlPercent: Double
        get() = if (investedValue > 0) (returns / investedValue) * 100 else 0.0
}

/**
 * Represents one row from the Stocks Holdings Statement XLSX.
 * Headers (row 10, 0-indexed): Stock Name, ISIN, Quantity, Average buy price,
 * Buy value, Closing price, Closing value, Unrealised P&L
 */
data class ImportedStockHolding(
    val stockName: String,
    val isin: String,
    val quantity: Double,
    val avgBuyPrice: Double,
    val buyValue: Double,
    val closingPrice: Double,
    val closingValue: Double,
    val unrealisedPnl: Double
) {
    val pnlPercent: Double
        get() = if (buyValue > 0) (unrealisedPnl / buyValue) * 100 else 0.0
}

/** Summary of an imported portfolio file */
data class ImportedPortfolioSummary(
    val fileName: String,
    val totalInvested: Double,
    val currentValue: Double,
    val pnl: Double,
    val pnlPercent: Double
)

