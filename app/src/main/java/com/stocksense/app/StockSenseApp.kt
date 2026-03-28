package com.stocksense.app

import android.app.Application
import android.util.Log
import com.stocksense.app.alerts.AlertManager
import com.stocksense.app.data.database.AppDatabase
import com.stocksense.app.data.database.dao.AlertDao
import com.stocksense.app.data.database.dao.PredictionDao
import com.stocksense.app.data.repository.StockRepository
import com.stocksense.app.engine.BitNetModelDownloader
import com.stocksense.app.engine.LearningEngine
import com.stocksense.app.engine.ModelManager
import com.stocksense.app.engine.PredictionEngine
import com.stocksense.app.ingestion.DataIngestion
import com.stocksense.app.preferences.UserPreferencesManager
import com.stocksense.app.workers.DataSyncWorker
import com.stocksense.app.workers.LearningWorker
import com.stocksense.app.workers.ModelDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "StockSenseApp"

/**
 * Application class – single source of truth for app-wide singletons.
 *
 * Using manual dependency injection to keep the footprint small
 * (no Hilt/Dagger required, though they could be added).
 */
class StockSenseApp : Application() {

    // Coroutine scope tied to application lifetime
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Database
    private val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    // DAOs
    val predictionDao: PredictionDao by lazy { database.predictionDao() }
    val alertDao: AlertDao by lazy { database.alertDao() }

    // Repository
    val stockRepository: StockRepository by lazy {
        StockRepository(database.stockDao(), database.stockHistoryDao())
    }

    // Engines
    val modelManager: ModelManager by lazy { ModelManager(this) }
    val predictionEngine: PredictionEngine get() = modelManager.predictionEngine
    val learningEngine: LearningEngine by lazy {
        LearningEngine(database.predictionDao(), database.learningDataDao())
    }

    // BitNet model downloader
    val bitNetDownloader: BitNetModelDownloader by lazy { BitNetModelDownloader(this) }

    // Alerts
    val alertManager: AlertManager by lazy { AlertManager(this, database.alertDao()) }

    // Data ingestion
    val dataIngestion: DataIngestion by lazy { DataIngestion(this, stockRepository) }

    // User preferences
    val userPreferencesManager: UserPreferencesManager by lazy { UserPreferencesManager(this) }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "StockSenseApp starting up")

        // Seed database on first launch (non-blocking)
        appScope.launch {
            try {
                dataIngestion.seedIfEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "Seed error: ${e.message}")
            }
        }

        // Schedule background workers
        DataSyncWorker.schedule(this)
        LearningWorker.schedule(this)

        // Auto-download Microsoft BitNet 1-bit LLM model on first launch.
        // Uses WorkManager so download runs only when network is available
        // and survives process death.
        ModelDownloadWorker.schedule(this)

        Log.i(TAG, "Background workers scheduled (including BitNet model download)")
    }
}
