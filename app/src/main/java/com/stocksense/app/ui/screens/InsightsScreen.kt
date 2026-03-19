package com.stocksense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stocksense.app.engine.QualityMode
import com.stocksense.app.viewmodel.InsightsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    symbol: String,
    viewModel: InsightsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$symbol – AI Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quality Mode selector
            QualityModeSelector(
                current = uiState.qualityMode,
                onModeSelected = { viewModel.setQualityMode(it) }
            )

            // Insight text card
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    when {
                        uiState.isLoading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Generating AI insight…",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        uiState.error != null -> {
                            Text(
                                text = "Error: ${uiState.error}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        uiState.insight.isNotEmpty() -> {
                            Column {
                                Text(
                                    "AI Analysis",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = uiState.insight,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Model: ${uiState.qualityMode.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        else -> {
                            Text(
                                "Run a prediction first to generate AI insights.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            if (!uiState.isLoading && uiState.insight.isNotEmpty()) {
                Button(
                    onClick = { /* User would need to go back to prediction and regenerate */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go Back to Prediction")
                }
            }
        }
    }
}

@Composable
private fun QualityModeSelector(
    current: QualityMode,
    onModeSelected: (QualityMode) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("LLM Quality Mode", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == current,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
        }
    }
}

private val QualityMode.label: String
    get() = when (this) {
        QualityMode.LITE -> "Lite"
        QualityMode.BALANCED -> "Balanced"
        QualityMode.PRO -> "Pro"
    }
