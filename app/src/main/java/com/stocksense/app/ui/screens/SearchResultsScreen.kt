package com.stocksense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.data.model.SearchResult
import com.stocksense.app.data.model.SearchResultType
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    query: String,
    viewModel: SearchViewModel,
    onResultClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(query) {
        viewModel.loadFullResults(query)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Results") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                .padding(horizontal = 16.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.updateQuery(it) },
                placeholder = { Text("Search companies, symbols…", color = MutedGrey) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MutedGrey) },
                trailingIcon = {
                    if (uiState.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MutedGrey)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GlassSurface,
                    unfocusedContainerColor = GlassSurface,
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = GlassStroke,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = ElectricBlue
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            if (uiState.isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ElectricBlue)
                }
            } else if (uiState.results.isEmpty() && uiState.query.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MutedGrey,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No results found", color = MutedGrey, fontSize = 16.sp)
                    }
                }
            } else {
                Text(
                    "${uiState.results.size} results",
                    color = MutedGrey,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.results) { result ->
                        SearchResultRow(result = result, onClick = { onResultClick(result.symbol) })
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Graphite,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Type icon
            val icon = when (result.type) {
                SearchResultType.COMPANY -> Icons.Default.Business
                SearchResultType.STOCK_SYMBOL -> Icons.AutoMirrored.Filled.TrendingUp
                SearchResultType.ETF -> Icons.Default.AccountBalance
                SearchResultType.INDEX -> Icons.Default.BarChart
                SearchResultType.OTHER -> Icons.Default.Category
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(GlassSurface, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.displayName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${result.code} • ${result.matchSource}",
                    fontSize = 12.sp,
                    color = MutedGrey
                )
            }

            // Type badge
            Surface(
                color = when (result.type) {
                    SearchResultType.COMPANY -> ElectricBlue.copy(alpha = 0.15f)
                    SearchResultType.STOCK_SYMBOL -> NeonGreen.copy(alpha = 0.15f)
                    SearchResultType.ETF -> AuroraPurple.copy(alpha = 0.15f)
                    SearchResultType.INDEX -> LuxeGold.copy(alpha = 0.15f)
                    SearchResultType.OTHER -> MutedGrey.copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    result.type.label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (result.type) {
                        SearchResultType.COMPANY -> ElectricBlue
                        SearchResultType.STOCK_SYMBOL -> NeonGreen
                        SearchResultType.ETF -> AuroraPurple
                        SearchResultType.INDEX -> LuxeGold
                        SearchResultType.OTHER -> MutedGrey
                    }
                )
            }
        }
    }
}
