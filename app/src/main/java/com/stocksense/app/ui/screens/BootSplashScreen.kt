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
// ...existing imports...
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
// ...existing imports...
import androidx.compose.ui.geometry.Offset
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

    // Removed logo animation states
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
    // Removed unused haloScale and haloAlpha variables
    val sweepShift by infiniteTransition.animateFloat(
        initialValue = -0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepShift"
    )

    // Shimmer effect for banner
    val shimmerTransition = rememberInfiniteTransition(label = "bannerShimmer")
    val shimmerX by shimmerTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
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
            // Removed logo. Only banner with shimmer effect remains.
            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .offset(y = bannerOffsetY.dp)
                    .alpha(bannerAlpha)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.stocksense_boot_banner),
                    contentDescription = "StockSense banner",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize()
                )
                // Shimmer overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.32f),
                                    Color.Transparent
                                ),
                                start = Offset(x = shimmerX * 600f, y = 0f),
                                end = Offset(x = (shimmerX + 0.5f) * 600f, y = 0f)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

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
