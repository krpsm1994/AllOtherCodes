package com.example.angelonestrategyexecutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.example.angelonestrategyexecutor.data.config.AppConfig
import com.example.angelonestrategyexecutor.data.repository.StockHistoryRepository
import com.example.angelonestrategyexecutor.data.repository.WatchListRepository
import com.example.angelonestrategyexecutor.receiver.LiveWatchListScheduler
import com.example.angelonestrategyexecutor.receiver.LoginAlarmScheduler
import com.example.angelonestrategyexecutor.receiver.WeeklyInstrumentsRefreshScheduler
import com.example.angelonestrategyexecutor.receiver.WatchListScanScheduler
import com.example.angelonestrategyexecutor.service.LiveWatchListStrategyEngine
import com.example.angelonestrategyexecutor.ui.screens.BacktestScreen
import com.example.angelonestrategyexecutor.ui.screens.ConfigScreen
import com.example.angelonestrategyexecutor.ui.screens.LoginScreen
import com.example.angelonestrategyexecutor.ui.screens.StocksScreen
import com.example.angelonestrategyexecutor.ui.screens.WatchListScreen
import com.example.angelonestrategyexecutor.ui.theme.AngelOneStrategyExecutorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize auth state from saved credentials (valid for 24h)
        AuthState.init(this)
        // Initialize app configuration from SharedPreferences
        AppConfig.init(this)
        // Initialize stock history repository
        StockHistoryRepository.init(this)
        // Initialize watchlist cache state
        WatchListRepository.init(this)
        // Schedule daily 8:50 AM login reminder (Mon-Fri)
        LoginAlarmScheduler.schedule(this)
        // Schedule weekly instruments refresh every Friday at 3:30 PM.
        WeeklyInstrumentsRefreshScheduler.schedule(this)
        // Schedule watchlist scanner based on configured mode.
        WatchListScanScheduler.schedule(this)
        // Schedule daily 9:00 AM live watchlist strategy service.
        LiveWatchListScheduler.schedule(this)
        // Load any previously saved live watchlist entries from disk.
        LiveWatchListStrategyEngine.init(this)
        enableEdgeToEdge()
        setContent {
            AngelOneStrategyExecutorTheme {
                AngelOneApp()
            }
        }
    }
}

enum class AppScreen(val label: String, val icon: ImageVector) {
    LOGIN("Login", Icons.Default.Lock),
    STOCKS("Stocks", Icons.Default.TrendingUp),
    WATCHLIST("WatchList", Icons.Default.RemoveRedEye),
    BACKTEST("Backtest", Icons.Default.DateRange),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun AngelOneApp() {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.STOCKS) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            AppScreen.LOGIN -> LoginScreen(contentPadding = innerPadding)
            AppScreen.STOCKS -> StocksScreen(contentPadding = innerPadding)
            AppScreen.WATCHLIST -> WatchListScreen(contentPadding = innerPadding)
            AppScreen.BACKTEST -> BacktestScreen(contentPadding = innerPadding)
            AppScreen.SETTINGS -> ConfigScreen(contentPadding = innerPadding)
        }
    }
}