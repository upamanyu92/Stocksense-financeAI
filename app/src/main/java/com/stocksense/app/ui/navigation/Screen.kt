package com.stocksense.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Markets : Screen("markets", "Markets", Icons.Default.ShowChart)
    object Watchlist : Screen("watchlist", "Watchlist", Icons.Default.Star)
    object Prediction : Screen("prediction/{symbol}", "Predict", Icons.Default.TrendingUp) {
        fun createRoute(symbol: String) = "prediction/$symbol"
    }
    object Insights : Screen("insights/{symbol}", "Insights", Icons.Default.Lightbulb) {
        fun createRoute(symbol: String) = "insights/$symbol"
    }
    object Alerts : Screen("alerts", "Alerts", Icons.Default.NotificationsActive)
    object Portfolio : Screen("portfolio", "Portfolio", Icons.Default.Work)
    object Chat : Screen("chat", "Chat", Icons.Default.Chat)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Watchlist,
    Screen.Portfolio,
    Screen.Alerts,
    Screen.Profile
)
