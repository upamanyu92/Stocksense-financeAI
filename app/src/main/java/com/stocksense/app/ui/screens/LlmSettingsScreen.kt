package com.stocksense.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.engine.QualityMode
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.LlmSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(
    viewModel: LlmSettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedMode by remember { mutableStateOf(QualityMode.BALANCED) }
    var showDropdown by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importLocalModel(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM Model Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Graphite),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val statusColor = when (uiState.status) {
                            LlmStatus.READY -> NeonGreen
                            LlmStatus.LOADING -> LuxeGold
                            LlmStatus.LOAD_FAILED -> SoftRed
                            LlmStatus.MODEL_NOT_DOWNLOADED -> MutedGrey
                            LlmStatus.NATIVE_UNAVAILABLE -> MutedGrey
                            LlmStatus.TEMPLATE_FALLBACK -> ElectricBlue
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(statusColor, RoundedCornerShape(6.dp))
                        )
                        Text("Model Status", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }

                    val statusText = when (uiState.status) {
                        LlmStatus.READY -> "Ready – LLM loaded and operational"
                        LlmStatus.LOADING -> "Loading model into memory…"
                        LlmStatus.LOAD_FAILED -> "Failed to load – try reloading or a different model"
                        LlmStatus.MODEL_NOT_DOWNLOADED -> "No model downloaded – download or import one below"
                        LlmStatus.NATIVE_UNAVAILABLE -> "Native library not available – using template fallback"
                        LlmStatus.TEMPLATE_FALLBACK -> "Using template-based responses (no LLM)"
                    }
                    Text(statusText, color = MutedGrey, fontSize = 14.sp)

                    if (uiState.currentModelName.isNotBlank()) {
                        Text("Model: ${uiState.currentModelName}", color = ElectricBlue, fontSize = 13.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.status == LlmStatus.LOAD_FAILED || uiState.status == LlmStatus.MODEL_NOT_DOWNLOADED) {
                            OutlinedButton(
                                onClick = { viewModel.reloadModel() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reload")
                            }
                        }
                        if (uiState.currentModelName.isNotBlank()) {
                            OutlinedButton(
                                onClick = { viewModel.deleteModels() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftRed)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }

            // Import Local GGUF Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Graphite),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Import Local Model", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "Select a GGUF model file from your device storage",
                        color = MutedGrey,
                        fontSize = 14.sp
                    )

                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        enabled = !uiState.isImporting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuroraPurple,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (uiState.isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Importing…")
                        } else {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose GGUF File")
                        }
                    }

                    uiState.importError?.let { error ->
                        Text(error, color = SoftRed, fontSize = 13.sp)
                    }
                }
            }

            // Download from predefined list
            Card(
                colors = CardDefaults.cardColors(containerColor = Graphite),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Download Model", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "Choose a compatible model to download",
                        color = MutedGrey,
                        fontSize = 14.sp
                    )

                    // Model dropdown
                    ExposedDropdownMenuBox(
                        expanded = showDropdown,
                        onExpandedChange = { showDropdown = it }
                    ) {
                        val selectedModel = uiState.availableModels.find { it.mode == selectedMode }
                            ?: uiState.availableModels.first()

                        OutlinedTextField(
                            value = selectedModel.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = GlassSurface,
                                unfocusedContainerColor = GlassSurface,
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = GlassStroke,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            uiState.availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.name, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${model.description} • ${model.sizeLabel}",
                                                fontSize = 12.sp,
                                                color = MutedGrey
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedMode = model.mode
                                        showDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Download progress
                    if (uiState.isDownloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { uiState.downloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = NeonGreen,
                                trackColor = NightGlare
                            )
                            Text(
                                "${(uiState.downloadProgress * 100).toInt()}% downloaded",
                                color = MutedGrey,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.downloadModel(selectedMode) },
                        enabled = !uiState.isDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = DeepBlack
                        )
                    ) {
                        if (uiState.isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = DeepBlack
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Downloading…")
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    uiState.error?.let { error ->
                        Text(error, color = SoftRed, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
