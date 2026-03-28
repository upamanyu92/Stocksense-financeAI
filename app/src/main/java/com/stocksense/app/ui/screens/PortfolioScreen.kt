package com.stocksense.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stocksense.app.data.database.entities.PortfolioHolding
import com.stocksense.app.data.database.entities.TradeType
import com.stocksense.app.ui.components.EmptyState
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.PortfolioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel,
    onStockClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTradeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Portfolio") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Graphite,
                    titleContentColor = ElectricBlue
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTradeDialog = true },
                containerColor = NeonGreen,
                contentColor = DeepBlack
            ) {
                Icon(Icons.Default.Add, contentDescription = "Record Trade")
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
                uiState.holdings.isEmpty() -> {
                    EmptyState(
                        message = "No holdings yet.\nTap + to record your first trade.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            PortfolioSummaryCard(
                                totalValue = uiState.totalValue,
                                totalInvested = uiState.totalInvested,
                                totalPnl = uiState.totalPnl,
                                totalPnlPercent = uiState.totalPnlPercent
                            )
                        }
                        item {
                            Text(
                                text = "Holdings",
                                style = MaterialTheme.typography.titleMedium,
                                color = ElectricBlue,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(uiState.holdings, key = { it.id }) { holding ->
                            HoldingCard(
                                holding = holding,
                                onClick = { onStockClick(holding.symbol) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTradeDialog) {
        RecordTradeDialog(
            onDismiss = { showTradeDialog = false },
            onConfirm = { symbol, tradeType, quantity, price ->
                viewModel.recordTrade(symbol, tradeType, quantity, price)
                showTradeDialog = false
            }
        )
    }
}

@Composable
private fun PortfolioSummaryCard(
    totalValue: Double,
    totalInvested: Double,
    totalPnl: Double,
    totalPnlPercent: Double
) {
    val pnlColor = if (totalPnl >= 0) NeonGreen else SoftRed
    val pnlPrefix = if (totalPnl >= 0) "+" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Graphite)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Portfolio Value",
                style = MaterialTheme.typography.labelMedium,
                color = MutedGrey
            )
            Text(
                text = "₹${"%.2f".format(totalValue)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ElectricBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Invested", style = MaterialTheme.typography.labelSmall, color = MutedGrey)
                    Text(
                        text = "₹${"%.2f".format(totalInvested)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("P&L", style = MaterialTheme.typography.labelSmall, color = MutedGrey)
                    Text(
                        text = "$pnlPrefix₹${"%.2f".format(totalPnl)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = pnlColor
                    )
                    Text(
                        text = "($pnlPrefix${"%.2f".format(totalPnlPercent)}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = pnlColor
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingCard(
    holding: PortfolioHolding,
    onClick: () -> Unit
) {
    val pnlColor = if (holding.pnlPercent >= 0) NeonGreen else SoftRed
    val pnlPrefix = if (holding.pnlPercent >= 0) "+" else ""

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
                    text = holding.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricBlue
                )
                Text(
                    text = "${"%.0f".format(holding.quantity)} shares · Avg ₹${"%.2f".format(holding.avgBuyPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGrey
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${"%.2f".format(holding.currentValue)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$pnlPrefix${"%.2f".format(holding.pnlPercent)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = pnlColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTradeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, TradeType, Double, Double) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var tradeType by remember { mutableStateOf(TradeType.BUY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Graphite,
        title = { Text("Record Trade", color = ElectricBlue) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase() },
                    label = { Text("Symbol (e.g. TCS)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        cursorColor = NeonGreen
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tradeType == TradeType.BUY,
                        onClick = { tradeType = TradeType.BUY },
                        label = { Text("BUY") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen
                        )
                    )
                    FilterChip(
                        selected = tradeType == TradeType.SELL,
                        onClick = { tradeType = TradeType.SELL },
                        label = { Text("SELL") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SoftRed.copy(alpha = 0.2f),
                            selectedLabelColor = SoftRed
                        )
                    )
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        cursorColor = NeonGreen
                    )
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price per share (₹)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        cursorColor = NeonGreen
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val qty = quantity.toDoubleOrNull() ?: return@TextButton
                    val prc = price.toDoubleOrNull() ?: return@TextButton
                    if (symbol.isNotBlank()) onConfirm(symbol, tradeType, qty, prc)
                }
            ) { Text("Submit", color = NeonGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MutedGrey) }
        }
    )
}
