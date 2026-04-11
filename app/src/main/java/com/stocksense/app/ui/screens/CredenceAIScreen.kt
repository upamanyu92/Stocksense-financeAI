package com.stocksense.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.data.model.credence.*
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.engine.QualityMode
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.CredenceAIViewModel
import com.stocksense.app.viewmodel.CredenceFormState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

private val dateFormat = SimpleDateFormat("dd MMM yy, HH:mm", Locale.getDefault())

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredenceAIScreen(
    viewModel: CredenceAIViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("New Analysis", "Results", "Evaluation", "History")

    // Show error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Error is shown inline; clear after display
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Credence AI", fontWeight = FontWeight.Bold, color = ElectricBlue)
                        Text("Tatva Ank Credit Scoring",
                            fontSize = 11.sp, color = MutedGrey, lineHeight = 13.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ElectricBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Graphite)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DeepBlack)
        ) {
            // ── Tab Row ──────────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor   = Graphite,
                contentColor     = ElectricBlue,
                edgePadding      = 0.dp,
                divider          = { HorizontalDivider(color = GlassStroke) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick  = { viewModel.selectTab(index) },
                        text     = {
                            Text(
                                title,
                                fontSize   = 13.sp,
                                fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color      = if (uiState.selectedTab == index) ElectricBlue else MutedGrey
                            )
                        }
                    )
                }
            }

            // ── Error Banner ─────────────────────────────────────────────
            AnimatedVisibility(visible = uiState.error != null) {
                uiState.error?.let { err ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SoftRed.copy(alpha = 0.15f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(err, color = SoftRed, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, "Dismiss", tint = SoftRed)
                        }
                    }
                }
            }

            // ── Tab Content ──────────────────────────────────────────────
            when (uiState.selectedTab) {
                0 -> NewAnalysisTab(uiState.form, uiState.isLoading, uiState.llmStatus, uiState.llmQualityMode, viewModel)
                1 -> ResultsTab(uiState.report, uiState.feedbackVisible,
                                uiState.feedbackDraft, uiState.isLoading, viewModel)
                2 -> EvaluationTab(uiState.report?.evaluationMetrics)
                3 -> HistoryTab(uiState.history, viewModel)
            }
        }
    }
}

// ── New Analysis Tab ──────────────────────────────────────────────────────────

@Composable
private fun NewAnalysisTab(
    form: CredenceFormState,
    isLoading: Boolean,
    llmStatus: LlmStatus,
    llmQualityMode: QualityMode,
    viewModel: CredenceAIViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = Graphite),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreditScore, null, tint = AuroraPurple, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tatva Ank Analysis", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Multi-agent credit scoring pipeline",
                            fontSize = 12.sp, color = MutedGrey)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // LLM agent status badge
                LlmStatusBadge(llmStatus, llmQualityMode)
            }
        }

        // Company details
        SectionHeader("Company Details")
        CredenceTextField(label = "Company Name *", value = form.companyName) {
            viewModel.updateForm { copy(companyName = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CredenceTextField(
                label    = "Industry",
                value    = form.industry,
                modifier = Modifier.weight(1f)
            ) { viewModel.updateForm { copy(industry = it) } }
            CredenceTextField(
                label    = "Sector",
                value    = form.sector,
                modifier = Modifier.weight(1f)
            ) { viewModel.updateForm { copy(sector = it) } }
        }
        CredenceTextField(
            label    = "Company Description / News",
            value    = form.description,
            maxLines = 4,
            singleLine = false
        ) { viewModel.updateForm { copy(description = it) } }

        // Financial profile
        SectionHeader("Financial Profile")
        InfoNote("Enter values in consistent currency units (e.g., INR crores or USD millions).")

        FinancialRow("Total Revenue *", form.totalRevenue) {
            viewModel.updateForm { copy(totalRevenue = it) }
        }
        FinancialRow("Total Assets *", form.totalAssets) {
            viewModel.updateForm { copy(totalAssets = it) }
        }
        FinancialRow("Total Liabilities", form.totalLiabilities) {
            viewModel.updateForm { copy(totalLiabilities = it) }
        }
        FinancialRow("EBIT", form.ebit) {
            viewModel.updateForm { copy(ebit = it) }
        }
        FinancialRow("Working Capital", form.workingCapital) {
            viewModel.updateForm { copy(workingCapital = it) }
        }
        FinancialRow("Retained Earnings", form.retainedEarnings) {
            viewModel.updateForm { copy(retainedEarnings = it) }
        }
        FinancialRow("Market Value of Equity", form.marketValueEquity) {
            viewModel.updateForm { copy(marketValueEquity = it) }
        }
        FinancialRow("Total Debt", form.totalDebt) {
            viewModel.updateForm { copy(totalDebt = it) }
        }
        FinancialRow("Shareholders' Equity", form.shareholdersEquity) {
            viewModel.updateForm { copy(shareholdersEquity = it) }
        }

        // Optional prior-year fields for trend analysis
        SectionHeader("Prior Year (Optional — for trend analysis)")
        FinancialRow("Prior Year Revenue", form.priorYearRevenue) {
            viewModel.updateForm { copy(priorYearRevenue = it) }
        }
        FinancialRow("Prior Year EBIT", form.priorYearEbit) {
            viewModel.updateForm { copy(priorYearEbit = it) }
        }
        CredenceTextField(
            label      = "Analyst Notes",
            value      = form.analystNotes,
            maxLines   = 3,
            singleLine = false
        ) { viewModel.updateForm { copy(analystNotes = it) } }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick  = { viewModel.startAnalysis() },
            enabled  = form.canSubmit && !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = AuroraPurple,
                disabledContainerColor = AuroraPurple.copy(alpha = 0.3f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                Icon(Icons.Default.Analytics, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isLoading) "Running Tatva Ank Pipeline…" else "Run Tatva Ank Analysis",
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Results Tab ───────────────────────────────────────────────────────────────

@Composable
private fun ResultsTab(
    report: TatvaAnkReport?,
    feedbackVisible: Map<String, Boolean>,
    feedbackDraft: Map<String, String>,
    isLoading: Boolean,
    viewModel: CredenceAIViewModel
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AuroraPurple)
                Spacer(Modifier.height(16.dp))
                Text("Running multi-agent pipeline…", color = MutedGrey)
                Text("Quant + NLP agents running in parallel", fontSize = 12.sp, color = MutedGrey)
            }
        }
        return
    }

    if (report == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CreditScore, null, tint = MutedGrey, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("No analysis yet", color = MutedGrey, fontSize = 16.sp)
                Text("Run an analysis from the New Analysis tab", fontSize = 13.sp, color = MutedGrey)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Gauge + Score ────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = Graphite),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier            = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(report.request.companyName,
                    fontWeight  = FontWeight.Bold,
                    fontSize    = 18.sp,
                    color       = Color.White,
                    textAlign   = TextAlign.Center)
                if (report.request.industry.isNotBlank()) {
                    Text("${report.request.industry} · ${report.request.sector}",
                        fontSize = 12.sp, color = MutedGrey)
                }
                Spacer(Modifier.height(16.dp))

                TatvaAnkGauge(score = report.tatvaAnkScore, modifier = Modifier.size(180.dp))

                Spacer(Modifier.height(12.dp))
                RiskBadge(report.riskLabel)

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MetricChip("Z-Score", "%.2f".format(report.altmanZScore))
                    MetricChip(
                        "D/E",
                        if (report.debtToEquity == Double.MAX_VALUE) "∞"
                        else "%.2f".format(report.debtToEquity)
                    )
                    MetricChip("Sentiment", "%.2f".format(report.sentimentScore))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Processed in ${report.processingTimeMs}ms · " +
                    "${report.evaluationPassCount}/22 eval metrics passed",
                    fontSize = 11.sp, color = MutedGrey, textAlign = TextAlign.Center
                )
            }
        }

        // ── Agent cards ──────────────────────────────────────────────────
        SectionHeader("Agent Results")
        AgentCard(
            result          = report.ingestionResult,
            feedbackVisible = feedbackVisible[report.ingestionResult.agentName] ?: false,
            feedbackDraft   = feedbackDraft[report.ingestionResult.agentName] ?: "",
            onToggleFeedback = { viewModel.toggleFeedbackPanel(report.ingestionResult.agentName) },
            onFeedbackChange = { viewModel.updateFeedbackDraft(report.ingestionResult.agentName, it) },
            onSubmitFeedback = { viewModel.submitFeedback(report.ingestionResult.agentName) }
        )
        AgentCard(
            result          = report.quantResult,
            feedbackVisible = feedbackVisible[report.quantResult.agentName] ?: false,
            feedbackDraft   = feedbackDraft[report.quantResult.agentName] ?: "",
            onToggleFeedback = { viewModel.toggleFeedbackPanel(report.quantResult.agentName) },
            onFeedbackChange = { viewModel.updateFeedbackDraft(report.quantResult.agentName, it) },
            onSubmitFeedback = { viewModel.submitFeedback(report.quantResult.agentName) }
        )
        AgentCard(
            result          = report.nlpResult,
            feedbackVisible = feedbackVisible[report.nlpResult.agentName] ?: false,
            feedbackDraft   = feedbackDraft[report.nlpResult.agentName] ?: "",
            onToggleFeedback = { viewModel.toggleFeedbackPanel(report.nlpResult.agentName) },
            onFeedbackChange = { viewModel.updateFeedbackDraft(report.nlpResult.agentName, it) },
            onSubmitFeedback = { viewModel.submitFeedback(report.nlpResult.agentName) }
        )
        AgentCard(
            result          = report.orchestratorResult,
            feedbackVisible = feedbackVisible[report.orchestratorResult.agentName] ?: false,
            feedbackDraft   = feedbackDraft[report.orchestratorResult.agentName] ?: "",
            onToggleFeedback = { viewModel.toggleFeedbackPanel(report.orchestratorResult.agentName) },
            onFeedbackChange = { viewModel.updateFeedbackDraft(report.orchestratorResult.agentName, it) },
            onSubmitFeedback = { viewModel.submitFeedback(report.orchestratorResult.agentName) }
        )

        // ── HITL feedback summary ────────────────────────────────────────
        if (report.hitlFeedback.isNotEmpty()) {
            SectionHeader("Submitted Feedback")
            report.hitlFeedback.forEach { (agent, fb) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(containerColor = Graphite)
                ) {
                    Row(Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Feedback, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(agent, fontSize = 11.sp, color = MutedGrey)
                            Text(fb, fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Evaluation Tab ────────────────────────────────────────────────────────────

@Composable
private fun EvaluationTab(metrics: List<EvaluationMetric>?) {
    if (metrics == null || metrics.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Assessment, null, tint = MutedGrey, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Run an analysis to see evaluation metrics", color = MutedGrey)
            }
        }
        return
    }

    val passCount = metrics.count { it.passed }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = Graphite),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Assessment, null, tint = AuroraPurple, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Evaluation Framework", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$passCount / ${metrics.size} metrics passed",
                            fontSize = 13.sp,
                            color = if (passCount >= 18) NeonGreen else if (passCount >= 12) LuxeGold else SoftRed)
                    }
                }
            }
        }

        // Group by category
        EvaluationCategory.entries.forEach { cat ->
            val catMetrics = metrics.filter { it.category == cat }
            if (catMetrics.isNotEmpty()) {
                item {
                    Text(
                        cat.displayName,
                        color      = categoryColor(cat),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                        modifier   = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(catMetrics) { metric ->
                    EvaluationMetricRow(metric)
                }
            }
        }
    }
}

// ── History Tab ───────────────────────────────────────────────────────────────

@Composable
private fun HistoryTab(
    history: List<com.stocksense.app.data.database.entities.CredenceAnalysis>,
    viewModel: CredenceAIViewModel
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, null, tint = MutedGrey, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("No past analyses yet", color = MutedGrey)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${history.size} analyses", color = MutedGrey, fontSize = 12.sp)
                TextButton(onClick = { viewModel.clearHistory() }) {
                    Text("Clear All", color = SoftRed, fontSize = 12.sp)
                }
            }
        }
        items(history) { analysis ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.loadHistoricalReport(analysis) },
                colors = CardDefaults.cardColors(containerColor = Graphite),
                shape  = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Score circle
                    Box(
                        modifier        = Modifier.size(44.dp).clip(CircleShape)
                            .background(riskLabelColor(analysis.riskLabel).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "%.0f".format(analysis.tatvaAnkScore),
                            fontWeight = FontWeight.Bold,
                            color      = riskLabelColor(analysis.riskLabel),
                            fontSize   = 15.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(analysis.companyName,
                            fontWeight = FontWeight.SemiBold, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (analysis.industry.isNotBlank()) {
                            Text(analysis.industry, fontSize = 12.sp, color = MutedGrey,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(dateFormat.format(Date(analysis.createdAt)),
                            fontSize = 11.sp, color = MutedGrey)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        RiskBadgeSmall(analysis.riskLabel)
                        Spacer(Modifier.height(4.dp))
                        Text("Z: ${"%.2f".format(analysis.altmanZScore)}",
                            fontSize = 11.sp, color = MutedGrey)
                    }
                }
            }
        }
    }
}

// ── Composable components ─────────────────────────────────────────────────────

@Composable
private fun TatvaAnkGauge(score: Double, modifier: Modifier = Modifier) {
    val gaugeColor = when {
        score >= 70 -> NeonGreen
        score >= 40 -> LuxeGold
        else        -> SoftRed
    }
    val sweepAngle = (score / 100.0 * 240.0).toFloat()
    val startAngle = 150f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val inset = strokeWidth / 2f
            val arcRect = androidx.compose.ui.geometry.Rect(
                offset = Offset(inset, inset),
                size   = Size(size.width - strokeWidth, size.height - strokeWidth)
            )
            // Background arc
            drawArc(
                color      = GlassStroke,
                startAngle = startAngle,
                sweepAngle = 240f,
                useCenter  = false,
                topLeft    = arcRect.topLeft,
                size       = arcRect.size,
                style      = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
            // Score arc
            if (sweepAngle > 0f) {
                drawArc(
                    brush      = Brush.sweepGradient(
                        listOf(gaugeColor.copy(alpha = 0.5f), gaugeColor),
                        center = center
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter  = false,
                    topLeft    = arcRect.topLeft,
                    size       = arcRect.size,
                    style      = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                // Needle dot
                val angleRad = Math.toRadians((startAngle + sweepAngle).toDouble())
                val radius = (size.minDimension - strokeWidth) / 2f
                val dotX = center.x + (radius * cos(angleRad)).toFloat()
                val dotY = center.y + (radius * sin(angleRad)).toFloat()
                drawCircle(gaugeColor, radius = strokeWidth / 2f, center = Offset(dotX, dotY))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "%.1f".format(score),
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 32.sp,
                color      = gaugeColor
            )
            Text("/ 100", fontSize = 12.sp, color = MutedGrey)
            Text("Tatva Ank", fontSize = 11.sp, color = MutedGrey)
        }
    }
}

@Composable
private fun RiskBadge(riskLabel: RiskLabel) {
    val color = when (riskLabel) {
        RiskLabel.LOW    -> NeonGreen
        RiskLabel.MEDIUM -> LuxeGold
        RiskLabel.HIGH   -> SoftRed
    }
    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            "${riskLabel.emoji} ${riskLabel.displayName}",
            color      = color,
            fontWeight = FontWeight.Bold,
            fontSize   = 14.sp,
            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RiskBadgeSmall(riskLabelName: String) {
    val label = runCatching { RiskLabel.valueOf(riskLabelName) }.getOrElse { RiskLabel.HIGH }
    val color = when (label) {
        RiskLabel.LOW    -> NeonGreen
        RiskLabel.MEDIUM -> LuxeGold
        RiskLabel.HIGH   -> SoftRed
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(label.name, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun AgentCard(
    result: AgentResult,
    feedbackVisible: Boolean,
    feedbackDraft: String,
    onToggleFeedback: () -> Unit,
    onFeedbackChange: (String) -> Unit,
    onSubmitFeedback: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val agentIcon = when (result.agentName) {
        "Ingestion"   -> Icons.Default.Description
        "Quant"       -> Icons.Default.QueryStats
        "NLP Risk"    -> Icons.Default.Psychology
        "Orchestrator" -> Icons.Default.Hub
        else          -> Icons.Default.SmartToy
    }
    val agentColor = when (result.agentName) {
        "Ingestion"   -> ElectricBlue
        "Quant"       -> NeonGreen
        "NLP Risk"    -> LuxeGold
        "Orchestrator" -> AuroraPurple
        else          -> MutedGrey
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.elevatedCardColors(containerColor = Graphite),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Header row ───────────────────────────────────────────────
            Row(
                modifier      = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(agentIcon, null, tint = agentColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.agentName, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(
                        "Confidence: ${"%.0f".format(result.confidence * 100)}%",
                        fontSize = 11.sp, color = MutedGrey
                    )
                }
                // Status dot
                val statusColor = if (result.passed) NeonGreen else SoftRed
                Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = MutedGrey, modifier = Modifier.size(20.dp)
                )
            }

            // ── Confidence bar ───────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { result.confidence.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color    = agentColor,
                trackColor = GlassStroke
            )

            // ── Expanded content ─────────────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(result.summary,
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 19.sp)
                    if (result.processingTimeMs > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("${result.processingTimeMs}ms",
                            fontSize = 11.sp, color = MutedGrey)
                    }

                    // ── HITL Feedback ────────────────────────────────────
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick  = onToggleFeedback,
                        modifier = Modifier.height(32.dp),
                        shape    = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        border   = BorderStroke(1.dp, agentColor.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.Feedback, null,
                            tint = agentColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Feedback", fontSize = 12.sp, color = agentColor)
                    }

                    AnimatedVisibility(visible = feedbackVisible) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value         = feedbackDraft,
                                onValueChange = onFeedbackChange,
                                label         = { Text("Your feedback", fontSize = 12.sp) },
                                modifier      = Modifier.fillMaxWidth(),
                                maxLines      = 3,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = agentColor,
                                    unfocusedBorderColor = GlassStroke,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = onSubmitFeedback,
                                enabled = feedbackDraft.isNotBlank(),
                                modifier = Modifier.height(34.dp),
                                shape   = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors  = ButtonDefaults.buttonColors(containerColor = agentColor)
                            ) {
                                Text("Submit", fontSize = 12.sp, color = DeepBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvaluationMetricRow(metric: EvaluationMetric) {
    val color = if (metric.passed) NeonGreen else SoftRed
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = Graphite.copy(alpha = 0.6f)),
        shape    = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (metric.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                null, tint = color, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(metric.name, fontSize = 13.sp, color = Color.White,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("%.0f%%".format(metric.score * 100),
                        fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress  = { metric.score.toFloat().coerceIn(0f, 1f) },
                    modifier  = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color     = color,
                    trackColor = GlassStroke
                )
                if (metric.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(metric.description, fontSize = 11.sp, color = MutedGrey, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        Text(label, fontSize = 11.sp, color = MutedGrey)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text       = text.uppercase(),
        fontSize   = 11.sp,
        fontWeight = FontWeight.Bold,
        color      = MutedGrey,
        letterSpacing = 1.sp
    )
}

@Composable
private fun InfoNote(text: String) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ElectricBlue.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment   = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, null, tint = ElectricBlue, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = ElectricBlue.copy(alpha = 0.8f))
    }
}

@Composable
private fun CredenceTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label, fontSize = 12.sp) },
        modifier      = modifier.fillMaxWidth(),
        maxLines      = maxLines,
        singleLine    = singleLine,
        shape         = RoundedCornerShape(10.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = ElectricBlue,
            unfocusedBorderColor = GlassStroke,
            focusedTextColor     = Color.White,
            unfocusedTextColor   = Color.White,
            focusedLabelColor    = ElectricBlue,
            unfocusedLabelColor  = MutedGrey
        )
    )
}

@Composable
private fun FinancialRow(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label, fontSize = 12.sp) },
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape         = RoundedCornerShape(10.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AuroraPurple,
            unfocusedBorderColor = GlassStroke,
            focusedTextColor     = Color.White,
            unfocusedTextColor   = Color.White,
            focusedLabelColor    = AuroraPurple,
            unfocusedLabelColor  = MutedGrey
        )
    )
}

private fun categoryColor(cat: EvaluationCategory): Color = when (cat) {
    EvaluationCategory.FACTUAL_ACCURACY  -> ElectricBlue
    EvaluationCategory.RELEVANCE         -> NeonGreen
    EvaluationCategory.SAFETY_COMPLIANCE -> SoftRed
    EvaluationCategory.SYSTEM_PERFORMANCE -> LuxeGold
    EvaluationCategory.REASONING_QUALITY -> AuroraPurple
    EvaluationCategory.UX_DELIVERY       -> Teal400
}

private fun riskLabelColor(name: String): Color = when (name) {
    "LOW"    -> NeonGreen
    "MEDIUM" -> LuxeGold
    else     -> SoftRed
}

// ── LLM Status Badge ─────────────────────────────────────────────────────────

@Composable
private fun LlmStatusBadge(status: LlmStatus, mode: QualityMode) {
    val (label, color) = when (status) {
        LlmStatus.READY              -> "LLM Ready · ${mode.name}" to NeonGreen
        LlmStatus.LOADING            -> "LLM Loading…" to LuxeGold
        LlmStatus.NATIVE_UNAVAILABLE -> "Template Mode · No LLM" to MutedGrey
        LlmStatus.MODEL_NOT_DOWNLOADED -> "Model Not Downloaded" to LuxeGold
        LlmStatus.LOAD_FAILED        -> "LLM Load Failed · Template" to SoftRed
        LlmStatus.TEMPLATE_FALLBACK  -> "Template Fallback" to LuxeGold
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

