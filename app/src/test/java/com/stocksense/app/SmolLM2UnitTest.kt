package com.stocksense.app

import com.stocksense.app.data.model.ImportedMFHolding
import com.stocksense.app.data.model.ImportedStockHolding
import com.stocksense.app.engine.BitNetModelDownloader
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * JVM (host-side) unit tests for:
 *  - PortfolioXlsxParser format detection
 *  - XlsxReader cell parsing
 *  - ImportedHolding P&L math
 *  - BitNetModelDownloader constant names
 */
class SmolLM2UnitTest {

    // ── ImportedHolding P&L calculations ──────────────────────────────────────

    @Test
    fun `imported stock holding pnl percent calculated correctly`() {
        val h = ImportedStockHolding(
            stockName = "NHPC LTD", isin = "INE848E01016",
            quantity = 624.0, avgBuyPrice = 85.27, buyValue = 53208.48,
            closingPrice = 76.59, closingValue = 47792.16, unrealisedPnl = -5416.32
        )
        assertEquals(-10.18, h.pnlPercent, 0.1)
        assertTrue(h.pnlPercent < 0)
    }

    @Test
    fun `imported stock holding positive pnl percent`() {
        val h = ImportedStockHolding(
            stockName = "NALCO", isin = "INE139A01034",
            quantity = 25.0, avgBuyPrice = 167.85, buyValue = 4196.25,
            closingPrice = 371.0, closingValue = 9275.0, unrealisedPnl = 5078.75
        )
        assertTrue("P&L should be positive", h.pnlPercent > 0)
        assertEquals(121.0, h.pnlPercent, 2.0)
    }

    @Test
    fun `imported mf holding pnl percent calculated correctly`() {
        val mf = ImportedMFHolding(
            schemeName = "Mirae Asset ELSS", amc = "Mirae Asset",
            category = "Equity", subCategory = "ELSS",
            folioNo = "12345", source = "Groww",
            units = 2197.11, investedValue = 94495.39,
            currentValue = 111967.02, returns = 17471.63, xirr = "14.28%"
        )
        assertTrue("MF returns should be positive", mf.returns > 0)
        assertTrue("MF pnlPercent should be positive", mf.pnlPercent > 0)
        assertEquals(18.49, mf.pnlPercent, 1.0)
    }

    @Test
    fun `imported mf holding negative returns`() {
        val mf = ImportedMFHolding(
            schemeName = "Axis Small Cap Fund", amc = "Axis MF",
            category = "Equity", subCategory = "Small Cap",
            folioNo = "910176003372", source = "Groww",
            units = 81.77, investedValue = 9499.43,
            currentValue = 9026.10, returns = -473.33, xirr = "-4.53%"
        )
        assertTrue("MF returns should be negative", mf.returns < 0)
        assertTrue("MF pnlPercent should be negative", mf.pnlPercent < 0)
    }

    // ── BitNetModelDownloader constants ───────────────────────────────────────

    @Test
    fun `bundled model constant names are correct`() {
        assertEquals(
            "smollm2-135m-instruct-v0.2-q4_k_m.gguf",
            BitNetModelDownloader.BUNDLED_MODEL_FILE
        )
        assertEquals(
            "models/smollm2-135m-instruct-v0.2-q4_k_m.gguf",
            BitNetModelDownloader.BUNDLED_MODEL_ASSET
        )
    }

    // ── XlsxReader + PortfolioXlsxParser (in-memory XLSX) ────────────────────

    @Test
    fun `xlsxReader parses shared strings and numeric cells`() {
        val xlsx = buildMinimalXlsx(
            sharedStrings = listOf("Hello", "World"),
            rows = mapOf(
                1 to mapOf("A" to CellSpec("s", "0"), "B" to CellSpec("s", "1")),
                2 to mapOf("A" to CellSpec("", "42.5"), "B" to CellSpec("", "100"))
            )
        )
        val rows = com.stocksense.app.util.XlsxReader.readSheet(ByteArrayInputStream(xlsx))

        assertTrue("Should have 2 rows", rows.size >= 2)
        assertEquals("Hello", rows[0][0])
        assertEquals("World", rows[0][1])
        assertEquals("42.5", rows[1][0])
        assertEquals("100", rows[1][1])
    }

    @Test
    fun `portfolioXlsxParser detects mutual fund format`() {
        // Build XLSX with MF header at row 21 (0-indexed 20)
        val sharedStrings = listOf("Scheme Name", "AMC", "Category", "Sub-category",
            "Folio No.", "Source", "Units", "Invested Value", "Current Value", "Returns", "XIRR",
            "Test Fund", "Test AMC", "Equity", "Large Cap", "12345", "Groww", "14.28%")
        val rows = mutableMapOf<Int, Map<String, CellSpec>>()
        // Header row (1-indexed = 21)
        rows[21] = mapOf(
            "A" to CellSpec("s", "0"), "B" to CellSpec("s", "1"), "C" to CellSpec("s", "2"),
            "D" to CellSpec("s", "3"), "E" to CellSpec("s", "4"), "F" to CellSpec("s", "5"),
            "G" to CellSpec("s", "6"), "H" to CellSpec("s", "7"), "I" to CellSpec("s", "8"),
            "J" to CellSpec("s", "9"), "K" to CellSpec("s", "10")
        )
        // Blank row 22
        // Data row 23
        rows[23] = mapOf(
            "A" to CellSpec("s", "11"), "B" to CellSpec("s", "12"), "C" to CellSpec("s", "13"),
            "D" to CellSpec("s", "14"), "E" to CellSpec("s", "15"), "F" to CellSpec("s", "16"),
            "G" to CellSpec("", "2197.11"), "H" to CellSpec("", "94495.39"),
            "I" to CellSpec("", "111967.02"), "J" to CellSpec("", "17471.63"),
            "K" to CellSpec("s", "17")
        )
        val xlsx = buildMinimalXlsx(sharedStrings, rows)
        val result = com.stocksense.app.util.PortfolioXlsxParser.parse(
            ByteArrayInputStream(xlsx), "Holdings_Statement_2026-03-28.xlsx"
        )

        assertTrue(
            "Expected MutualFunds result, got ${result::class.simpleName}",
            result is com.stocksense.app.util.PortfolioXlsxParser.ParseResult.MutualFunds
        )
        val mfResult = result as com.stocksense.app.util.PortfolioXlsxParser.ParseResult.MutualFunds
        assertTrue("Should parse at least 1 MF holding", mfResult.holdings.isNotEmpty())
        assertEquals("Test Fund", mfResult.holdings.first().schemeName)
    }

    @Test
    fun `portfolioXlsxParser detects stocks format`() {
        val sharedStrings = listOf(
            "Stock Name", "ISIN", "Quantity", "Average buy price",
            "Buy value", "Closing price", "Closing value", "Unrealised P&L",
            "NHPC LTD", "INE848E01016",
            "Invested Value", "Closing Value"
        )
        val rows = mutableMapOf<Int, Map<String, CellSpec>>()
        // Summary rows
        rows[7] = mapOf("A" to CellSpec("s", "10"), "B" to CellSpec("", "226371.60"))
        rows[8] = mapOf("A" to CellSpec("s", "11"), "B" to CellSpec("", "196515.32"))
        // Header row 11
        rows[11] = mapOf(
            "A" to CellSpec("s", "0"), "B" to CellSpec("s", "1"), "C" to CellSpec("s", "2"),
            "D" to CellSpec("s", "3"), "E" to CellSpec("s", "4"), "F" to CellSpec("s", "5"),
            "G" to CellSpec("s", "6"), "H" to CellSpec("s", "7")
        )
        // Data row 12
        rows[12] = mapOf(
            "A" to CellSpec("s", "8"), "B" to CellSpec("s", "9"),
            "C" to CellSpec("", "624"), "D" to CellSpec("", "85.27"),
            "E" to CellSpec("", "53208.48"), "F" to CellSpec("", "76.59"),
            "G" to CellSpec("", "47792.16"), "H" to CellSpec("", "-5416.32")
        )
        val xlsx = buildMinimalXlsx(sharedStrings, rows)
        val result = com.stocksense.app.util.PortfolioXlsxParser.parse(
            ByteArrayInputStream(xlsx), "Stocks_Holdings_Statement_8990061013_2026-03-27.xlsx"
        )

        assertTrue(
            "Expected Stocks result, got ${result::class.simpleName}",
            result is com.stocksense.app.util.PortfolioXlsxParser.ParseResult.Stocks
        )
        val sResult = result as com.stocksense.app.util.PortfolioXlsxParser.ParseResult.Stocks
        assertTrue("Should parse at least 1 stock", sResult.holdings.isNotEmpty())
        assertEquals("NHPC LTD", sResult.holdings.first().stockName)
        assertEquals(624.0, sResult.holdings.first().quantity, 0.01)
    }

    // ── XLSX builder helper ───────────────────────────────────────────────────

    private data class CellSpec(val type: String, val value: String)

    private fun buildMinimalXlsx(
        sharedStrings: List<String>,
        rows: Map<Int, Map<String, CellSpec>>
    ): ByteArray {
        val sstXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            sharedStrings.forEach { s -> append("<si><t>${s.escapeXml()}</t></si>") }
            append("</sst>")
        }
        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.toSortedMap().forEach { (rowNum, cells) ->
                append("""<row r="$rowNum">""")
                cells.forEach { (col, spec) ->
                    val typeAttr = if (spec.type.isNotEmpty()) """ t="${spec.type}"""" else ""
                    append("""<c r="$col$rowNum"$typeAttr><v>${spec.value}</v></c>""")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/></Types>"""
        val relsRoot = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Holdings" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/></Relationships>"""

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun add(name: String, xml: String) {
                zos.putNextEntry(ZipEntry(name)); zos.write(xml.toByteArray()); zos.closeEntry()
            }
            add("[Content_Types].xml", contentTypes)
            add("_rels/.rels", relsRoot)
            add("xl/workbook.xml", workbook)
            add("xl/_rels/workbook.xml.rels", wbRels)
            add("xl/sharedStrings.xml", sstXml)
            add("xl/worksheets/sheet1.xml", sheetXml)
        }
        return baos.toByteArray()
    }

    private fun String.escapeXml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

