package com.stocksense.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksense.app.R
import com.stocksense.app.ui.theme.DeepBlack
import com.stocksense.app.ui.theme.ElectricBlue
import com.stocksense.app.ui.theme.GlassStroke
import com.stocksense.app.ui.theme.Graphite
import com.stocksense.app.ui.theme.MutedGrey
import com.stocksense.app.ui.theme.NeonGreen
import com.stocksense.app.ui.theme.Onyx

@Composable
fun BootSplashScreen() {
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
    }

    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "logoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.72f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "logoScale"
    )
    val bannerAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 1100, delayMillis = 250),
        label = "bannerAlpha"
    )
    val bannerOffsetY by animateFloatAsState(
        targetValue = if (started) 0f else 56f,
        animationSpec = tween(durationMillis = 1200, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "bannerOffsetY"
    )
    val lineScaleX by animateFloatAsState(
        targetValue = if (started) 1f else 0.25f,
        animationSpec = tween(durationMillis = 1400, delayMillis = 500, easing = FastOutSlowInEasing),
        label = "lineScaleX"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "bootFx")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )
    val sweepShift by infiniteTransition.animateFloat(
        initialValue = -0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepShift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepBlack, Graphite, Onyx)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = (sweepShift * 220).dp)
                .graphicsLayer { rotationZ = -18f }
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            ElectricBlue.copy(alpha = 0.08f),
                            NeonGreen.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(170.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(158.dp)
                        .scale(haloScale)
                        .alpha(haloAlpha)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ElectricBlue.copy(alpha = 0.55f),
                                    NeonGreen.copy(alpha = 0.28f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    GlassStroke.copy(alpha = 0.85f),
                                    ElectricBlue.copy(alpha = 0.18f),
                                    NeonGreen.copy(alpha = 0.18f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "StockSense logo",
                        modifier = Modifier
                            .size(104.dp)
                            .alpha(logoAlpha)
                            .scale(logoScale)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Image(
                painter = painterResource(id = R.drawable.stocksense_boot_banner),
                contentDescription = "StockSense banner",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = bannerOffsetY.dp)
                    .alpha(bannerAlpha)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .scale(scaleX = lineScaleX, scaleY = 1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(ElectricBlue, NeonGreen, ElectricBlue)
                        )
                    )
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Initializing market intelligence",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Loading predictive systems…",
                color = MutedGrey,
                fontSize = 13.sp
            )
        }
    }
}
