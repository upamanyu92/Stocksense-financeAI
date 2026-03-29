package com.stocksense.app.util

import com.stocksense.app.data.model.ImportedMFHolding
import com.stocksense.app.data.model.ImportedPortfolioSummary
import com.stocksense.app.data.model.ImportedStockHolding
import java.io.InputStream

/**
 * Parses the two holdings XLSX formats produced by Indian brokers / Groww:
 *
 * **Format A – Mutual Fund Holdings** (`Holdings_Statement_*.xlsx`):
 *  Row 12 (0-idx): Summary header  →  Total Investments, Current Portfolio Value, ...
 *  Row 13 (0-idx): Summary values
 *  Row 20 (0-idx): Column headers  →  Scheme Name, AMC, Category, ...
 *  Row 22+         : Data rows
 *
 * **Format B – Stock Holdings** (`Stocks_Holdings_Statement_*.xlsx`):
 *  Row 6  (0-idx): Invested Value
 *  Row 7  (0-idx): Closing Value
 *  Row 10 (0-idx): Column headers  →  Stock Name, ISIN, Quantity, ...
 *  Row 11+        : Data rows
 */
object PortfolioXlsxParser {

    sealed class ParseResult {
        data class MutualFunds(
            val summary: ImportedPortfolioSummary,
            val holdings: List<ImportedMFHolding>
        ) : ParseResult()

        data class Stocks(
            val summary: ImportedPortfolioSummary,
            val holdings: List<ImportedStockHolding>
        ) : ParseResult()

        data class Error(val message: String) : ParseResult()
    }

    fun parse(inputStream: InputStream, fileName: String): ParseResult {
        return try {
            val rows = XlsxReader.readSheet(inputStream)
            // Primary: filename-based detection (covers test scenarios where rows are sparse)
            val nameUpper = fileName.uppercase()
            val filenameIsMF = nameUpper.contains("HOLDINGS_STATEMENT") &&
                !nameUpper.contains("STOCKS_HOLDINGS")
            val filenameIsStock = nameUpper.contains("STOCKS_HOLDINGS_STATEMENT")
            when {
                filenameIsStock || (!filenameIsMF && isStockFormat(rows)) -> parseStocks(rows, fileName)
                filenameIsMF || isMFFormat(rows) -> parseMutualFunds(rows, fileName)
                else -> ParseResult.Error("Unrecognised holdings format in $fileName")
            }
        } catch (e: Exception) {
            ParseResult.Error("Failed to parse $fileName: ${e.message}")
        }
    }

    // ── Format detection ──────────────────────────────────────────────────────

    private fun isMFFormat(rows: List<List<String>>): Boolean =
        rows.getOrNull(20)?.getOrNull(0)?.contains("Scheme Name", ignoreCase = true) == true ||
            rows.getOrNull(19)?.getOrNull(0)?.contains("Scheme Name", ignoreCase = true) == true

    private fun isStockFormat(rows: List<List<String>>): Boolean =
        rows.getOrNull(10)?.getOrNull(0)?.contains("Stock Name", ignoreCase = true) == true ||
            rows.getOrNull(9)?.getOrNull(0)?.contains("Stock Name", ignoreCase = true) == true

    // ── Mutual Funds parser ───────────────────────────────────────────────────

    private fun parseMutualFunds(rows: List<List<String>>, fileName: String): ParseResult {
        // Locate header row
        val headerIdx = rows.indexOfFirst { row ->
            row.any { it.contains("Scheme Name", ignoreCase = true) }
        }
        if (headerIdx < 0) return ParseResult.Error("Could not find MF header row")

        // Summary (row 13 = index 13, but scan for it)
        val summaryValRow = rows.indexOfFirst { row ->
            row.firstOrNull()?.toDoubleOrNull() != null &&
                (row.getOrNull(1)?.toDoubleOrNull() != null)
        }
        val totalInvested = rows.getOrNull(summaryValRow)?.getOrNull(0)?.parseDouble() ?: 0.0
        val currentValue  = rows.getOrNull(summaryValRow)?.getOrNull(1)?.parseDouble() ?: 0.0
        val pnl = currentValue - totalInvested
        val pnlPct = if (totalInvested > 0) pnl / totalInvested * 100 else 0.0

        val dataRows = rows.drop(headerIdx + 1)   // blank row filtered by row[0].isBlank() below
        val holdings = dataRows.mapNotNull { row ->
            if (row.size < 9 || row[0].isBlank()) return@mapNotNull null
            try {
                ImportedMFHolding(
                    schemeName   = row.getOrElse(0) { "" },
                    amc          = row.getOrElse(1) { "" },
                    category     = row.getOrElse(2) { "" },
                    subCategory  = row.getOrElse(3) { "" },
                    folioNo      = row.getOrElse(4) { "" },
                    source       = row.getOrElse(5) { "" },
                    units        = row.getOrElse(6) { "0" }.parseDouble(),
                    investedValue = row.getOrElse(7) { "0" }.parseDouble(),
                    currentValue  = row.getOrElse(8) { "0" }.parseDouble(),
                    returns       = row.getOrElse(9) { "0" }.parseDouble(),
                    xirr          = row.getOrElse(10) { "—" }
                )
            } catch (_: Exception) { null }
        }

        return ParseResult.MutualFunds(
            summary = ImportedPortfolioSummary(fileName, totalInvested, currentValue, pnl, pnlPct),
            holdings = holdings
        )
    }

    // ── Stocks parser ─────────────────────────────────────────────────────────

    private fun parseStocks(rows: List<List<String>>, fileName: String): ParseResult {
        val headerIdx = rows.indexOfFirst { row ->
            row.any { it.contains("Stock Name", ignoreCase = true) }
        }
        if (headerIdx < 0) return ParseResult.Error("Could not find stocks header row")

        // Summary scan
        val investedRow = rows.indexOfFirst { it.firstOrNull()?.contains("Invested Value", ignoreCase = true) == true }
        val closingRow  = rows.indexOfFirst { it.firstOrNull()?.contains("Closing Value", ignoreCase = true) == true }
        val totalInvested = rows.getOrNull(investedRow)?.getOrNull(1)?.parseDouble() ?: 0.0
        val currentValue  = rows.getOrNull(closingRow)?.getOrNull(1)?.parseDouble() ?: 0.0
        val pnl = currentValue - totalInvested
        val pnlPct = if (totalInvested > 0) pnl / totalInvested * 100 else 0.0

        val dataRows = rows.drop(headerIdx + 1)
        val holdings = dataRows.mapNotNull { row ->
            if (row.size < 8 || row[0].isBlank()) return@mapNotNull null
            try {
                ImportedStockHolding(
                    stockName    = row.getOrElse(0) { "" },
                    isin         = row.getOrElse(1) { "" },
                    quantity     = row.getOrElse(2) { "0" }.parseDouble(),
                    avgBuyPrice  = row.getOrElse(3) { "0" }.parseDouble(),
                    buyValue     = row.getOrElse(4) { "0" }.parseDouble(),
                    closingPrice = row.getOrElse(5) { "0" }.parseDouble(),
                    closingValue = row.getOrElse(6) { "0" }.parseDouble(),
                    unrealisedPnl = row.getOrElse(7) { "0" }.parseDouble()
                )
            } catch (_: Exception) { null }
        }

        return ParseResult.Stocks(
            summary = ImportedPortfolioSummary(fileName, totalInvested, currentValue, pnl, pnlPct),
            holdings = holdings
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun String.parseDouble(): Double =
        this.replace(",", "").replace("%", "").trim().toDoubleOrNull() ?: 0.0
}

