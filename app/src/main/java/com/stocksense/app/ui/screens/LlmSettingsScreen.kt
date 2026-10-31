package com.stocksense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stocksense.app.viewmodel.LlmSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(viewModel: LlmSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshStatus() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("AI service") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("NVIDIA Nemotron", style = MaterialTheme.typography.titleLarge)
            Text("Primary: Nemotron 3 Super through NVIDIA.")
            Text("Fallback: Nemotron hosted on Hugging Face when NVIDIA quota is exhausted or the service is unavailable.")
            Text("AI analysis requires internet access. Questions and supplied financial data are sent to the active provider.")
            Text(if (state.isConfigured) "AI service configured" else "AI service has not been configured for this build.")
            Text("Last provider model: ${state.currentModelName.ifBlank { "None" }}")
            Text("Status: ${state.status.name.replace('_', ' ')}")
            Button(onClick = viewModel::runLiveCheck,
                enabled = state.isConfigured && !state.isRunningLiveCheck) {
                Text(if (state.isRunningLiveCheck) "Checking…" else "Check connection")
            }
            state.liveCheckMessage?.let { Text(it) }
            Text("If both providers are unavailable, responses are labelled as general template guidance.")
        }
    }
}
