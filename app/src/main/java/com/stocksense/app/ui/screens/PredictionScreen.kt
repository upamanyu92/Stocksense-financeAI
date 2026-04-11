package com.stocksense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stocksense.app.data.model.PredictionResult
import com.stocksense.app.ui.components.StockChart
import com.stocksense.app.ui.theme.Green400
import com.stocksense.app.ui.theme.Red400
import com.stocksense.app.viewmodel.PredictionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionScreen(
    symbol: String,
    viewModel: PredictionViewModel,
    onShowInsights: (PredictionResult) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(symbol) {
        viewModel.loadStock(symbol)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${uiState.displayName.ifBlank { symbol }} Prediction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Chart
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Price History (60 days)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    StockChart(history = uiState.history)
                }
            }

            // Prediction result
            uiState.prediction?.let { prediction ->
                PredictionCard(prediction = prediction)
            }

            // Error
            uiState.error?.let { error ->
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            uiState.warning?.let { warning ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = warning,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val activeSymbol = uiState.symbol.ifBlank { symbol }
                Button(
                    onClick = { viewModel.runPrediction(activeSymbol) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Run Prediction")
                    }
                }

                uiState.prediction?.let { prediction ->
                    OutlinedButton(
                        onClick = { onShowInsights(prediction) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Insights")
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictionCard(prediction: PredictionResult) {
    val directionColor = when (prediction.direction) {
        "UP" -> Green400
        "DOWN" -> Red400
        else -> MaterialTheme.colorScheme.onSurface
    }
    val directionEmoji = when (prediction.direction) {
        "UP" -> "📈"
        "DOWN" -> "📉"
        else -> "➡️"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Prediction Result", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$directionEmoji ${prediction.direction}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = directionColor
                )
            }
            Text(
                text = "Target: ${"%.2f".format(prediction.predictedPrice)}",
                style = MaterialTheme.typography.bodyLarge
            )
            LinearProgressIndicator(
                progress = { prediction.confidence },
                modifier = Modifier.fillMaxWidth(),
                color = directionColor
            )
            Text(
                text = "Confidence: ${"%.0f".format(prediction.confidence * 100)}%",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
