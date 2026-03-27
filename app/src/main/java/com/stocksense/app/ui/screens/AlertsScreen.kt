package com.stocksense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stocksense.app.data.database.entities.Alert
import com.stocksense.app.data.database.entities.AlertStatus
import com.stocksense.app.data.database.entities.AlertType
import com.stocksense.app.ui.components.EmptyState
import com.stocksense.app.viewmodel.AlertsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(viewModel: AlertsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Alerts") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Alert")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.alerts.isEmpty() -> {
                    EmptyState(
                        message = "No alerts set.\nTap + to add one.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.alerts, key = { it.id }) { alert ->
                            AlertCard(
                                alert = alert,
                                onDismiss = { viewModel.dismissAlert(alert.id) },
                                onDelete = { viewModel.deleteAlert(alert.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAlertDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { symbol, threshold, type ->
                if (type == AlertType.PRICE_ABOVE) {
                    viewModel.addPriceAboveAlert(symbol, threshold)
                } else {
                    viewModel.addPriceBelowAlert(symbol, threshold)
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AlertCard(
    alert: Alert,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = when (alert.status) {
        AlertStatus.TRIGGERED -> MaterialTheme.colorScheme.errorContainer
        AlertStatus.DISMISSED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        AlertStatus.ACTIVE -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${alert.symbol} – ${alert.type.displayName}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Threshold: ${"%.2f".format(alert.threshold)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Status: ${alert.status.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (alert.status == AlertStatus.TRIGGERED) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete alert")
            }
        }
    }
}

@Composable
private fun AddAlertDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, AlertType) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf("") }
    var alertType by remember { mutableStateOf(AlertType.PRICE_ABOVE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase() },
                    label = { Text("Symbol (e.g. AAPL)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text("Price threshold") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = alertType == AlertType.PRICE_ABOVE,
                        onClick = { alertType = AlertType.PRICE_ABOVE },
                        label = { Text("Above") }
                    )
                    FilterChip(
                        selected = alertType == AlertType.PRICE_BELOW,
                        onClick = { alertType = AlertType.PRICE_BELOW },
                        label = { Text("Below") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = threshold.toDoubleOrNull() ?: return@TextButton
                    if (symbol.isNotBlank()) onConfirm(symbol, t, alertType)
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private val AlertType.displayName: String
    get() = when (this) {
        AlertType.PRICE_ABOVE -> "Price Above"
        AlertType.PRICE_BELOW -> "Price Below"
        AlertType.CHANGE_PERCENT -> "Change %"
        AlertType.PREDICTION_SIGNAL -> "Prediction Signal"
    }
