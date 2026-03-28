package com.stocksense.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stocksense.app.data.database.entities.WatchlistItem
import com.stocksense.app.data.model.StockData
import com.stocksense.app.ui.components.EmptyState
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.WatchlistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onStockClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watchlist") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Graphite,
                    titleContentColor = ElectricBlue
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = NeonGreen,
                contentColor = DeepBlack
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add to Watchlist")
            }
        },
        containerColor = DeepBlack
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = NeonGreen
                    )
                }
                uiState.watchlistItems.isEmpty() -> {
                    EmptyState(
                        message = "Your watchlist is empty.\nTap + to add stocks.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.watchlistItems, key = { it.id }) { item ->
                            WatchlistRow(
                                item = item,
                                stockData = uiState.stocks[item.symbol],
                                onClick = { onStockClick(item.symbol) },
                                onRemove = { viewModel.removeFromWatchlist(item.symbol) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddToWatchlistDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { symbol ->
                viewModel.addToWatchlist(symbol)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun WatchlistRow(
    item: WatchlistItem,
    stockData: StockData?,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Graphite)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricBlue
                )
                if (stockData != null) {
                    Text(
                        text = stockData.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey
                    )
                }
            }

            if (stockData != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${"%.2f".format(stockData.currentPrice)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val changeColor = if (stockData.changePercent >= 0) NeonGreen else SoftRed
                    val changePrefix = if (stockData.changePercent >= 0) "+" else ""
                    Text(
                        text = "$changePrefix${"%.2f".format(stockData.changePercent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = changeColor
                    )
                }
            } else {
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGrey
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove from watchlist",
                    tint = SoftRed
                )
            }
        }
    }
}

@Composable
private fun AddToWatchlistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var symbol by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Graphite,
        title = { Text("Add to Watchlist", color = ElectricBlue) },
        text = {
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it.uppercase() },
                label = { Text("Stock Symbol (e.g. RELIANCE)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    cursorColor = NeonGreen
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (symbol.isNotBlank()) onConfirm(symbol) }
            ) { Text("Add", color = NeonGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MutedGrey) }
        }
    )
}
