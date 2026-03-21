package com.stocksense.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = DeepBlack,
    secondary = NeonGreen,
    onSecondary = DeepBlack,
    tertiary = LuxeGold,
    background = DeepBlack,
    surface = Graphite,
    onBackground = Color.White,
    onSurface = Color.White,
    error = SoftRed
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlueDeep,
    onPrimary = Color.White,
    secondary = NeonGreenDeep,
    onSecondary = DeepBlack,
    tertiary = LuxeGold,
    background = Grey100,
    surface = Color.White,
    onBackground = Grey900,
    onSurface = Grey900,
    error = SoftRedDeep
)

@Composable
fun StockSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StockSenseTypography,
        content = content
    )
}
