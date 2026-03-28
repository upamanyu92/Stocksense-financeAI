package com.stocksense.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.data.model.SearchResult
import com.stocksense.app.data.model.SearchResultType
import com.stocksense.app.ui.theme.*

/**
 * Dropdown overlay showing up to 5 search results with type badges.
 * Shows a "View all results" link when there are more than 5 matches.
 */
@Composable
fun SearchDropdown(
    results: List<SearchResult>,
    totalCount: Int,
    isSearching: Boolean,
    onResultClick: (SearchResult) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty() && !isSearching) return

    Card(
        colors = CardDefaults.cardColors(containerColor = Graphite),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = ElectricBlue
                    )
                }
            } else {
                results.forEachIndexed { index, result ->
                    SearchDropdownItem(
                        result = result,
                        onClick = { onResultClick(result) }
                    )
                    if (index < results.lastIndex) {
                        HorizontalDivider(
                            color = GlassStroke,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                // "View all" link
                if (totalCount > 5) {
                    HorizontalDivider(
                        color = GlassStroke,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onViewAllClick),
                        color = GlassSurface,
                        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "View all $totalCount results",
                                color = ElectricBlue,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDropdownItem(result: SearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Type icon
        val icon = when (result.type) {
            SearchResultType.COMPANY -> Icons.Default.Business
            SearchResultType.STOCK_SYMBOL -> Icons.Default.TrendingUp
            SearchResultType.ETF -> Icons.Default.AccountBalance
            SearchResultType.INDEX -> Icons.Default.BarChart
            SearchResultType.OTHER -> Icons.Default.Category
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(GlassSurface, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
        }

        // Text info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.displayName,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${result.code} • ${result.matchSource}",
                fontSize = 11.sp,
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
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                result.type.label,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp,
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
