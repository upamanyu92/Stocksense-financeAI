package com.stocksense.app.ui.screens

import android.app.ActivityManager
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.engine.LlmStatus
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.LlmSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(
    viewModel: LlmSettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDropdown by remember { mutableStateOf(false) }
    val safeSelectedIndex = uiState.selectedModelIndex.coerceIn(0, (uiState.availableModels.size - 1).coerceAtLeast(0))
    val selectedModel = uiState.availableModels.getOrElse(safeSelectedIndex) {
        uiState.availableModels.first()
    }

    val deviceRamGb = getDeviceRamGb(context)
    val bestModel = uiState.availableModels
        .filter { deviceRamGb >= it.recommendedRamGb }
        .maxByOrNull { it.recommendedRamGb } ?: uiState.availableModels.first()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importLocalModel(context, it) }
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
                        val statusColor = when {
                            uiState.isDownloading -> ElectricBlue
                            else -> when (uiState.status) {
                            LlmStatus.READY -> NeonGreen
                            LlmStatus.LOADING -> LuxeGold
                            LlmStatus.LOAD_FAILED -> SoftRed
                            LlmStatus.MODEL_NOT_DOWNLOADED -> MutedGrey
                            LlmStatus.NATIVE_UNAVAILABLE -> MutedGrey
                            LlmStatus.TEMPLATE_FALLBACK -> ElectricBlue
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(statusColor, RoundedCornerShape(6.dp))
                        )
                        Text("Model Status", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }

                    val statusText = when {
                        uiState.isDownloading && uiState.activeDownloadModelName.isNotBlank() ->
                            "Downloading ${uiState.activeDownloadModelName}…"
                        else -> when (uiState.status) {
                        LlmStatus.READY -> "Ready – LLM loaded and operational"
                        LlmStatus.LOADING -> "Loading model into memory…"
                        LlmStatus.LOAD_FAILED -> "Failed to load – try reloading or a different model"
                        LlmStatus.MODEL_NOT_DOWNLOADED -> "No model downloaded – download or import one below"
                        LlmStatus.NATIVE_UNAVAILABLE -> "This APK does not include the native llama runtime"
                        LlmStatus.TEMPLATE_FALLBACK -> "Using template-based responses (no LLM)"
                        }
                    }
                    Text(statusText, color = MutedGrey, fontSize = 14.sp)

                    if (!uiState.isNativeAvailable) {
                        Text(
                            "Downloads, imports, reloads, and live checks are disabled until you install a build with native llama support.",
                            color = SoftRed,
                            fontSize = 12.sp
                        )
                    }

                    if (uiState.isDownloading && uiState.activeDownloadModelName.isNotBlank()) {
                        Text("Downloading model: ${uiState.activeDownloadModelName}", color = ElectricBlue, fontSize = 13.sp)
                    }

                    if (uiState.currentModelName.isNotBlank() &&
                        (uiState.isModelDownloaded || uiState.status == LlmStatus.READY || uiState.status == LlmStatus.LOAD_FAILED || uiState.status == LlmStatus.TEMPLATE_FALLBACK)
                    ) {
                        Text(
                            text = if (uiState.isDownloading) "Loaded model: ${uiState.currentModelName}" else "Model: ${uiState.currentModelName}",
                            color = if (uiState.isDownloading) MutedGrey else ElectricBlue,
                            fontSize = 13.sp
                        )
                    }

                    val selectedModelLabel = uiState.availableModels
                        .getOrNull(uiState.selectedModelIndex)
                        ?.name
                        .orEmpty()
                    Text(
                        text = buildString {
                            append("Selected: ")
                            append(selectedModelLabel.ifBlank { "—" })
                            append(" • Native: ")
                            append(if (uiState.isNativeAvailable) "Available" else "Unavailable")
                            append(" • Downloaded: ")
                            append(if (uiState.isModelDownloaded) "Yes" else "No")
                            append(" • Last check: ")
                            append(if (uiState.lastInferenceTimeMs > 0) "${uiState.lastInferenceTimeMs} ms" else "—")
                        },
                        color = MutedGrey,
                        fontSize = 12.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.status == LlmStatus.LOAD_FAILED || uiState.status == LlmStatus.MODEL_NOT_DOWNLOADED) {
                            OutlinedButton(
                                onClick = { viewModel.reloadModel() },
                                enabled = uiState.isNativeAvailable,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reload")
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.runLiveCheck() },
                            enabled = uiState.isNativeAvailable && !uiState.isRunningLiveCheck,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                        ) {
                            if (uiState.isRunningLiveCheck) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Live check")
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

                    uiState.liveCheckMessage?.let { message ->
                        Text(message, color = MutedGrey, fontSize = 12.sp)
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
                        if (uiState.isNativeAvailable) {
                            "If model download fails, you can import a compatible GGUF model file from your device. Download a BitNet GGUF model from a trusted source (for example HuggingFace or your own backup) and select it here."
                        } else {
                            "Import is unavailable in builds without the native llama runtime"
                        },
                        color = MutedGrey,
                        fontSize = 14.sp
                    )
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        enabled = uiState.isNativeAvailable && !uiState.isImporting,
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
                        if (uiState.isNativeAvailable) {
                            "Choose a compatible model to download"
                        } else {
                            "Downloading is disabled because this build cannot load local GGUF models"
                        },
                        color = MutedGrey,
                        fontSize = 14.sp
                    )

                    // Model dropdown
                    ExposedDropdownMenuBox(
                        expanded = showDropdown,
                        onExpandedChange = { showDropdown = it }
                    ) {

                        OutlinedTextField(
                            value = selectedModel.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                            uiState.availableModels.forEachIndexed { index, model ->
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
                                        viewModel.selectModel(index)
                                        showDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Download progress
                    if (uiState.isDownloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (uiState.activeDownloadModelName.isNotBlank()) {
                                Text(
                                    "Downloading ${uiState.activeDownloadModelName}",
                                    color = ElectricBlue,
                                    fontSize = 12.sp
                                )
                            }
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
                        onClick = { viewModel.downloadSelectedModel() },
                        enabled = uiState.isNativeAvailable && !uiState.isDownloading,
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

            // Recommendation banner
            Card(
                colors = CardDefaults.cardColors(containerColor = Graphite),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Recommended Model for Your Device", fontWeight = FontWeight.Bold, color = NeonGreen)
                    Text(
                        "Detected RAM: ${deviceRamGb}GB\nBest compatibility: ${bestModel.name}",
                        color = MutedGrey,
                        fontSize = 14.sp
                    )
                    Text(
                        "${bestModel.description} (${bestModel.sizeLabel})",
                        color = MutedGrey,
                        fontSize = 13.sp
                    )
                    Text(
                        "Download: ${bestModel.downloadUrl}",
                        color = ElectricBlue,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun getDeviceRamGb(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    return (memInfo.totalMem / (1024 * 1024 * 1024)).toInt()
}
