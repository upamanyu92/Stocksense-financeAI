package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity that persists a completed CredenceAI Tatva Ank analysis.
 *
 * Scalar columns are denormalised for query efficiency (e.g., list/sort by score or risk).
 * The full [TatvaAnkReport] is stored as a JSON blob in [reportJson] using
 * `kotlinx-serialization-json`, so no extra migration is needed when report
 * fields are added (blob is schema-stable).
 */
@Entity(
    tableName = "credence_analyses",
    indices = [
        Index("companyName"),
        Index("createdAt")
    ]
)
data class CredenceAnalysis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Company name (denormalised for query/sort). */
    val companyName: String,

    /** Industry classification (denormalised). */
    val industry: String,

    /** Sector classification (denormalised). */
    val sector: String,

    /** Tatva Ank score 0–100 (denormalised for filtering). */
    val tatvaAnkScore: Double,

    /** Altman Z-Score (denormalised for filtering). */
    val altmanZScore: Double,

    /** Sentiment score -1.0 to +1.0 (denormalised). */
    val sentimentScore: Double,

    /** "LOW", "MEDIUM", "HIGH" — stored as String for SQL predicates. */
    val riskLabel: String,

    /** Total pipeline processing time in milliseconds. */
    val processingTimeMs: Long,

    /** Full serialised [TatvaAnkReport] JSON. */
    val reportJson: String,

    /** Unix timestamp (ms) when the analysis completed. */
    val createdAt: Long = System.currentTimeMillis()
)

