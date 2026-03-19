package com.stocksense.app.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.stocksense.app.data.database.dao.AlertDao
import com.stocksense.app.data.database.entities.Alert
import com.stocksense.app.data.database.entities.AlertStatus
import com.stocksense.app.data.database.entities.AlertType
import com.stocksense.app.data.model.StockData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AlertManager"
private const val CHANNEL_ID = "stock_alerts"
private const val NOTIFICATION_BASE_ID = 1000

/**
 * AlertManager – evaluates user-defined alert rules against current stock prices
 * and fires Android notifications when thresholds are crossed.
 */
class AlertManager(
    private val context: Context,
    private val alertDao: AlertDao
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    // ---------- Public API ----------

    /**
     * Evaluate all active alerts against [currentStocks].
     * Triggers notifications and marks alerts as TRIGGERED in the database.
     */
    suspend fun evaluate(currentStocks: List<StockData>) = withContext(Dispatchers.IO) {
        val activeAlerts = alertDao.getActiveAlerts()
        val stockMap = currentStocks.associateBy { it.symbol }

        for (alert in activeAlerts) {
            val stock = stockMap[alert.symbol] ?: continue
            if (shouldTrigger(alert, stock)) {
                triggerAlert(alert, stock)
            }
        }
    }

    /** Add a new alert rule. */
    suspend fun addAlert(alert: Alert): Long = withContext(Dispatchers.IO) {
        alertDao.insertAlert(alert)
    }

    /** Dismiss an alert (won't fire again unless re-created). */
    suspend fun dismissAlert(alertId: Long) = withContext(Dispatchers.IO) {
        alertDao.updateAlertStatus(alertId, AlertStatus.DISMISSED, null)
    }

    /** Delete an alert permanently. */
    suspend fun deleteAlert(alertId: Long) = withContext(Dispatchers.IO) {
        alertDao.deleteById(alertId)
    }

    // ---------- Private ----------

    private fun shouldTrigger(alert: Alert, stock: StockData): Boolean = when (alert.type) {
        AlertType.PRICE_ABOVE -> stock.currentPrice >= alert.threshold
        AlertType.PRICE_BELOW -> stock.currentPrice <= alert.threshold
        AlertType.CHANGE_PERCENT -> Math.abs(stock.changePercent) >= alert.threshold
        AlertType.PREDICTION_SIGNAL -> false  // handled separately by PredictionWorker
    }

    private suspend fun triggerAlert(alert: Alert, stock: StockData) {
        val now = System.currentTimeMillis()
        alertDao.updateAlertStatus(alert.id, AlertStatus.TRIGGERED, now)

        val title = "${stock.symbol} Alert"
        val body = when (alert.type) {
            AlertType.PRICE_ABOVE ->
                "${stock.symbol} price ${"%.2f".format(stock.currentPrice)} crossed above ${"%.2f".format(alert.threshold)}"
            AlertType.PRICE_BELOW ->
                "${stock.symbol} price ${"%.2f".format(stock.currentPrice)} dropped below ${"%.2f".format(alert.threshold)}"
            AlertType.CHANGE_PERCENT ->
                "${stock.symbol} changed ${"%.1f".format(stock.changePercent)}% (threshold: ${"%.1f".format(alert.threshold)}%)"
            AlertType.PREDICTION_SIGNAL -> alert.message
        }

        showNotification(alert.id.toInt() + NOTIFICATION_BASE_ID, title, body)
        Log.i(TAG, "Alert triggered: $body")
    }

    fun showPredictionNotification(symbol: String, direction: String, confidence: Float) {
        val title = "$symbol Prediction"
        val body = "Model predicts $direction with ${"%.0f".format(confidence * 100)}% confidence"
        showNotification(symbol.hashCode(), title, body)
    }

    private fun showNotification(id: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Stock Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for stock price alerts and predictions"
        }
        notificationManager.createNotificationChannel(channel)
    }
}
