package com.stocksense.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Prediction : Screen("prediction/{symbol}", "Predict", Icons.Default.TrendingUp) {
        fun createRoute(symbol: String) = "prediction/$symbol"
    }
    object Insights : Screen("insights/{symbol}", "Insights", Icons.Default.Lightbulb) {
        fun createRoute(symbol: String) = "insights/$symbol"
    }
    object Alerts : Screen("alerts", "Alerts", Icons.Default.NotificationsActive)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Alerts
)
