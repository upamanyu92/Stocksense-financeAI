package com.stocksense.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stocksense.app.data.database.entities.WatchlistItem
import com.stocksense.app.data.model.StockData
import com.stocksense.app.ui.components.EmptyState
import com.stocksense.app.ui.components.SearchDropdown
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.SearchViewModel
import com.stocksense.app.viewmodel.WatchlistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    searchViewModel: SearchViewModel,
    onStockClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchState by searchViewModel.uiState.collectAsState()

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
        containerColor = DeepBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Inline search bar
            OutlinedTextField(
                value = searchState.query,
                onValueChange = { searchViewModel.updateQuery(it) },
                placeholder = { Text("Search company name or symbol…", color = MutedGrey) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MutedGrey) },
                trailingIcon = {
                    if (searchState.query.isNotBlank()) {
                        IconButton(onClick = { searchViewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = MutedGrey)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = GlassStroke,
                    cursorColor = NeonGreen,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // Search dropdown — tap a result to add it to watchlist
            if (searchState.query.isNotBlank() && (searchState.results.isNotEmpty() || searchState.isSearching)) {
                SearchDropdown(
                    results = searchState.results,
                    totalCount = searchState.totalCount,
                    isSearching = searchState.isSearching,
                    onResultClick = { result ->
                        viewModel.addToWatchlist(result.symbol)
                        searchViewModel.clearSearch()
                    },
                    onViewAllClick = { /* results are added inline */ },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Watchlist items
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = NeonGreen
                        )
                    }
                }
                uiState.watchlistItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EmptyState(
                            message = "Your watchlist is empty.\nSearch and tap a company to add.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
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
                                displayName = uiState.displayNames[item.symbol],
                                onClick = { onStockClick(item.symbol) },
                                onRemove = { viewModel.removeFromWatchlist(item.symbol) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistRow(
    item: WatchlistItem,
    stockData: StockData?,
    displayName: String?,
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
                    text = displayName ?: item.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricBlue
                )
                if (displayName != null || stockData != null) {
                    Text(
                        text = item.symbol,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey
                    )
                }
            }

            if (stockData != null && (stockData.currentPrice != 0.0 || stockData.previousClose != 0.0)) {
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
                    text = "Syncing…",
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
