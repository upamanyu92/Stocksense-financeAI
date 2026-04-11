package com.stocksense.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.R
import com.stocksense.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// ── LLM Token particle data ───────────────────────────────────────────────────
private data class TokenParticle(
    val x: Float, val y: Float, val text: String,
    val speed: Float, val alpha: Float, val size: Float
)

private val TOKEN_STRINGS = listOf(
    "▶", "◀", "0x", "∑", "λ", "∞", "π", "η", "β",
    "ATT", "FFN", "MLP", "MHA", "KV", "Q·K", "V", "W",
    "0.97", "1.0", "0.32", "emb", "tok", "ctx", "seq"
)

private fun generateParticles(count: Int = 38): List<TokenParticle> =
    (0 until count).map {
        TokenParticle(
            x = Random.nextFloat(),
            y = Random.nextFloat(),
            text = TOKEN_STRINGS.random(),
            speed = 0.0015f + Random.nextFloat() * 0.003f,
            alpha = 0.04f + Random.nextFloat() * 0.18f,
            size = 9f + Random.nextFloat() * 10f
        )
    }

@Composable
private fun LlmProcessingBackground(modifier: Modifier = Modifier) {
    val particles = remember { generateParticles() }
    val transition = rememberInfiniteTransition(label = "llmBg")
    val tick by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "tick"
    )
    val scanY by transition.animateFloat(
        initialValue = -0.1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "scanY"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "pulse"
    )

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        // Radial glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ElectricBlue.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(w / 2, h / 2), radius = w * 0.65f
            ),
            radius = w * 0.65f, center = Offset(w / 2, h / 2)
        )
        // Scan line
        val sy = h * scanY
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, ElectricBlue.copy(alpha = 0.35f),
                    NeonGreen.copy(alpha = 0.22f), Color.Transparent)
            ),
            start = Offset(0f, sy), end = Offset(w, sy), strokeWidth = 2.5f
        )
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, ElectricBlue.copy(alpha = 0.10f), Color.Transparent)
            ),
            start = Offset(0f, sy + 12f), end = Offset(w, sy + 12f), strokeWidth = 1f
        )
        // Token particles
        particles.forEach { p ->
            val yPos = ((p.y + tick * p.speed * 1.4f) % 1.1f) * h
            val xPos = p.x * w
            val alphaMod = p.alpha * (0.5f + 0.5f * sin((pulse + p.x * 6f).toDouble()).toFloat())
            drawCircle(color = NeonGreen.copy(alpha = alphaMod * 0.5f), radius = 1.5f, center = Offset(xPos, yPos))
        }
        // Diagonal circuit lines
        for (i in 0..5) {
            val xStart = w * (i / 5f)
            val alpha = 0.04f + 0.03f * abs(sin((pulse + i).toDouble()).toFloat())
            drawLine(color = ElectricBlue.copy(alpha = alpha),
                start = Offset(xStart, 0f), end = Offset(xStart + w * 0.3f, h), strokeWidth = 0.8f)
        }
        // Corner brackets
        val bSize = 28f; val bOff = 20f; val bColor = NeonGreen.copy(alpha = 0.22f); val bStroke = 2.5f
        drawLine(bColor, Offset(bOff, bOff), Offset(bOff + bSize, bOff), strokeWidth = bStroke)
        drawLine(bColor, Offset(bOff, bOff), Offset(bOff, bOff + bSize), strokeWidth = bStroke)
        drawLine(bColor, Offset(w - bOff, bOff), Offset(w - bOff - bSize, bOff), strokeWidth = bStroke)
        drawLine(bColor, Offset(w - bOff, bOff), Offset(w - bOff, bOff + bSize), strokeWidth = bStroke)
        drawLine(bColor, Offset(bOff, h - bOff), Offset(bOff + bSize, h - bOff), strokeWidth = bStroke)
        drawLine(bColor, Offset(bOff, h - bOff), Offset(bOff, h - bOff - bSize), strokeWidth = bStroke)
        drawLine(bColor, Offset(w - bOff, h - bOff), Offset(w - bOff - bSize, h - bOff), strokeWidth = bStroke)
        drawLine(bColor, Offset(w - bOff, h - bOff), Offset(w - bOff, h - bOff - bSize), strokeWidth = bStroke)
    }
}

@Composable
private fun rememberAssetBitmap(name: String): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(name) {
        runCatching { BitmapFactory.decodeStream(ctx.assets.open(name))?.asImageBitmap() }.getOrNull()
    }
}

@Composable
fun BootSplashScreen() {
    var phase by remember { mutableIntStateOf(0) }
    val img0234 = rememberAssetBitmap("IMG_0234.PNG")
    val img0233 = rememberAssetBitmap("IMG_0233.PNG")

    LaunchedEffect(Unit) {
        delay(900L); phase = 1
        delay(800L); phase = 2
    }

    val alpha0234 by animateFloatAsState(
        targetValue = if (phase == 0) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = "alpha0234"
    )
    val alpha0233 by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(700, delayMillis = 200, easing = FastOutSlowInEasing), label = "alpha0233"
    )
    val scale0233 by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 1.12f,
        animationSpec = tween(700, easing = FastOutSlowInEasing), label = "scale0233"
    )
    val glitchOffset by animateFloatAsState(
        targetValue = if (phase == 1) 8f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "glitchOffset"
    )

    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmerTransition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    val glowAlpha by shimmerTransition.animateFloat(
        initialValue = 0.12f, targetValue = 0.38f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )
    val sweepShift by shimmerTransition.animateFloat(
        initialValue = -0.8f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "sweepShift"
    )

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val bannerAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(1100, delayMillis = 350), label = "bannerAlpha"
    )
    val bannerOffsetY by animateFloatAsState(
        targetValue = if (started) 0f else 56f,
        animationSpec = tween(1200, delayMillis = 350, easing = FastOutSlowInEasing), label = "bannerOffsetY"
    )
    val lineScaleX by animateFloatAsState(
        targetValue = if (started) 1f else 0.25f,
        animationSpec = tween(1400, delayMillis = 600, easing = FastOutSlowInEasing), label = "lineScaleX"
    )

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepBlack, Graphite, Onyx)))
    ) {
        // Semi-transparent LLM processing background
        LlmProcessingBackground(modifier = Modifier.fillMaxSize().alpha(0.85f))

        // Sweep beam
        Box(
            modifier = Modifier.fillMaxSize()
                .offset(x = (sweepShift * 220).dp)
                .graphicsLayer { rotationZ = -18f }
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, ElectricBlue.copy(alpha = 0.07f),
                            NeonGreen.copy(alpha = 0.11f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Banner: IMG_0234 → IMG_0233 futuristic morph ─────────────────
            Box(
                modifier = Modifier.fillMaxWidth().height(260.dp)
                    .clip(RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                // IMG_0234 fades out with glitch offset
                if (img0234 != null && alpha0234 > 0.01f) {
                    Image(
                        bitmap = img0234, contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize().alpha(alpha0234).offset(x = glitchOffset.dp)
                    )
                }
                // IMG_0233 morphs in with scale + glow
                if (img0233 != null) {
                    Box(
                        modifier = Modifier.matchParentSize().alpha(alpha0233).scale(scale0233),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = img0233, contentDescription = "SenseQuant",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.matchParentSize()
                        )
                        if (phase >= 2) {
                            Box(
                                modifier = Modifier.matchParentSize().background(
                                    Brush.radialGradient(
                                        colors = listOf(ElectricBlue.copy(alpha = glowAlpha), Color.Transparent)
                                    )
                                )
                            )
                        }
                    }
                }
                // Fallback banner PNG if assets not available
                if (img0234 == null && img0233 == null) {
                    Image(
                        painter = painterResource(id = R.drawable.stocksense_boot_banner),
                        contentDescription = "SenseQuant banner",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize()
                    )
                }
                // Shimmer overlay
                Box(
                    modifier = Modifier.matchParentSize().background(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                            start = Offset(shimmerX * 600f, 0f),
                            end = Offset((shimmerX + 0.4f) * 600f, 0f)
                        )
                    )
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Glowing progress line
            Box(
                modifier = Modifier.fillMaxWidth(0.65f).height(4.dp)
                    .scale(scaleX = lineScaleX, scaleY = 1f)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(ElectricBlue, NeonGreen, ElectricBlue)))
                    .alpha(bannerAlpha)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "QuantSense",
                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.offset(y = bannerOffsetY.dp).alpha(bannerAlpha)
            )
            Text(
                text = "Quantified Intelligence · Precision Wealth",
                color = MutedGrey, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp).alpha(bannerAlpha)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Initializing market intelligence…",
                color = ElectricBlue.copy(alpha = 0.75f), fontSize = 12.sp,
                modifier = Modifier.alpha(bannerAlpha)
            )
        }
    }
}
