package com.stocksense.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.R
import com.stocksense.app.data.model.*
import com.stocksense.app.ui.components.SearchDropdown
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.DashboardViewModel
import com.stocksense.app.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    searchViewModel: SearchViewModel,
    onStockClick: (String) -> Unit,
    onProfileClick: () -> Unit = {},
    onViewAllSearchResults: (String) -> Unit = {},
    onNavigateToCredence: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchState by searchViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "SenseQuant Logo",
                            modifier = Modifier.size(28.dp)
                        )
                        Text("SenseQuant", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { /* notifications */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = MutedGrey)
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepBlack,
                    titleContentColor = Color.White,
                    actionIconContentColor = MutedGrey
                )
            )
        },
        containerColor = DeepBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBlack)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderBar(
                searchQuery = searchState.query,
                onSearchQueryChange = { query ->
                    searchViewModel.updateQuery(query)
                    viewModel.updateSearchQuery(query)
                },
                onClearSearch = {
                    searchViewModel.clearSearch()
                    viewModel.updateSearchQuery("")
                },
                onProfileClick = onProfileClick
            )

            // Search dropdown overlay
            if (searchState.query.isNotBlank() && (searchState.results.isNotEmpty() || searchState.isSearching)) {
                SearchDropdown(
                    results = searchState.results,
                    totalCount = searchState.totalCount,
                    isSearching = searchState.isSearching,
                    onResultClick = { result -> onStockClick(result.symbol) },
                    onViewAllClick = { onViewAllSearchResults(searchState.query) }
                )
            }

            uiState.portfolio?.let {
                HeroPortfolioCard(snapshot = it)
            }

            SentimentAndDebate(
                sentiment = uiState.sentiment,
                debate = uiState.debate
            )

            PredictionsSection(uiState.predictions)

            // Quick access — Credence AI credit scoring
            CredenceAICard(onClick = onNavigateToCredence)

            WatchlistSection(
                title = if (uiState.searchQuery.isBlank()) "Watchlist" else "Search Results",
                stocks = uiState.filteredStocks,
                onStockClick = onStockClick
            )

            IndicatorTranslationSection(uiState.indicatorTranslations)

            KillCriteriaSection(uiState.killNotices)

            WeightingRulesSection(uiState.weightingRules, uiState.stopWords)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onProfileClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("SenseQuant Command Center", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            IconButton(onClick = onProfileClick) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = MutedGrey
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search TCS, RELIANCE, NIFTY…", color = MutedGrey) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MutedGrey) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = MutedGrey)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GlassSurface,
                unfocusedContainerColor = GlassSurface,
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = ElectricBlue
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HeroPortfolioCard(snapshot: PortfolioSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Graphite),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(GlassSurface, ElectricBlue.copy(alpha = 0.25f))
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Portfolio", color = MutedGrey, fontSize = 13.sp)
                Text(
                    text = "₹${"%,.0f".format(snapshot.totalValue)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PnlPill(value = snapshot.dailyPnl, percent = snapshot.dailyPercent)
                    LevelPill(level = snapshot.level, streak = snapshot.streakDays)
                }
            }
        }
    }
}

@Composable
private fun PnlPill(value: Double, percent: Double) {
    val positive = value >= 0
    val color = if (positive) NeonGreen else SoftRed
    Surface(
        color = GlassSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Daily P&L", color = MutedGrey, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "₹${"%,.0f".format(value)}",
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
                Text("${"%.2f".format(percent)}%", color = color, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LevelPill(level: String, streak: Int) {
    Surface(
        color = LuxeGold.copy(alpha = 0.15f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Level", color = LuxeGold, fontSize = 12.sp)
            Text(level, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Streak: $streak days", color = MutedGrey, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SentimentAndDebate(sentiment: SentimentSummary?, debate: List<DebateMessage>) {
    if (sentiment == null) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SentimentCard(sentiment)
        DebateThread(debate)
    }
}

@Composable
private fun SentimentCard(summary: SentimentSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Graphite),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Sentiment Summary", color = MutedGrey, fontSize = 13.sp)
                Text(summary.headline, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(summary.detail, color = MutedGrey, fontSize = 13.sp)
            }
            SentimentGauge(summary)
        }
    }
}

@Composable
private fun SentimentGauge(summary: SentimentSummary) {
    val gaugeColor = when (summary.stance) {
        SentimentStance.BULLISH -> NeonGreen
        SentimentStance.BEARISH -> SoftRed
        SentimentStance.NEUTRAL -> ElectricBlue
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(GlassSurface, shape = RoundedCornerShape(48.dp)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { summary.score / 100f },
            color = gaugeColor,
            strokeWidth = 8.dp,
            trackColor = NightGlare,
            modifier = Modifier.fillMaxSize()
        )
        Text("${summary.score}%", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DebateThread(debate: List<DebateMessage>) {
    if (debate.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Graphite, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Agent Debate", fontWeight = FontWeight.SemiBold)
        debate.forEach { message ->
            val color = when (message.speaker) {
                DebateRole.BULL -> NeonGreen
                DebateRole.BEAR -> SoftRed
                DebateRole.SKEPTIC -> ElectricBlue
            }
            Surface(
                color = GlassSurface,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (message.speaker) {
                                DebateRole.BULL -> "🐂"
                                DebateRole.BEAR -> "🐻"
                                DebateRole.SKEPTIC -> "🔍"
                            },
                            fontSize = 16.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(message.claim, color = Color.White, fontSize = 14.sp)
                        Text(
                            "${message.evidenceGrade.label} • Verified by ${message.evidenceGrade.sources} sources",
                            color = MutedGrey,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictionsSection(predictions: List<AiPredictionCard>) {
    if (predictions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("AI Predictions")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(predictions) { prediction ->
                PredictionCard(prediction)
            }
        }
    }
}

@Composable
private fun PredictionCard(prediction: AiPredictionCard) {
    val color = when (prediction.movement) {
        Movement.UP -> NeonGreen
        Movement.DOWN -> SoftRed
        Movement.NEUTRAL -> ElectricBlue
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Graphite),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(220.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(prediction.symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(prediction.name, color = MutedGrey, fontSize = 12.sp)
                }
                Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = color)
            }
            LinearProgressIndicator(
                progress = { prediction.confidence / 100f },
                color = color,
                trackColor = NightGlare
            )
            Text("Confidence ${prediction.confidence}%", color = color, fontSize = 12.sp)
            Text(prediction.rationale, color = MutedGrey, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WatchlistSection(
    title: String,
    stocks: List<StockData>,
    onStockClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title)
        if (stocks.isEmpty()) {
            Text("No stocks found. Seed data will appear on first launch.", color = MutedGrey)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stocks, key = { it.symbol }) { stock ->
                    WatchlistCard(stock, onStockClick)
                }
            }
        }
    }
}

@Composable
private fun WatchlistCard(stock: StockData, onStockClick: (String) -> Unit) {
    val changeColor = if (stock.changePercent >= 0) NeonGreen else SoftRed
    Card(
        onClick = { onStockClick(stock.symbol) },
        colors = CardDefaults.cardColors(containerColor = Graphite),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(180.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stock.symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(stock.name, color = MutedGrey, fontSize = 12.sp, maxLines = 1)
            Text("₹${"%.2f".format(stock.currentPrice)}", fontWeight = FontWeight.SemiBold)
            Text(
                text = "${if (stock.changePercent >= 0) "+" else ""}${"%.2f".format(stock.changePercent)}%",
                color = changeColor,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun IndicatorTranslationSection(translations: List<IndicatorTranslation>) {
    if (translations.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Plain English Indicators")
        translations.forEach { item ->
            Surface(
                color = GlassSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.key, fontWeight = FontWeight.SemiBold)
                    Text(item.plainEnglish, color = Color.White, fontSize = 13.sp)
                    Text("Why it matters: ${item.whyItMatters}", color = MutedGrey, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun KillCriteriaSection(notices: List<KillCriteriaNotice>) {
    if (notices.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Kill Criteria")
        notices.forEach { notice ->
            Surface(
                color = Graphite,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(notice.title, fontWeight = FontWeight.Bold)
                    Text(notice.body, color = Color.White)
                    Text(notice.trustSignal, color = LuxeGold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WeightingRulesSection(rules: List<WeightingRule>, stopWords: List<String>) {
    if (rules.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Sentiment Weighting & Filters")
        rules.forEach { rule ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(rule.source, color = Color.White)
                Text("${rule.weight}x", color = NeonGreen, fontWeight = FontWeight.SemiBold)
            }
        }
        if (stopWords.isNotEmpty()) {
            Text("Context stop words: ${stopWords.joinToString()}", color = MutedGrey, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CredenceAICard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Graphite),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(AuroraPurple.copy(alpha = 0.12f), GlassSurface)
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Default.CreditScore,
                contentDescription = null,
                tint = AuroraPurple,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Credence AI",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AuroraPurple
                )
                Text(
                    "Tatva Ank Credit Scoring · Run AI-powered credit analysis",
                    color = MutedGrey,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Default.ArrowOutward,
                contentDescription = null,
                tint = AuroraPurple.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
}
