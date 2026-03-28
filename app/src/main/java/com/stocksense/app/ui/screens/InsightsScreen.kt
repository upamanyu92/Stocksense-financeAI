package com.stocksense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.engine.AgenticMetrics
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.engine.QualityMode
import com.stocksense.app.viewmodel.ChatMessage
import com.stocksense.app.viewmodel.InsightsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    symbol: String,
    viewModel: InsightsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var chatInput by remember { mutableStateOf("") }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LLM Agent Status Banner
            LlmStatusBanner(status = uiState.llmStatus)

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

            // Agentic Evaluation Metrics
            AgenticMetricsCard(metrics = uiState.metrics)

            // Chat with Agent section
            Text("Chat with Agent", style = MaterialTheme.typography.titleMedium)

            // Chat messages
            val listState = rememberLazyListState()
            val messageCount = uiState.chatMessages.size
            LaunchedEffect(messageCount) {
                if (messageCount > 0) {
                    listState.animateScrollToItem(messageCount - 1)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.chatMessages) { message ->
                    ChatBubble(message = message)
                }
                if (uiState.isChatLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Agent is thinking…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Chat input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = { Text("Ask about $symbol…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                )
                IconButton(
                    onClick = {
                        if (chatInput.isNotBlank()) {
                            viewModel.sendChatMessage(chatInput)
                            chatInput = ""
                        }
                    },
                    enabled = !uiState.isChatLoading && chatInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun LlmStatusBanner(status: LlmStatus) {
    val (text, color) = when (status) {
        LlmStatus.READY -> "✅ LLM Agent Ready" to Color(0xFF4CAF50)
        LlmStatus.LOADING -> "⏳ LLM Agent Loading…" to Color(0xFFFFC107)
        LlmStatus.NATIVE_UNAVAILABLE -> "⚠️ Native LLM unavailable – using template mode" to Color(0xFFFF9800)
        LlmStatus.MODEL_NOT_DOWNLOADED -> "📥 Model not downloaded – using template mode" to Color(0xFFFF9800)
        LlmStatus.LOAD_FAILED -> "❌ LLM failed to load – using template mode" to Color(0xFFF44336)
        LlmStatus.TEMPLATE_FALLBACK -> "ℹ️ Using template fallback (no LLM)" to Color(0xFF2196F3)
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun AgenticMetricsCard(metrics: AgenticMetrics) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Agentic Evaluation Metrics", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            MetricRow("Quality Mode", metrics.qualityMode.name)
            MetricRow("Native Available", if (metrics.isNativeAvailable) "Yes" else "No")
            MetricRow("Model Downloaded", if (metrics.isModelDownloaded) "Yes" else "No")
            MetricRow("Model File", metrics.modelFileName)
            MetricRow("Last Inference", if (metrics.lastInferenceTimeMs > 0) "${metrics.lastInferenceTimeMs}ms" else "N/A")
            MetricRow("Cache Hits", "${metrics.cacheHits}")
            MetricRow("Total Inferences", "${metrics.totalInferences}")
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Arrangement.End else Arrangement.Start
    val bgColor = if (message.isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = alignment
    ) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (message.isUser) "You" else "Agent",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.7f)
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
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
