package com.stocksense.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Blue200,
    onPrimary = Grey900,
    secondary = Teal400,
    onSecondary = Grey900,
    background = Grey900,
    surface = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White,
    error = Red400
)

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    secondary = Teal400,
    onSecondary = Grey900,
    background = Grey100,
    surface = Color.White,
    onBackground = Grey900,
    onSurface = Grey900,
    error = Red700
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
