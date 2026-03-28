package com.stocksense.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.stocksense.app.ui.navigation.StockSenseNavGraph
import com.stocksense.app.ui.theme.StockSenseTheme
import com.stocksense.app.viewmodel.*

class MainActivity : ComponentActivity() {

    private val app: StockSenseApp by lazy { application as StockSenseApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dashboardViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                DashboardViewModel(app.stockRepository) as T
        })[DashboardViewModel::class.java]

        val predictionViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                PredictionViewModel(app.stockRepository, app.modelManager) as T
        })[PredictionViewModel::class.java]

        val insightsViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                InsightsViewModel(app.stockRepository, app.modelManager) as T
        })[InsightsViewModel::class.java]

        val alertsViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                AlertsViewModel(app.alertDao, app.alertManager) as T
        })[AlertsViewModel::class.java]

        val profileViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ProfileViewModel(app.userPreferencesManager) as T
        })[ProfileViewModel::class.java]

        val watchlistViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                WatchlistViewModel(app.watchlistDao, app.stockRepository) as T
        })[WatchlistViewModel::class.java]

        val portfolioViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                PortfolioViewModel(app.portfolioHoldingDao, app.tradeDao, app.stockRepository) as T
        })[PortfolioViewModel::class.java]

        val chatViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(app.chatMessageDao, app.modelManager.llmEngine, app.stockRepository) as T
        })[ChatViewModel::class.java]

        setContent {
            StockSenseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StockSenseNavGraph(
                        dashboardViewModel = dashboardViewModel,
                        predictionViewModel = predictionViewModel,
                        insightsViewModel = insightsViewModel,
                        alertsViewModel = alertsViewModel,
                        profileViewModel = profileViewModel,
                        watchlistViewModel = watchlistViewModel,
                        portfolioViewModel = portfolioViewModel,
                        chatViewModel = chatViewModel
                    )
                }
            }
        }
    }
}
