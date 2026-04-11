package com.stocksense.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.data.database.entities.PortfolioHolding
import com.stocksense.app.data.database.entities.TradeType
import com.stocksense.app.data.model.ImportedMFHolding
import com.stocksense.app.data.model.ImportedPortfolioSummary
import com.stocksense.app.data.model.ImportedStockHolding
import com.stocksense.app.ui.components.EmptyState
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.PortfolioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel,
    onStockClick: (String) -> Unit,
    onNavigateToCredence: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current
    val showTradeDialog = remember { mutableStateOf(false) }

    val xlsxPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFromXlsx(ctx, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Portfolio") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Graphite,
                    titleContentColor = ElectricBlue
                ),
                actions = {
                    // Import XLSX button
                    IconButton(onClick = { xlsxPicker.launch("*/*") }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Import Statement",
                            tint = NeonGreen)
                    }
                    // AI Analysis button
                    IconButton(onClick = { viewModel.analyzePortfolio() }) {
                        Icon(Icons.Default.Analytics, contentDescription = "AI Analysis",
                            tint = ElectricBlue)
                    }
                    // Credence AI credit scoring
                    if (onNavigateToCredence != null) {
                        IconButton(onClick = onNavigateToCredence) {
                            Icon(Icons.Default.CreditScore, contentDescription = "Credence AI",
                                tint = AuroraPurple)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showTradeDialog.value = true },
                    containerColor = NeonGreen, contentColor = DeepBlack
                ) { Icon(Icons.Default.Add, contentDescription = "Record Trade") }
            }
        },
        containerColor = DeepBlack
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // ── Tab Row ───────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = Graphite,
                contentColor = ElectricBlue
            ) {
                Tab(selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Holdings") })
                Tab(selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = {
                        val count = uiState.importedStocks.size + uiState.importedMFs.size
                        Text(if (count > 0) "Imported ($count)" else "Imported")
                    })
                Tab(selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    text = { Text("AI Analysis") })
            }

            // ── Import progress / error ───────────────────────────────────────
            if (uiState.isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonGreen
                )
            }
            uiState.importError?.let { err ->
                Surface(color = SoftRed.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
                    Text(err, color = SoftRed, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }

            // ── Tab content ───────────────────────────────────────────────────
            when (uiState.selectedTab) {
                0 -> HoldingsTab(uiState.holdings, uiState.isLoading,
                    uiState.totalValue, uiState.totalInvested, uiState.totalPnl,
                    uiState.totalPnlPercent, onStockClick)
                1 -> ImportedTab(
                    stocks = uiState.importedStocks,
                    stockSummary = uiState.importedStockSummary,
                    mfs = uiState.importedMFs,
                    mfSummary = uiState.importedSummary,
                    onImportClick = { xlsxPicker.launch("*/*") }
                )
                2 -> AnalysisTab(
                    analysisResult = uiState.analysisResult,
                    isAnalysing = uiState.isAnalysing,
                    onRunAnalysis = { viewModel.analyzePortfolio() }
                )
            }
        }
    }

    if (showTradeDialog.value) {
        RecordTradeDialog(
            onDismiss = { showTradeDialog.value = false },
            onConfirm = { symbol, tradeType, quantity, price ->
                viewModel.recordTrade(symbol, tradeType, quantity, price)
                showTradeDialog.value = false
            }
        )
    }
}

// ── Tab 0: Manual Holdings ────────────────────────────────────────────────────

@Composable
private fun HoldingsTab(
    holdings: List<PortfolioHolding>,
    isLoading: Boolean,
    totalValue: Double, totalInvested: Double, totalPnl: Double, totalPnlPercent: Double,
    onStockClick: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center), color = NeonGreen)
            holdings.isEmpty() -> EmptyState(
                message = "No holdings yet.\nTap + to record your first trade.",
                modifier = Modifier.align(Alignment.Center))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    PortfolioSummaryCard(totalValue, totalInvested, totalPnl, totalPnlPercent)
                }
                item {
                    Text("Holdings", style = MaterialTheme.typography.titleMedium,
                        color = ElectricBlue, modifier = Modifier.padding(top = 8.dp))
                }
                items(holdings, key = { it.id }) { holding ->
                    HoldingCard(holding = holding, onClick = { onStockClick(holding.symbol) })
                }
            }
        }
    }
}

// ── Tab 1: Imported Holdings ──────────────────────────────────────────────────

@Composable
private fun ImportedTab(
    stocks: List<ImportedStockHolding>,
    stockSummary: ImportedPortfolioSummary?,
    mfs: List<ImportedMFHolding>,
    mfSummary: ImportedPortfolioSummary?,
    onImportClick: () -> Unit
) {
    if (stocks.isEmpty() && mfs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("No statements imported yet", color = MutedGrey, fontSize = 16.sp)
                Text("Import your broker's Holdings Statement\n(Stocks or Mutual Fund XLSX)",
                    color = MutedGrey, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Button(onClick = onImportClick, colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)) {
                    Icon(Icons.Default.FileOpen, contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp))
                    Text("Import Statement")
                }
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Stocks section
        if (stocks.isNotEmpty()) {
            item {
                stockSummary?.let { ImportedSummaryCard(it, "Stock Holdings") }
            }
            item {
                Text("Stocks (${stocks.size})", style = MaterialTheme.typography.titleMedium,
                    color = ElectricBlue, modifier = Modifier.padding(top = 4.dp))
            }
            items(stocks, key = { it.isin }) { stock ->
                ImportedStockCard(stock)
            }
        }
        // MF section
        if (mfs.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                mfSummary?.let { ImportedSummaryCard(it, "Mutual Fund Holdings") }
            }
            // Group by category
            val byCategory = mfs.groupBy { it.category }
            byCategory.forEach { (category, items) ->
                item {
                    Text(category, style = MaterialTheme.typography.titleSmall,
                        color = LuxeGold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(items, key = { it.folioNo + it.schemeName }) { mf ->
                    ImportedMFCard(mf)
                }
            }
        }
        item {
            Button(
                onClick = onImportClick,
                colors = ButtonDefaults.buttonColors(containerColor = Graphite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Import Another Statement")
            }
        }
    }
}

@Composable
private fun ImportedSummaryCard(summary: ImportedPortfolioSummary, title: String) {
    val pnlColor = if (summary.pnl >= 0) NeonGreen else SoftRed
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Graphite)) {
        Box(modifier = Modifier.background(Brush.linearGradient(listOf(GlassSurface, ElectricBlue.copy(alpha = 0.15f)))).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = MutedGrey, fontSize = 12.sp)
                Text("Invested: ₹${"%.2f".format(summary.totalInvested)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Current Value", color = MutedGrey, fontSize = 11.sp)
                        Text("₹${"%.2f".format(summary.currentValue)}", fontWeight = FontWeight.Bold, color = Color(0xFFFFFFFF))
                    }
                    Column {
                        Text("P&L", color = MutedGrey, fontSize = 11.sp)
                        Text("₹${"%.2f".format(summary.pnl)} (${"%.2f".format(summary.pnlPercent)}%)",
                            fontWeight = FontWeight.SemiBold, color = pnlColor, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportedStockCard(stock: ImportedStockHolding) {
    val pnlColor = if (stock.unrealisedPnl >= 0) NeonGreen else SoftRed
    val pnlSign = if (stock.unrealisedPnl >= 0) "+" else ""
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Graphite)) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stock.stockName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${stock.quantity.toInt()} shares · Avg ₹${"%.2f".format(stock.avgBuyPrice)}",
                    color = MutedGrey, fontSize = 12.sp)
                Text("ISIN: ${stock.isin}", color = MutedGrey, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${"%.2f".format(stock.closingValue)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("$pnlSign₹${"%.2f".format(stock.unrealisedPnl)}",
                    color = pnlColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("$pnlSign${"%.2f".format(stock.pnlPercent)}%", color = pnlColor, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ImportedMFCard(mf: ImportedMFHolding) {
    val returnsColor = if (mf.returns >= 0) NeonGreen else SoftRed
    val sign = if (mf.returns >= 0) "+" else ""
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Graphite)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(mf.schemeName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${mf.amc} · ${mf.subCategory}", color = MutedGrey, fontSize = 11.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Invested", color = MutedGrey, fontSize = 11.sp)
                    Text("₹${"%.2f".format(mf.investedValue)}", fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Current", color = MutedGrey, fontSize = 11.sp)
                    Text("₹${"%.2f".format(mf.currentValue)}", fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Returns", color = MutedGrey, fontSize = 11.sp)
                    Text("$sign₹${"%.0f".format(mf.returns)}", color = returnsColor,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("XIRR: ${mf.xirr}", color = returnsColor, fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Tab 2: AI Analysis ────────────────────────────────────────────────────────

@Composable
private fun AnalysisTab(
    analysisResult: String,
    isAnalysing: Boolean,
    onRunAnalysis: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Header card
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Graphite)) {
            Box(modifier = Modifier.background(
                Brush.linearGradient(listOf(GlassSurface, ElectricBlue.copy(alpha = 0.2f))))
                .padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("QuantSense Agent Analysis", fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, color = ElectricBlue)
                    Text("AI agent evaluates your portfolio against current market sentiment " +
                        "and provides actionable recommendations.",
                        color = MutedGrey, fontSize = 12.sp)
                    Button(
                        onClick = onRunAnalysis,
                        enabled = !isAnalysing,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isAnalysing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                color = DeepBlack, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analysing…")
                        } else {
                            Icon(Icons.Default.Analytics, contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp))
                            Text("Run Portfolio Analysis")
                        }
                    }
                }
            }
        }

        // Analysis result
        if (analysisResult.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Graphite)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Render markdown-like text: bold lines with **text**
                    analysisResult.lines().forEach { line ->
                        if (line.startsWith("**") && line.endsWith("**")) {
                            Text(line.removeSurrounding("**"), fontWeight = FontWeight.Bold,
                                color = ElectricBlue, fontSize = 14.sp)
                        } else if (line.startsWith("•")) {
                            Text(line, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp,
                                modifier = Modifier.padding(start = 8.dp))
                        } else if (line.isBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                        } else {
                            Text(line, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun PortfolioSummaryCard(
    totalValue: Double, totalInvested: Double, totalPnl: Double, totalPnlPercent: Double
) {
    val pnlColor = if (totalPnl >= 0) NeonGreen else SoftRed
    val pnlPrefix = if (totalPnl >= 0) "+" else ""
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Graphite)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Portfolio Value", style = MaterialTheme.typography.labelMedium, color = MutedGrey)
            Text("₹${"%.2f".format(totalValue)}", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = ElectricBlue)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Invested", style = MaterialTheme.typography.labelSmall, color = MutedGrey)
                    Text("₹${"%.2f".format(totalInvested)}", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("P&L", style = MaterialTheme.typography.labelSmall, color = MutedGrey)
                    Text("$pnlPrefix₹${"%.2f".format(totalPnl)}",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = pnlColor)
                    Text("($pnlPrefix${"%.2f".format(totalPnlPercent)}%)",
                        style = MaterialTheme.typography.bodySmall, color = pnlColor)
                }
            }
        }
    }
}

@Composable
private fun HoldingCard(holding: PortfolioHolding, onClick: () -> Unit) {
    val pnlColor = if (holding.pnlPercent >= 0) NeonGreen else SoftRed
    val pnlPrefix = if (holding.pnlPercent >= 0) "+" else ""
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Graphite)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(holding.symbol, style = MaterialTheme.typography.titleMedium, color = ElectricBlue)
                Text("${"%.0f".format(holding.quantity)} shares · Avg ₹${"%.2f".format(holding.avgBuyPrice)}",
                    style = MaterialTheme.typography.bodySmall, color = MutedGrey)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${"%.2f".format(holding.currentValue)}", style = MaterialTheme.typography.titleSmall)
                Text("$pnlPrefix${"%.2f".format(holding.pnlPercent)}%",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = pnlColor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTradeDialog(onDismiss: () -> Unit, onConfirm: (String, TradeType, Double, Double) -> Unit) {
    var symbol by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var tradeType by remember { mutableStateOf(TradeType.BUY) }
    val validationError = remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Graphite,
        title = { Text("Record Trade", color = ElectricBlue) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = symbol, onValueChange = { symbol = it.uppercase() },
                    label = { Text("Symbol (e.g. TCS)") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen, cursorColor = NeonGreen))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tradeType == TradeType.BUY, onClick = { tradeType = TradeType.BUY },
                        label = { Text("BUY") }, colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f), selectedLabelColor = NeonGreen))
                    FilterChip(selected = tradeType == TradeType.SELL, onClick = { tradeType = TradeType.SELL },
                        label = { Text("SELL") }, colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SoftRed.copy(alpha = 0.2f), selectedLabelColor = SoftRed))
                }
                OutlinedTextField(value = quantity, onValueChange = { quantity = it; validationError.value = null },
                    label = { Text("Quantity") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen, cursorColor = NeonGreen))
                OutlinedTextField(value = price, onValueChange = { price = it; validationError.value = null },
                    label = { Text("Price per share (₹)") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen, cursorColor = NeonGreen))
                validationError.value?.let { Text(it, color = SoftRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    symbol.isBlank() -> validationError.value = "Symbol is required"
                    quantity.toDoubleOrNull() == null -> validationError.value = "Enter a valid quantity"
                    price.toDoubleOrNull() == null -> validationError.value = "Enter a valid price"
                    else -> onConfirm(symbol, tradeType, quantity.toDouble(), price.toDouble())
                }
            }) { Text("Submit", color = NeonGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MutedGrey) } }
    )
}
