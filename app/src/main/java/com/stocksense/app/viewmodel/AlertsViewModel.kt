package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.alerts.AlertManager
import com.stocksense.app.data.database.dao.AlertDao
import com.stocksense.app.data.database.entities.Alert
import com.stocksense.app.data.database.entities.AlertStatus
import com.stocksense.app.data.database.entities.AlertType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AlertsUiState(
    val alerts: List<Alert> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class AlertsViewModel(
    private val alertDao: AlertDao,
    private val alertManager: AlertManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            alertDao.getAllAlerts()
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { alerts ->
                    _uiState.update { it.copy(alerts = alerts, isLoading = false) }
                }
        }
    }

    fun addPriceAboveAlert(symbol: String, threshold: Double) {
        viewModelScope.launch {
            alertManager.addAlert(
                Alert(
                    symbol = symbol,
                    type = AlertType.PRICE_ABOVE,
                    threshold = threshold,
                    message = "$symbol crosses above $$threshold"
                )
            )
        }
    }

    fun addPriceBelowAlert(symbol: String, threshold: Double) {
        viewModelScope.launch {
            alertManager.addAlert(
                Alert(
                    symbol = symbol,
                    type = AlertType.PRICE_BELOW,
                    threshold = threshold,
                    message = "$symbol drops below $$threshold"
                )
            )
        }
    }

    fun dismissAlert(alertId: Long) {
        viewModelScope.launch {
            alertManager.dismissAlert(alertId)
        }
    }

    fun deleteAlert(alertId: Long) {
        viewModelScope.launch {
            alertManager.deleteAlert(alertId)
        }
    }
}
