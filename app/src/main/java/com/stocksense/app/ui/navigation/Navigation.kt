package com.stocksense.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stocksense.app.ui.screens.AlertsScreen
import com.stocksense.app.ui.screens.ChatScreen
import com.stocksense.app.ui.screens.DashboardScreen
import com.stocksense.app.ui.screens.InsightsScreen
import com.stocksense.app.ui.screens.PortfolioScreen
import com.stocksense.app.ui.screens.PredictionScreen
import com.stocksense.app.ui.screens.ProfileScreen
import com.stocksense.app.ui.screens.WatchlistScreen
import com.stocksense.app.viewmodel.AlertsViewModel
import com.stocksense.app.viewmodel.ChatViewModel
import com.stocksense.app.viewmodel.DashboardViewModel
import com.stocksense.app.viewmodel.InsightsViewModel
import com.stocksense.app.viewmodel.PortfolioViewModel
import com.stocksense.app.viewmodel.PredictionViewModel
import com.stocksense.app.viewmodel.ProfileViewModel
import com.stocksense.app.viewmodel.WatchlistViewModel

@Composable
fun StockSenseNavGraph(
    dashboardViewModel: DashboardViewModel,
    predictionViewModel: PredictionViewModel,
    insightsViewModel: InsightsViewModel,
    alertsViewModel: AlertsViewModel,
    profileViewModel: ProfileViewModel,
    watchlistViewModel: WatchlistViewModel,
    portfolioViewModel: PortfolioViewModel,
    chatViewModel: ChatViewModel
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute != Screen.Chat.route) {
                FloatingActionButton(
                    onClick = { navController.navigate(Screen.Chat.route) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "Chat")
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onStockClick = { symbol ->
                        navController.navigate(Screen.Prediction.createRoute(symbol))
                    },
                    onProfileClick = {
                        navController.navigate(Screen.Profile.route)
                    }
                )
            }
            composable(Screen.Watchlist.route) {
                WatchlistScreen(
                    viewModel = watchlistViewModel,
                    onStockClick = { symbol ->
                        navController.navigate(Screen.Prediction.createRoute(symbol))
                    }
                )
            }
            composable(Screen.Portfolio.route) {
                PortfolioScreen(
                    viewModel = portfolioViewModel,
                    onStockClick = { symbol ->
                        navController.navigate(Screen.Prediction.createRoute(symbol))
                    }
                )
            }
            composable(Screen.Prediction.route) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                PredictionScreen(
                    symbol = symbol,
                    viewModel = predictionViewModel,
                    onShowInsights = { prediction ->
                        insightsViewModel.generateInsight(symbol, prediction)
                        navController.navigate(Screen.Insights.createRoute(symbol))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Insights.route) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                InsightsScreen(
                    symbol = symbol,
                    viewModel = insightsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Alerts.route) {
                AlertsScreen(viewModel = alertsViewModel)
            }
            composable(Screen.Chat.route) {
                ChatScreen(
                    viewModel = chatViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    viewModel = profileViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
