package com.stocksense.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.stocksense.app.R

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null
) {
    object Login : Screen("login", "Login", icon = Icons.Default.Lock)
    object Register : Screen("register", "Register", icon = Icons.Default.PersonAdd)
    object Dashboard : Screen("dashboard", "Dashboard", icon = Icons.Default.Home)
    object Markets : Screen("markets", "Markets", icon = Icons.Default.ShowChart)
    object Watchlist : Screen("watchlist", "Watchlist", icon = Icons.Default.Star)
    object Prediction : Screen("prediction/{symbol}", "Predict", icon = Icons.Default.TrendingUp) {
        fun createRoute(symbol: String) = "prediction/$symbol"
    }
    object Insights : Screen("insights/{symbol}", "Insights", icon = Icons.Default.Lightbulb) {
        fun createRoute(symbol: String) = "insights/$symbol"
    }
    object Alerts : Screen("alerts", "Alerts", icon = Icons.Default.NotificationsActive)
    object Portfolio : Screen("portfolio", "Portfolio", icon = Icons.Default.Work)
    object Chat : Screen("chat", "senseAI", iconRes = R.drawable.ic_app_logo)
    object Profile : Screen("profile", "Profile", icon = Icons.Default.Person)
    object LlmSettings : Screen("llm_settings", "LLM Settings", icon = Icons.Default.Settings)
    object Feedback : Screen("feedback", "Feedback", icon = Icons.Default.Email)
    object SearchResults : Screen("search_results/{query}", "Search", icon = Icons.Default.Search) {
        fun createRoute(query: String) = "search_results/$query"
    }
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Watchlist,
    Screen.Chat,
    Screen.Alerts,
    Screen.Profile
)
