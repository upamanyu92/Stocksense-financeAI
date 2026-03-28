package com.stocksense.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stocksense.app.ui.screens.*
import com.stocksense.app.viewmodel.*

@Composable
fun StockSenseNavGraph(
    dashboardViewModel: DashboardViewModel,
    predictionViewModel: PredictionViewModel,
    insightsViewModel: InsightsViewModel,
    alertsViewModel: AlertsViewModel,
    profileViewModel: ProfileViewModel,
    watchlistViewModel: WatchlistViewModel,
    portfolioViewModel: PortfolioViewModel,
    chatViewModel: ChatViewModel,
    authViewModel: AuthViewModel,
    searchViewModel: SearchViewModel,
    llmSettingsViewModel: LlmSettingsViewModel
) {
    val navController = rememberNavController()
    val authState by authViewModel.uiState.collectAsState()

    // Show loading while checking auth state
    if (authState.isCheckingAuth) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (authState.isLoggedIn) Screen.Dashboard.route else Screen.Login.route

    // Screens that show the bottom nav bar
    val authRoutes = setOf(Screen.Login.route, Screen.Register.route)

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // Hide bottom bar on auth screens
            if (currentRoute != null && currentRoute !in authRoutes) {
                NavigationBar {
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
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Auth screens
            composable(Screen.Login.route) {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    }
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.popBackStack()
                    }
                )
            }

            // Main app screens
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    searchViewModel = searchViewModel,
                    onStockClick = { symbol ->
                        navController.navigate(Screen.Prediction.createRoute(symbol))
                    },
                    onProfileClick = {
                        navController.navigate(Screen.Profile.route)
                    },
                    onViewAllSearchResults = { query ->
                        navController.navigate(Screen.SearchResults.createRoute(query))
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
                    onBack = { navController.popBackStack() },
                    onNavigateToLlmSettings = {
                        navController.navigate(Screen.LlmSettings.route)
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.LlmSettings.route) {
                LlmSettingsScreen(
                    viewModel = llmSettingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.SearchResults.route) { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query") ?: ""
                SearchResultsScreen(
                    query = query,
                    viewModel = searchViewModel,
                    onResultClick = { code ->
                        navController.navigate(Screen.Prediction.createRoute(code))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
