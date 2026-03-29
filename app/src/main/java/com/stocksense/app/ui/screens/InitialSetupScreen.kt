package com.stocksense.app.ui.screens

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.ui.theme.*
import com.stocksense.app.viewmodel.InitialSetupViewModel
import kotlin.math.roundToInt
import kotlin.math.sin

// ── Finance chart background ──────────────────────────────────────────────────

private data class ChartLine(
    val yFrac: Float, val ampFrac: Float, val freq: Float,
    val phaseShift: Float, val color: Color
)

private val CHART_LINES = listOf(
    ChartLine(0.18f, 0.04f, 2.8f, 0.0f, NeonGreen),
    ChartLine(0.33f, 0.05f, 2.2f, 1.1f, ElectricBlue),
    ChartLine(0.50f, 0.04f, 3.5f, 2.0f, LuxeGold),
    ChartLine(0.67f, 0.03f, 3.0f, 0.6f, NeonGreen),
    ChartLine(0.82f, 0.05f, 2.5f, 1.7f, ElectricBlue)
)

@Composable
private fun FinanceChartBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "chartBg")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "chartPhase"
    )
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        CHART_LINES.forEach { line ->
            val baseY = h * line.yFrac; val amp = h * line.ampFrac
            val path = Path(); var first = true
            var x = 0f
            while (x <= w) {
                val t = x / w * line.freq * 2 * Math.PI
                val y = (baseY + amp * sin(phase + line.phaseShift + t)).toFloat()
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += 4f
            }
            drawPath(path, color = line.color.copy(alpha = 0.09f), style = Stroke(width = 1.5f))
        }
    }
}

// ── Shared widgets ────────────────────────────────────────────────────────────

@Composable
private fun StepIndicator(currentStep: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepDot(1, currentStep)
        Box(
            modifier = Modifier
                .width(52.dp).height(2.dp)
                .background(if (currentStep >= 2) NeonGreen else GlassStroke, RoundedCornerShape(1.dp))
        )
        StepDot(2, currentStep)
    }
}

@Composable
private fun StepDot(step: Int, currentStep: Int) {
    val done = currentStep > step
    val active = currentStep == step
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    when { done -> NeonGreen; active -> ElectricBlue; else -> Graphite },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(Icons.Default.Check, null, tint = DeepBlack, modifier = Modifier.size(16.dp))
            } else {
                Text(
                    "$step", color = if (active) DeepBlack else MutedGrey,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }
        }
        Text(
            text = if (step == 1) "Choose" else "Download",
            fontSize = 10.sp,
            color = if (active || done) Color.White else MutedGrey
        )
    }
}

@Composable
private fun FinanceProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by shimmer.animateFloat(
        0.15f, 0.55f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    val animProg by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "progAnim"
    )
    Box(modifier = modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp))) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0A1628)))
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(animProg)
                .background(Brush.horizontalGradient(listOf(ElectricBlue, NeonGreen)))
        )
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(shimmerAlpha), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun SpecChip(label: String, modifier: Modifier = Modifier) {
    Surface(
        color = GlassSurface, shape = RoundedCornerShape(8.dp), modifier = modifier
    ) {
        Text(
            label, color = MutedGrey, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun DownloadStatRow(speed: String, eta: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$speed MB/s", fontSize = 13.sp, color = MutedGrey)
        Text("  ·  ", fontSize = 13.sp, color = MutedGrey.copy(alpha = 0.4f))
        Text(eta, fontSize = 13.sp, color = MutedGrey)
    }
}

// ── Step 1 — Model Selection ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionStep(
    uiState: InitialSetupViewModel.UiState,
    viewModel: InitialSetupViewModel
) {
    var showDropdown by remember { mutableStateOf(false) }
    val selected = uiState.availableModels.getOrNull(uiState.selectedModelIndex) ?: return
    val recName = uiState.availableModels.firstOrNull { it.recommended }?.name ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StepIndicator(currentStep = 1)

        // Header
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "⚡ AI Intelligence Setup",
                fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color.White
            )
            Text(
                "Select the on-device model that powers your finance AI assistant",
                fontSize = 13.sp, color = MutedGrey, lineHeight = 19.sp
            )
        }

        // Device info card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1628)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("📱 Your Device", fontSize = 11.sp, color = MutedGrey)
                    Text(
                        "RAM: ${uiState.deviceRamGb} GB  ·  ARM64  ·  Android ${Build.VERSION.RELEASE}",
                        fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("⭐ Best for you", fontSize = 11.sp, color = NeonGreen)
                    Text(
                        recName.substringBefore(" ("), fontSize = 12.sp,
                        color = NeonGreen, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Dropdown
        ExposedDropdownMenuBox(expanded = showDropdown, onExpandedChange = { showDropdown = it }) {
            OutlinedTextField(
                value = selected.name + if (selected.recommended) "  ⭐" else "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Select AI Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0A1628),
                    unfocusedContainerColor = Color(0xFF0A1628),
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = GlassStroke,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = NeonGreen,
                    unfocusedLabelColor = MutedGrey
                )
            )
            ExposedDropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                modifier = Modifier.background(Color(0xFF0D1F35))
            ) {
                uiState.availableModels.forEachIndexed { idx, model ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(model.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        "${model.sizeLabel} · Min ${model.recommendedRamGb} GB RAM",
                                        fontSize = 11.sp, color = MutedGrey
                                    )
                                }
                                if (model.recommended) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = NeonGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            "⭐ Best", color = NeonGreen, fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { viewModel.selectModel(idx); showDropdown = false },
                        modifier = Modifier.background(
                            if (idx == uiState.selectedModelIndex) ElectricBlue.copy(alpha = 0.1f) else Color.Transparent
                        )
                    )
                }
            }
        }

        // Model detail card
        val quant = selected.name.substringAfter("(").substringBefore(")").ifBlank { "Quantized" }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1628)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                if (selected.recommended) NeonGreen.copy(alpha = 0.45f) else GlassStroke
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        selected.name, fontWeight = FontWeight.Bold,
                        color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f)
                    )
                    if (selected.recommended) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = NeonGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, null, tint = NeonGreen, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Best Match", color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Spec chips
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpecChip("📦 ${selected.sizeLabel}", Modifier.weight(1f))
                    SpecChip("💾 ${selected.recommendedRamGb}GB+ RAM", Modifier.weight(1f))
                    SpecChip("⚙ $quant", Modifier.weight(1f))
                }

                HorizontalDivider(color = GlassStroke)

                // Short description
                Text(selected.description, color = ElectricBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)

                // Full explanation
                Text(selected.explanation, color = MutedGrey, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }

        // Download CTA
        Button(
            onClick = { viewModel.startDownload() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DeepBlack)
        ) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download & Continue", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }

        // Skip
        TextButton(onClick = { viewModel.skipSetup() }, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now — I'll set up the model later", color = MutedGrey, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))
    }
}

// ── Step 2 — Download Progress ────────────────────────────────────────────────

@Composable
private fun DownloadProgressStep(
    uiState: InitialSetupViewModel.UiState,
    viewModel: InitialSetupViewModel
) {
    val selected = uiState.availableModels.getOrNull(uiState.selectedModelIndex)

    val warningTransition = rememberInfiniteTransition(label = "warning")
    val warningAlpha by warningTransition.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "warningAlpha"
    )


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StepIndicator(currentStep = 2)
            Spacer(Modifier.height(8.dp))

            // Icon + title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CloudDownload, null,
                    tint = ElectricBlue, modifier = Modifier.size(52.dp)
                )
                Text(
                    "Downloading AI Brain", fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp, color = Color.White, textAlign = TextAlign.Center
                )
                if (selected != null) {
                    Text(selected.name, fontSize = 14.sp, color = ElectricBlue, textAlign = TextAlign.Center)
                }
            }
        }

        // Error state OR progress
        if (uiState.downloadError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SoftRed.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SoftRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = SoftRed, modifier = Modifier.size(32.dp))
                    Text(uiState.downloadError, color = SoftRed, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Button(
                        onClick = { viewModel.retryDownload() },
                        colors = ButtonDefaults.buttonColors(containerColor = SoftRed, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry Download", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Subtle percentage
                Text(
                    "${(uiState.downloadProgress * 100).roundToInt()}%",
                    fontSize = 14.sp,
                    color = MutedGrey
                )

                FinanceProgressBar(progress = uiState.downloadProgress)

                // Speed + ETA — subtle inline row
                DownloadStatRow(speed = uiState.downloadSpeed, eta = uiState.eta)
            }
        }

        // Warning banner
        AnimatedVisibility(visible = uiState.downloadError == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = LuxeGold.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, LuxeGold.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(warningAlpha)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning, null, tint = LuxeGold,
                        modifier = Modifier.size(22.dp).padding(top = 1.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Do not close the app",
                            fontWeight = FontWeight.SemiBold, color = LuxeGold, fontSize = 14.sp
                        )
                        Text(
                            "Your AI model download is in progress. Closing or backgrounding the app " +
                                "will interrupt the download and require restarting it.",
                            color = LuxeGold.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Root screen ───────────────────────────────────────────────────────────────

@Composable
fun InitialSetupScreen(
    viewModel: InitialSetupViewModel,
    onSetupComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.setupComplete) {
        if (uiState.setupComplete) onSetupComplete()
    }

    val isDownloadPhase = uiState.isDownloading || uiState.downloadError != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DeepBlack, Color(0xFF080F1E), Graphite))
            )
    ) {
        FinanceChartBackground(modifier = Modifier.fillMaxSize())

        AnimatedContent(
            targetState = isDownloadPhase,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn(tween(300)))
                    .togetherWith(slideOutHorizontally { -it } + fadeOut(tween(300)))
            },
            label = "setupStep"
        ) { downloading ->
            if (!downloading) {
                ModelSelectionStep(uiState, viewModel)
            } else {
                DownloadProgressStep(uiState, viewModel)
            }
        }
    }
}
