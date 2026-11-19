package com.stocksense.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AuroraPurple,
    onPrimary = DeepBlack,
    primaryContainer = VioletContainer,
    onPrimaryContainer = VioletOnContainer,
    secondary = Color(0xFFCEC0EB),
    onSecondary = DeepBlack,
    tertiary = LuxeGold,
    background = DeepBlack,
    onBackground = Color(0xFFF5F1FF),
    surface = Graphite,
    onSurface = Color(0xFFF5F1FF),
    surfaceVariant = Onyx,
    onSurfaceVariant = MutedGrey,
    outline = Color(0xFF555068),
    error = SoftRed
)

private val LightColorScheme = lightColorScheme(
    primary = VioletDeep,
    onPrimary = Color.White,
    primaryContainer = VioletOnContainer,
    onPrimaryContainer = VioletContainer,
    secondary = Color(0xFF675681),
    tertiary = LuxeGold,
    background = Grey100,
    surface = Color.White,
    onBackground = Grey900,
    onSurface = Grey900,
    error = SoftRedDeep
)

@Composable
fun StockSenseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = StockSenseTypography,
        content = content
    )
}
