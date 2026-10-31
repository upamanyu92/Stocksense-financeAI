package com.stocksense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
    var gatewayUrl by remember(state.gatewayUrl) { mutableStateOf(state.gatewayUrl) }
    var gatewayToken by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.refreshStatus() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("AI service") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("NVIDIA Nemotron", style = MaterialTheme.typography.titleLarge)
            Text("Primary: Nemotron 3 Super through NVIDIA.")
            Text("Fallback: Nemotron hosted on Hugging Face when NVIDIA quota is exhausted or the service is unavailable.")
            Text("AI analysis requires internet access. Questions and supplied financial data are sent to the active provider.")
            OutlinedTextField(value = gatewayUrl, onValueChange = { gatewayUrl = it },
                label = { Text("Gateway URL") }, singleLine = true)
            OutlinedTextField(value = gatewayToken, onValueChange = { gatewayToken = it },
                label = { Text("Gateway access token") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text(if (state.hasGatewayToken) "Leave blank to retain the saved token." else "Use your gateway token, not a provider API key.") })
            Button(onClick = {
                viewModel.saveConnection(gatewayUrl, gatewayToken)
                gatewayToken = ""
            }) { Text("Save connection") }
            state.connectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
