package com.stocksense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.stocksense.app.R
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.ChatUiMessage
import com.stocksense.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("senseAI")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
        ) {
            AgentStatusStrip(
                status = uiState.llmStatus,
                modelName = uiState.llmMetrics.modelFileName,
                isChecking = uiState.isCheckingAgent,
                lastInferenceTimeMs = uiState.llmMetrics.lastInferenceTimeMs,
                onRefresh = { viewModel.refreshAgentStatus() }
            )

            // Messages list
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.messages.isEmpty() && !uiState.isLoading) {
                    item {
                        WelcomeMessage()
                    }
                }
                items(uiState.messages) { message ->
                    ChatBubble(message = message)
                }
                if (uiState.isLoading) {
                    item {
                        LoadingBubble()
                    }
                }
            }

            // Error message
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = SoftRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Suggested queries
            if (uiState.messages.isEmpty()) {
                SuggestedQueries(onQueryClick = { query ->
                    inputText = query
                })
            }

            // Input bar
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                        coroutineScope.launch {
                            if (uiState.messages.isNotEmpty()) {
                                listState.animateScrollToItem(uiState.messages.size - 1)
                            }
                        }
                    }
                },
                isLoading = uiState.isLoading
            )
        }
    }
}

@Composable
private fun AgentStatusStrip(
    status: LlmStatus,
    modelName: String,
    isChecking: Boolean,
    lastInferenceTimeMs: Long,
    onRefresh: () -> Unit
) {
    val (label, color, detail) = when (status) {
        LlmStatus.READY -> Triple(
            "Agent live",
            NeonGreen,
            buildString {
                append(modelName.ifBlank { "Local model loaded" })
                if (lastInferenceTimeMs > 0) append(" • ${lastInferenceTimeMs} ms")
            }
        )
        LlmStatus.LOADING -> Triple("Loading agent", LuxeGold, "Preparing the local model")
        LlmStatus.MODEL_NOT_DOWNLOADED -> Triple(
            "Template mode",
            ElectricBlue,
            "Using built-in responses until a GGUF model is downloaded"
        )
        LlmStatus.NATIVE_UNAVAILABLE -> Triple(
            "Template mode",
            ElectricBlue,
            "Using built-in responses because this build has no native llama runtime"
        )
        LlmStatus.LOAD_FAILED -> Triple(
            "Load failed",
            SoftRed,
            "Using built-in responses because the selected model could not load"
        )
        LlmStatus.TEMPLATE_FALLBACK -> Triple(
            "Template mode",
            ElectricBlue,
            "Using built-in responses while the local agent is inactive"
        )
    }

    Surface(
        color = Graphite,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = color, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGrey
                )
            }
            IconButton(onClick = onRefresh, enabled = !isChecking) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh agent status", tint = ElectricBlue)
                }
            }
        }
    }
}

@Composable
private fun WelcomeMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = "StockSense AI",
            modifier = Modifier.size(56.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "StockSense AI",
            style = MaterialTheme.typography.headlineSmall,
            color = ElectricBlue
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ask me about stocks, predictions, or market analysis",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGrey
        )
    }
}

@Composable
private fun ChatBubble(message: ChatUiMessage) {
    val isUser = message.isUser
    val bubbleColor = if (isUser) ElectricBlueDeep.copy(alpha = 0.3f) else Graphite
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) ElectricBlue else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LoadingBubble() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(Graphite)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MutedGrey.copy(alpha = 0.6f))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thinking…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGrey
                )
            }
        }
    }
}

@Composable
private fun SuggestedQueries(onQueryClick: (String) -> Unit) {
    val suggestions = listOf(
        "What is RELIANCE prediction?",
        "Show my watchlist",
        "Market analysis"
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionChip(
                onClick = { onQueryClick(suggestion) },
                label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Graphite,
                    labelColor = ElectricBlue
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(
                    enabled = true,
                    borderColor = GlassStroke
                )
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        color = Graphite,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about any stock…", color = MutedGrey) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = GlassStroke,
                    cursorColor = NeonGreen,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = NeonGreen,
                    disabledContentColor = MutedGrey
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}
