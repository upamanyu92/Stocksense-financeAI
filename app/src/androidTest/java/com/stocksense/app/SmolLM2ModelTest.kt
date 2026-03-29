package com.stocksense.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.stocksense.app.engine.BitNetModelDownloader
import com.stocksense.app.engine.LLMInsightEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SmolLM2-135M model verification instrumented tests.
 *
 * Execution order:
 *   1. bundledModelCopiedToFilesDir  – ensures the GGUF was extracted from assets
 *   2. modelFileIntegrity           – basic GGUF header check (magic bytes)
 *   3. llmEngineStatusWithoutNative – template-fallback smoke test (no JNI required)
 *   4. portfolioAnalysisTemplate    – verifies template portfolio analysis works
 *   5. xlsxParserMFFormat           – parses a synthetic MF XLSX in memory
 *   6. xlsxParserStocksFormat       – parses a synthetic Stocks XLSX in memory
 *   7. importedHoldingPnlCalc       – unit logic for P&L calculations
 *
 * Run on-device:
 *   ./gradlew connectedDebugAndroidTest
 *   OR  adb shell am instrument -w \
 *       -e class com.stocksense.app.SmolLM2ModelTest \
 *       com.stocksense.app.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@LargeTest
class SmolLM2ModelTest {

    private lateinit var ctx: Context
    private lateinit var downloader: BitNetModelDownloader
    private lateinit var llmEngine: LLMInsightEngine

    companion object {
        private const val TAG = "SmolLM2Test"
        // Expected minimum size for a valid GGUF (~80 MB minimum after quantization)
        private const val MIN_MODEL_SIZE_BYTES = 70 * 1024 * 1024L
        // GGUF magic bytes: "GGUF" = 0x47 0x47 0x55 0x46
        private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46)
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        downloader = BitNetModelDownloader(ctx)
        llmEngine = LLMInsightEngine(ctx)
    }

    // ─── Test 1: Model copied from assets to filesDir ─────────────────────────

    @Test
    fun t1_bundledModelCopiedToFilesDir(): Unit = runBlocking {
        Log.i(TAG, "=== TEST 1: Bundled model copy ===")

        val targetFile = File(downloader.modelsDir, BitNetModelDownloader.BUNDLED_MODEL_FILE)

        if (!targetFile.exists()) {
            // Trigger copy from assets
            val copied = downloader.copyBundledModelIfNeeded()
            if (copied) {
                Log.i(TAG, "✓ Model copied from APK assets → ${targetFile.absolutePath}")
            } else {
                Log.w(TAG, "Model not bundled in this APK — checking if already on disk")
            }
        } else {
            Log.i(TAG, "✓ Model already on disk: ${targetFile.absolutePath}")
        }

        val exists = targetFile.exists()
        val sizeOk = exists && targetFile.length() > MIN_MODEL_SIZE_BYTES

        Log.i(TAG, "File exists: $exists, size: ${if (exists) "${targetFile.length() / 1024 / 1024} MB" else "N/A"}")

        if (!exists) {
            // Log asset list for debugging
            try {
                val assets = ctx.assets.list("models") ?: emptyArray()
                Log.w(TAG, "Assets/models contents: ${assets.joinToString()}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not list assets: ${e.message}")
            }
            fail(
                "SmolLM2 model not found at ${targetFile.absolutePath}\n" +
                "Run: ./gradlew downloadBundledModel  before building the APK"
            )
        }

        assertTrue("Model file must be > ${MIN_MODEL_SIZE_BYTES / 1024 / 1024} MB", sizeOk)
        Log.i(TAG, "✓ PASS – model on disk, ${targetFile.length() / 1024 / 1024} MB")
    }

    // ─── Test 2: GGUF file integrity (magic bytes) ────────────────────────────

    @Test
    fun t2_modelFileGgufIntegrity() {
        Log.i(TAG, "=== TEST 2: GGUF file integrity ===")

        val modelFile = File(downloader.modelsDir, BitNetModelDownloader.BUNDLED_MODEL_FILE)

        if (!modelFile.exists()) {
            Log.w(TAG, "Model not on disk – skipping integrity check")
            return
        }

        val header = ByteArray(4)
        modelFile.inputStream().use { it.read(header) }

        Log.i(TAG, "First 4 bytes: ${header.map { "0x%02X".format(it) }.joinToString()}")

        assertArrayEquals(
            "File must start with GGUF magic bytes",
            GGUF_MAGIC,
            header
        )

        // Also verify version word (bytes 4–7, should be > 0)
        val versionBytes = ByteArray(4)
        modelFile.inputStream().use { s -> s.skip(4); s.read(versionBytes) }
        val version = versionBytes.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        assertTrue("GGUF version should be > 0 (got $version)", version > 0)

        Log.i(TAG, "✓ PASS – valid GGUF file (version bytes: ${versionBytes.map { "0x%02X".format(it) }.joinToString()})")
    }

    // ─── Test 3: LLM engine status (no native JNI) ────────────────────────────

    @Test
    fun t3_llmEngineStatusWithoutNative() {
        Log.i(TAG, "=== TEST 3: LLM engine status ===")

        val metrics = llmEngine.getMetrics()
        Log.i(TAG, "LLM status: ${metrics.status}")
        Log.i(TAG, "Native available: ${metrics.isNativeAvailable}")
        Log.i(TAG, "Model downloaded: ${metrics.isModelDownloaded}")
        Log.i(TAG, "Model file: ${metrics.modelFileName}")

        // The engine must at least initialise (NATIVE_UNAVAILABLE or MODEL_NOT_DOWNLOADED are fine)
        assertNotNull("LLM metrics should not be null", metrics)

        val validInitialStatuses = setOf(
            com.stocksense.app.engine.LlmStatus.NATIVE_UNAVAILABLE,
            com.stocksense.app.engine.LlmStatus.MODEL_NOT_DOWNLOADED,
            com.stocksense.app.engine.LlmStatus.READY,
            com.stocksense.app.engine.LlmStatus.TEMPLATE_FALLBACK
        )
        assertTrue(
            "Status must be a valid initial state (got ${metrics.status})",
            metrics.status in validInitialStatuses
        )

        Log.i(TAG, "✓ PASS – engine initialised, status=${metrics.status}")
    }

    // ─── Test 4: Portfolio analysis template fallback ─────────────────────────

    @Test
    fun t4_portfolioAnalysisTemplateFallback(): Unit = runBlocking {
        Log.i(TAG, "=== TEST 4: Portfolio analysis (template fallback) ===")

        val samplePortfolio = """
=== Imported Stock Holdings ===
Invested: ₹226371.60, Current: ₹196515.32, P&L: ₹-29856.28 (-13.19%)
NHPC LTD: qty=624, avg=₹85.27, closing=₹76.59, unrealised P&L=₹-5416.32 (-10.18%)
SUZLON ENERGY LIMITED: qty=100, avg=₹77.76, closing=₹40.82, unrealised P&L=₹-3694.00 (-47.51%)
NATIONAL ALUMINIUM CO LTD: qty=25, avg=₹167.85, closing=₹371.00, unrealised P&L=₹5078.75 (+121.0%)

=== Imported Mutual Fund Holdings ===
Invested: ₹332084.01, Current: ₹355399.10, P&L: ₹23315.09 (+7.02%)
Equity:
  Mirae Asset ELSS Tax Saver Fund Direct Growth: invested=₹94495.39, current=₹111967.02, returns=₹17471.64, XIRR=14.28%
  Axis Small Cap Fund Direct Growth: invested=₹9499.43, current=₹9026.10, returns=₹-473.33, XIRR=-4.53%
Commodities:
  LIC MF Gold ETF FoF Direct Growth: invested=₹41497.97, current=₹51236.53, returns=₹9738.55, XIRR=49.0%
""".trimIndent()

        val result = withTimeoutOrNull(30_000L) {
            llmEngine.analyzePortfolio(samplePortfolio)
        }

        assertNotNull("Portfolio analysis should return a result within 30 s", result)
        val analysis = result!!

        Log.i(TAG, "Analysis length: ${analysis.length} chars")
        Log.i(TAG, "Analysis preview:\n${analysis.take(400)}")

        assertTrue("Analysis result must not be blank", analysis.isNotBlank())
        assertTrue("Analysis must be at least 100 chars (got ${analysis.length})", analysis.length >= 100)

        // Check it mentions finance-relevant keywords
        val lower = analysis.lowercase()
        val hasFinanceKeywords = listOf("portfolio", "hold", "exit", "sell", "risk", "return", "invest", "fund")
            .any { lower.contains(it) }
        assertTrue("Analysis should contain finance-relevant language", hasFinanceKeywords)

        Log.i(TAG, "✓ PASS – portfolio analysis returned ${analysis.length} chars")
    }

    // ─── Test 5: XLSX parser — Mutual Funds format ────────────────────────────

    @Test
    fun t5_xlsxParserMutualFundsFormat() {
        Log.i(TAG, "=== TEST 5: XLSX parser – Mutual Funds ===")

        val xlsxFile = File(ctx.cacheDir, "Holdings_Statement_2026-03-28.xlsx")
        buildMFXlsx(xlsxFile)

        val result = xlsxFile.inputStream().use {
            com.stocksense.app.util.PortfolioXlsxParser.parse(it, xlsxFile.name)
        }

        Log.i(TAG, "Parse result type: ${result::class.simpleName}")

        val errMsg = if (result is com.stocksense.app.util.PortfolioXlsxParser.ParseResult.Error)
            result.message else ""
        assertTrue(
            "Should parse as MutualFunds, got ${result::class.simpleName}: $errMsg",
            result is com.stocksense.app.util.PortfolioXlsxParser.ParseResult.MutualFunds
        )

        val mfResult = result as com.stocksense.app.util.PortfolioXlsxParser.ParseResult.MutualFunds
        assertTrue("Should have at least 1 holding (got ${mfResult.holdings.size})", mfResult.holdings.isNotEmpty())

        val first = mfResult.holdings.first()
        Log.i(TAG, "First holding: ${first.schemeName}, XIRR=${first.xirr}, value=${first.currentValue}")
        assertTrue("Scheme name should not be blank", first.schemeName.isNotBlank())

        Log.i(TAG, "✓ PASS – parsed ${mfResult.holdings.size} MF holdings")
    }

    // ─── Test 6: XLSX parser — Stocks format ─────────────────────────────────

    @Test
    fun t6_xlsxParserStocksFormat() {
        Log.i(TAG, "=== TEST 6: XLSX parser – Stocks ===")

        // Use the real XLSX from assets if available
        val stocksFile = File(ctx.cacheDir, "Stocks_Holdings_Statement_8990061013_2026-03-28.xlsx")
        buildStocksXlsx(stocksFile)

        val result = stocksFile.inputStream().use {
            com.stocksense.app.util.PortfolioXlsxParser.parse(it, stocksFile.name)
        }

        Log.i(TAG, "Parse result type: ${result::class.simpleName}")

        val errMsg2 = if (result is com.stocksense.app.util.PortfolioXlsxParser.ParseResult.Error)
            result.message else ""
        assertTrue(
            "Should parse as Stocks, got ${result::class.simpleName}: $errMsg2",
            result is com.stocksense.app.util.PortfolioXlsxParser.ParseResult.Stocks
        )

        val sResult = result as com.stocksense.app.util.PortfolioXlsxParser.ParseResult.Stocks
        assertTrue("Should have at least 1 stock (got ${sResult.holdings.size})", sResult.holdings.isNotEmpty())

        val first = sResult.holdings.first()
        Log.i(TAG, "First stock: ${first.stockName}, qty=${first.quantity}, pnl=${first.unrealisedPnl}")
        assertTrue("Stock name should not be blank", first.stockName.isNotBlank())
        assertTrue("Quantity should be > 0", first.quantity > 0)

        Log.i(TAG, "✓ PASS – parsed ${sResult.holdings.size} stock holdings")
    }

    // ─── Test 7: ImportedHolding P&L calculations ─────────────────────────────

    @Test
    fun t7_importedHoldingPnlCalc() {
        Log.i(TAG, "=== TEST 7: Imported holding P&L calculations ===")

        val stock = com.stocksense.app.data.model.ImportedStockHolding(
            stockName = "NHPC LTD",
            isin = "INE848E01016",
            quantity = 624.0,
            avgBuyPrice = 85.27,
            buyValue = 53208.48,
            closingPrice = 76.59,
            closingValue = 47792.16,
            unrealisedPnl = -5416.32
        )

        val expectedPnlPct = (-5416.32 / 53208.48) * 100
        assertEquals("P&L % should be ~-10.18%", expectedPnlPct, stock.pnlPercent, 0.5)
        assertTrue("P&L % should be negative", stock.pnlPercent < 0)

        val mf = com.stocksense.app.data.model.ImportedMFHolding(
            schemeName = "LIC MF Gold ETF FoF Direct Growth",
            amc = "LIC Mutual Fund",
            category = "Commodities",
            subCategory = "Gold",
            folioNo = "57734853924",
            source = "Groww",
            units = 1020.337,
            investedValue = 31498.48,
            currentValue = 39474.29,
            returns = 7975.81,
            xirr = "51.13%"
        )

        assertTrue("MF returns should be positive", mf.returns > 0)
        assertTrue("MF P&L % should be positive", mf.pnlPercent > 0)
        assertEquals("MF P&L % should be ~25.3%", 25.3, mf.pnlPercent, 1.0)

        Log.i(TAG, "Stock P&L %: ${"%.2f".format(stock.pnlPercent)}%  (expected ~-10.18%)")
        Log.i(TAG, "MF P&L %:    ${"%.2f".format(mf.pnlPercent)}%  (expected ~25.3%)")
        Log.i(TAG, "✓ PASS – P&L calculations correct")
    }

    // ─── Helpers: build minimal XLSX files for parser tests ──────────────────

    /**
     * Builds a minimal XLSX (zipped XML) mimicking the Mutual Fund format.
     * Row 20 (0-indexed) = header, Row 22 = first data row.
     */
    private fun buildMFXlsx(dest: File) {
        val sharedStrings = listOf(
            "Scheme Name", "AMC", "Category", "Sub-category", "Folio No.", "Source",
            "Mirae Asset ELSS Tax Saver Fund Direct Growth", "Mirae Asset Mutual Fund",
            "Equity", "ELSS", "79933909225", "Groww",
            "Personal Details", "Name", "Test User",
            "HOLDING SUMMARY", "Total Investments", "Current Portfolio Value",
            "Profit/Loss", "XIRR", "Holdings",
            "LIC MF Gold ETF FoF Direct Growth", "LIC Mutual Fund", "Commodities", "Gold",
            "57734853924"
        )

        val sstXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">""")
            sharedStrings.forEach { s -> append("<si><t>$s</t></si>") }
            append("</sst>")
        }

        // Build cells for 25 rows (headers at row 21 = index 20, data at 23 = index 22)
        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("<sheetData>")
            // Row 21 (1-indexed) = header (0-indexed row 20)
            append("""<row r="21">""")
            listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10).forEachIndexed { col, sIdx ->
                val colLetter = ('A' + col).toString()
                append("""<c r="${colLetter}21" t="s"><v>$sIdx</v></c>""")
            }
            append("</row>")
            // Row 22 = blank
            append("""<row r="22"></row>""")
            // Row 23 = first data row
            append("""<row r="23">""")
            // Scheme Name
            append("""<c r="A23" t="s"><v>6</v></c>""")
            // AMC
            append("""<c r="B23" t="s"><v>7</v></c>""")
            // Category
            append("""<c r="C23" t="s"><v>8</v></c>""")
            // Sub-category
            append("""<c r="D23" t="s"><v>9</v></c>""")
            // Folio
            append("""<c r="E23" t="s"><v>10</v></c>""")
            // Source
            append("""<c r="F23" t="s"><v>11</v></c>""")
            // Units
            append("""<c r="G23"><v>2197.11</v></c>""")
            // Invested Value
            append("""<c r="H23"><v>94495.39</v></c>""")
            // Current Value
            append("""<c r="I23"><v>111967.02</v></c>""")
            // Returns
            append("""<c r="J23"><v>17471.63</v></c>""")
            // XIRR (use plain numeric, not shared string)
            append("""<c r="K23"><v>14.28</v></c>""")
            append("</row>")
            append("</sheetData></worksheet>")
        }

        writeXlsx(dest, sstXml, sheetXml)
    }

    private fun buildStocksXlsx(dest: File) {
        val sharedStrings = listOf(
            "Stock Name", "ISIN", "Quantity", "Average buy price",
            "Buy value", "Closing price", "Closing value", "Unrealised PnL",
            "NHPC LTD", "INE848E01016",
            "Invested Value", "Closing Value"
        )

        val sstXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">""")
            sharedStrings.forEach { s -> append("<si><t>$s</t></si>") }
            append("</sst>")
        }

        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("<sheetData>")
            // Row 7: Invested Value (sharedStrings[10]="Invested Value")
            append("""<row r="7"><c r="A7" t="s"><v>10</v></c><c r="B7"><v>226371.60</v></c></row>""")
            // Row 8: Closing Value (sharedStrings[11]="Closing Value")
            append("""<row r="8"><c r="A8" t="s"><v>11</v></c><c r="B8"><v>196515.32</v></c></row>""")
            // Row 11: headers — Stock Name(0),ISIN(1),Qty(2),AvgBuy(3),BuyVal(4),ClosPx(5),ClosVal(6),PnL(7)
            append("""<row r="11">""")
            listOf(0, 1, 2, 3, 4, 5, 6, 7).forEachIndexed { col, sIdx ->
                val colLetter = ('A' + col).toString()
                append("""<c r="${colLetter}11" t="s"><v>$sIdx</v></c>""")
            }
            append("</row>")
            // Row 12: NHPC (sharedStrings[8]="NHPC LTD", [9]="INE848E01016")
            append("""<row r="12">""")
            append("""<c r="A12" t="s"><v>8</v></c>""")   // NHPC LTD
            append("""<c r="B12" t="s"><v>9</v></c>""")   // INE848E01016
            append("""<c r="C12"><v>624</v></c>""")        // qty
            append("""<c r="D12"><v>85.27</v></c>""")      // avg
            append("""<c r="E12"><v>53208.48</v></c>""")   // buy value
            append("""<c r="F12"><v>76.59</v></c>""")      // closing price
            append("""<c r="G12"><v>47792.16</v></c>""")   // closing value
            append("""<c r="H12"><v>-5416.32</v></c>""")   // pnl
            append("</row>")
            append("</sheetData></worksheet>")
        }

        writeXlsx(dest, sstXml, sheetXml)
    }

    private fun writeXlsx(dest: File, sstXml: String, sheetXml: String) {
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>"""
        val relsRoot = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Holdings" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
        val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>"""

        java.util.zip.ZipOutputStream(FileOutputStream(dest)).use { zos ->
            fun addEntry(name: String, content: String) {
                zos.putNextEntry(java.util.zip.ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            addEntry("[Content_Types].xml", contentTypes)
            addEntry("_rels/.rels", relsRoot)
            addEntry("xl/workbook.xml", workbook)
            addEntry("xl/_rels/workbook.xml.rels", wbRels)
            addEntry("xl/sharedStrings.xml", sstXml)
            addEntry("xl/worksheets/sheet1.xml", sheetXml)
        }
    }
}

