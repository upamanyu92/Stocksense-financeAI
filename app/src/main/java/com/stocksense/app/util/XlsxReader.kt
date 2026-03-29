package com.stocksense.app.util

import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Lightweight XLSX reader for Android – no Apache POI required.
 *
 * XLSX files are ZIP archives. This reader:
 *  1. Unzips `xl/sharedStrings.xml` to build the Shared String Table (SST).
 *  2. Unzips `xl/worksheets/sheet1.xml` to iterate rows/cells.
 *  3. Returns a `List<List<String>>` (rows × columns) with every cell as a String.
 *
 * Limitations: single-sheet, no formula evaluation (values must be stored as cache).
 */
object XlsxReader {

    /** Read the first worksheet of an XLSX file and return all cells as strings. */
    fun readSheet(inputStream: InputStream): List<List<String>> {
        val zipBytes = inputStream.readBytes()
        val sst = readSharedStrings(zipBytes)
        return readWorksheet(zipBytes, sst)
    }

    // ── Shared String Table ───────────────────────────────────────────────────

    private fun readSharedStrings(zipBytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val zis = ZipInputStream(zipBytes.inputStream())
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "xl/sharedStrings.xml") {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(zis, "UTF-8")
                var inT = false
                val sb = StringBuilder()
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (parser.name == "t") { inT = true; sb.clear() }
                        }
                        XmlPullParser.TEXT -> {
                            if (inT) sb.append(parser.text)
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "t" && inT) { strings += sb.toString(); inT = false }
                        }
                    }
                    eventType = parser.next()
                }
                break
            }
            entry = zis.nextEntry
        }
        return strings
    }

    // ── Worksheet ─────────────────────────────────────────────────────────────

    private fun readWorksheet(zipBytes: ByteArray, sst: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val zis = ZipInputStream(zipBytes.inputStream())
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "xl/worksheets/sheet1.xml") {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(zis, "UTF-8")

                var currentRow = mutableListOf<String>()
                var cellType = ""       // t attribute: "s" = shared string, "str" = inline str, else numeric
                var cellRef = ""        // r attribute e.g. "A1"
                var inV = false
                val valueBuffer = StringBuilder()

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "row" -> {
                                currentRow = mutableListOf()
                            }
                            "c" -> {
                                cellType = parser.getAttributeValue(null, "t") ?: ""
                                cellRef = parser.getAttributeValue(null, "r") ?: ""
                            }
                            "v", "t" -> { inV = true; valueBuffer.clear() }
                        }
                        XmlPullParser.TEXT -> {
                            if (inV) valueBuffer.append(parser.text)
                        }
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "v", "t" -> inV = false
                            "c" -> {
                                val raw = valueBuffer.toString()
                                val colIdx = colRefToIndex(cellRef)
                                // Fill gaps with empty strings
                                while (currentRow.size < colIdx) currentRow.add("")
                                val cellValue = when (cellType) {
                                    "s" -> sst.getOrNull(raw.toIntOrNull() ?: -1) ?: raw
                                    "str", "inlineStr" -> raw
                                    "b" -> if (raw == "1") "TRUE" else "FALSE"
                                    else -> raw   // numeric / date stored as number
                                }
                                currentRow.add(cellValue)
                            }
                            "row" -> if (currentRow.isNotEmpty()) rows += currentRow.toList()
                        }
                    }
                    eventType = parser.next()
                }
                break
            }
            entry = zis.nextEntry
        }
        return rows
    }

    /** Convert column reference like "A", "B", "AA" to 0-based index. */
    private fun colRefToIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        return letters.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
    }
}

