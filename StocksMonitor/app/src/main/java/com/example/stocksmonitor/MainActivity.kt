package com.example.stocksmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TableRow
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.Spinner
import android.widget.ListView
import android.app.AlarmManager
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.example.stocksmonitor.databinding.ActivityMainBinding
import com.example.stocksmonitor.databinding.FragmentLoginBinding
import com.example.stocksmonitor.databinding.FragmentStocksBinding
import com.example.stocksmonitor.databinding.FragmentAddStockBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.Calendar

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle
    private val client = OkHttpClient()
    private lateinit var stockManager: StockManager
    private lateinit var logManager: LogManager
    private var currentStocksAdapter: StockAdapter? = null
    private var currentStocksBinding: FragmentStocksBinding? = null
    private var currentAddStockBinding: FragmentAddStockBinding? = null
    private var localWebSocket: AngelOneWebSocket? = null
    private lateinit var settingsManager: SettingsManager
    
    private val stockUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == StockMonitorService.ACTION_STOCK_UPDATED) {
                Logger.d("MainActivity", "Received stock update broadcast - refreshing UI")
                runOnUiThread {
                    refreshStocksView()
                }
            }
        }
    }
    
    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        
        // Kite Connect credentials (for trading)
        private const val KITE_API_KEY = "7mov9qt27tpmk2ft"
        private const val KITE_SECRET_KEY = "00jheezucvwwxaurf806p5jzp5gqsts3"
        
        // AngelOne SmartAPI credentials (for live data)
        private const val ANGEL_API_KEY = "YOUR_ANGEL_API_KEY"  // Get from AngelOne dashboard
        private const val ANGEL_CLIENT_IP = "192.168.1.1"  // Set to your local IP
        private const val ANGEL_PUBLIC_IP = "YOUR_PUBLIC_IP"  // Set to your public IP
        private const val ANGEL_MAC_ADDRESS = "00:00:00:00:00:00"  // Set to your MAC address
        
        private const val PREFS_NAME = "StocksMonitorPrefs"
        private const val KEY_KITE_ACCESS_TOKEN = "kite_access_token"
        private const val KEY_KITE_REQUEST_TOKEN = "kite_request_token"
        private const val KEY_ANGEL_JWT_TOKEN = "angel_jwt_token"
        private const val KEY_ANGEL_REFRESH_TOKEN = "angel_refresh_token"
        private const val KEY_ANGEL_FEED_TOKEN = "angel_feed_token"
        private const val KEY_ANGEL_CLIENT_ID = "angel_client_id"
        private const val KEY_ANGEL_LAST_LOGIN = "angel_last_login"
        private const val KEY_LAST_INSTRUMENTS_FETCH = "last_instruments_fetch"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        
        // Initialize stock manager
        stockManager = StockManager(this)
        logManager = LogManager(this)
        settingsManager = SettingsManager(this)
        
        // Register broadcast receiver for stock updates
        val filter = IntentFilter(StockMonitorService.ACTION_STOCK_UPDATED)
        registerReceiver(stockUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
        
        // Set callback for stock status changes
        StockMonitorService.onStockStatusChanged = {
            runOnUiThread {
                Logger.d("MainActivity", "Stock status changed callback - refreshing UI")
                refreshStocksView()
            }
        }
        
        // Initialize Logger with debug settings
        Logger.enableDebugLogs = settingsManager.isDebugLogsEnabled()

        // Setup navigation drawer
        toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)

        // Setup back press handling
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // Navigate back to stocks page if on another page
                    val currentTitle = supportActionBar?.title.toString()
                    if (currentTitle == getString(R.string.nav_stocks)) {
                        // On stocks page, exit app
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    } else {
                        // On other pages, go back to stocks
                        showStocksPage()
                        binding.navView.setCheckedItem(R.id.nav_stocks)
                    }
                }
            }
        })

        // Load default page (Stocks)
        if (savedInstanceState == null) {
            showStocksPage()
            binding.navView.setCheckedItem(R.id.nav_stocks)
        }

        binding.fab.setOnClickListener { view ->
            showAddStockPage()
        }
        
        // Request notification permission and schedule daily login reminder
        requestNotificationPermission()
        
        // Initialize WebSocket if there are stocks to monitor
        val allStocks = stockManager.getAllStocks()
        if (allStocks.isNotEmpty()) {
            Logger.d("MainActivity", "onCreate() - Found ${allStocks.size} stocks, initializing WebSocket")
            initializeWebSocket()
        } else {
            Logger.d("MainActivity", "onCreate() - No stocks found, WebSocket will start when first stock is added")
        }
        
        // Resume order tracking for any stocks in ORDER_PLACED status
        resumeOrderTracking()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_login -> showLoginPage()
            R.id.nav_stocks -> showStocksPage()
            R.id.nav_all_exchanges_stocks -> showAllExchangesStocksPage()
            R.id.nav_logs -> showLogsPage()
            R.id.nav_settings -> showSettingsPage()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_portfolio -> {
                showPortfolioPage()
                true
            }
            R.id.action_settings -> {
                showSettingsPage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh stocks view when app comes to foreground
        Logger.d("MainActivity", "onResume() - Refreshing stocks view")
        refreshStocksView()
    }

    private fun showLoginPage() {
        currentStocksBinding = null  // Clear reference when leaving stocks page
        val loginBinding = FragmentLoginBinding.inflate(LayoutInflater.from(this))
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(loginBinding.root)
        supportActionBar?.title = getString(R.string.nav_login)

        // Check if tokens already exist
        val kite_existingAccessToken = getKiteAccessToken()
        val angel_existingJwtToken = getAngelJwtToken()
        
        if (kite_existingAccessToken != null || angel_existingJwtToken != null) {
            loginBinding.existingTokenContainer.visibility = View.VISIBLE
            loginBinding.existingAccessToken.text = kite_existingAccessToken ?: "Not logged in"
            
            // Show last login date/time for AngelOne
            val lastLoginTime = getAngelLastLoginTime()
            if (lastLoginTime > 0) {
                val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", java.util.Locale.getDefault())
                loginBinding.existingAngelToken.text = "Last login: ${dateFormat.format(java.util.Date(lastLoginTime))}"
            } else {
                loginBinding.existingAngelToken.text = "Not logged in"
            }
        } else {
            loginBinding.existingTokenContainer.visibility = View.GONE
        }

        // Configure Kite WebView
        loginBinding.kiteWebview.settings.javaScriptEnabled = true
        loginBinding.kiteWebview.settings.domStorageEnabled = true
        
        loginBinding.kiteWebview.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                
                url?.let {
                    if (it.startsWith("https://kite.trade/?request_token=") || it.contains("request_token=")) {
                        val uri = android.net.Uri.parse(it)
                        val kite_requestToken = uri.getQueryParameter("request_token")
                        
                        if (kite_requestToken != null) {
                            view?.stopLoading()
                            saveKiteRequestToken(kite_requestToken)
                            loginBinding.kiteWebview.visibility = View.GONE
                            loginBinding.loginPrompt.visibility = View.GONE
                            getKiteAccessToken(kite_requestToken, loginBinding)
                        }
                    }
                }
            }
        }

        // Kite login button
        loginBinding.kiteLoginButton.setOnClickListener {
            loginBinding.loginPrompt.visibility = View.GONE
            loginBinding.kiteWebview.visibility = View.VISIBLE
            loginBinding.kiteWebview.loadUrl("https://kite.zerodha.com/connect/login?v=3&api_key=$KITE_API_KEY")
        }
        
        // AngelOne login button
        loginBinding.angelLoginButton.setOnClickListener {
            loginBinding.loginPrompt.visibility = View.GONE
            loginBinding.angelLoginForm.visibility = View.VISIBLE
        }
        
        // AngelOne login form submit
        loginBinding.angelSubmitButton.setOnClickListener {
            val angel_clientId = loginBinding.angelClientId.text.toString()
            val angel_password = loginBinding.angelPassword.text.toString()
            val angel_totp = loginBinding.angelTotp.text.toString()
            
            if (angel_clientId.isEmpty() || angel_password.isEmpty() || angel_totp.isEmpty()) {
                Snackbar.make(binding.root, "Please fill all fields", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            loginWithAngelOne(angel_clientId, angel_password, angel_totp, loginBinding)
        }
        
        // AngelOne cancel button
        loginBinding.angelCancelButton.setOnClickListener {
            loginBinding.angelLoginForm.visibility = View.GONE
            loginBinding.loginPrompt.visibility = View.VISIBLE
        }
    }

    private fun showStocksPage() {
        val stocksBinding = FragmentStocksBinding.inflate(LayoutInflater.from(this))
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(stocksBinding.root)
        supportActionBar?.title = getString(R.string.nav_stocks)
        
        // Store reference for live updates
        currentStocksBinding = stocksBinding
        
        // Load stocks from file
        val allStocks = stockManager.getAllStocks()
        
        // Separate active and history stocks
        val activeStocks = allStocks.filter { it.status != StockStatus.HISTORY }
        val historyStocks = allStocks.filter { it.status == StockStatus.HISTORY }
        
        // Setup ViewPager2 adapter
        val adapter = StocksTabAdapter(
            activeStocks = activeStocks,
            historyStocks = historyStocks,
            onStockClick = { stock -> showStockOptionsDialog(stock) },
            onOrdersTabSelected = { fetchAndDisplayOrders() },
            onOrderClick = { order -> showOrderDetailsDialog(order) },
            quotesCache = StockMonitorService.quotesCache
        )
        stocksBinding.stocksViewPager.adapter = adapter
        
        // Setup TabLayout with ViewPager2 using TabLayoutMediator
        com.google.android.material.tabs.TabLayoutMediator(
            stocksBinding.stocksTabLayout,
            stocksBinding.stocksViewPager
        ) { tab, position ->
            tab.text = when (position) {
                0 -> "Stocks"
                1 -> "Orders"
                2 -> "History"
                else -> ""
            }
        }.attach()
        
        // Add page change listener to fetch orders when Orders tab is selected
        var isLoadingOrders = false
        var ordersAutoRefreshHandler: android.os.Handler? = null
        val ordersAutoRefreshRunnable = object : Runnable {
            override fun run() {
                if (stocksBinding.stocksViewPager.currentItem == 1) {
                    // Still on Orders tab, fetch fresh data
                    fetchAndDisplayOrders()
                    // Schedule next refresh in 20 seconds
                    ordersAutoRefreshHandler?.postDelayed(this, 20000)
                }
            }
        }
        
        stocksBinding.stocksViewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == 1 && !isLoadingOrders) {
                    // Orders tab selected, fetch orders
                    isLoadingOrders = true
                    if (ordersAutoRefreshHandler == null) {
                        ordersAutoRefreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    }
                    fetchAndDisplayOrders()
                    // Schedule auto-refresh every 20 seconds
                    ordersAutoRefreshHandler?.postDelayed(ordersAutoRefreshRunnable, 20000)
                    // Reset loading flag after a short delay to allow new fetches on next navigation
                    stocksBinding.root.postDelayed({ isLoadingOrders = false }, 1000)
                } else if (position != 1) {
                    // Left Orders tab, stop auto-refresh
                    ordersAutoRefreshHandler?.removeCallbacks(ordersAutoRefreshRunnable)
                }
            }
        })
        
        // Setup quote update listener to refresh adapter and monitor status when quotes arrive
        StockMonitorService.onQuoteUpdateListener = { token, quote ->
            // Post to main thread to avoid threading issues
            runOnUiThread {
                // IMPORTANT: Get fresh stocks from manager each time, not the cached snapshot
                // This ensures we're checking against the latest status (especially ORDER_PLACED)
                val currentStocks = stockManager.getAllStocks()
                
                // Monitor and update stock statuses
                for (stock in currentStocks) {
                    if (stock.instrument.instrumentToken == token) {
                        checkAndUpdateStockStatus(stock, quote)
                    }
                }
                // Refresh the adapter
                stocksBinding.stocksViewPager.adapter?.notifyDataSetChanged()
            }
        }
        
        // Ensure service/WebSocket is subscribed to current stocks
        notifyServiceToResubscribe()
    }
    
    private fun fetchAndDisplayOrders() {
        currentStocksBinding?.let { stocksBinding ->
            val adapter = stocksBinding.stocksViewPager.adapter as? StocksTabAdapter
            
            if (adapter != null) {
                fetchKiteOrders { orders, error ->
                    runOnUiThread {
                        adapter.updateOrders(orders, error)
                    }
                }
            }
        }
    }

    private fun showAddStockPage() {
        currentStocksBinding = null  // Clear reference when leaving stocks page
        val addStockBinding = FragmentAddStockBinding.inflate(LayoutInflater.from(this))
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(addStockBinding.root)
        currentAddStockBinding = addStockBinding
        supportActionBar?.title = "Add Stock"
        
        // Get equity instruments
        val instruments = stockManager.getEquityInstruments()
        val displayNames = instruments.map {
            if (it.exchange == "NSE") {
                "${it.name} : ${it.exchange}"
            } else if (it.exchange == "BSE") {
                "${it.tradingSymbol} : ${it.exchange}"
            } else {
                // Fallback for other exchanges
                if (it.name.isNotEmpty()) "${it.name} : ${it.exchange}" else "${it.tradingSymbol} : ${it.exchange}"
            }
        }.toTypedArray()
        
        // Setup stock name dropdown with search
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayNames)
        addStockBinding.stockNameInput.setAdapter(adapter)
        addStockBinding.stockNameInput.threshold = 1
        
        // Setup status dropdown
        val statusValues = StockStatus.values().map { it.name.replace("_", " ") }.toTypedArray()
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusValues)
        addStockBinding.statusSpinner.setAdapter(statusAdapter)
        addStockBinding.statusSpinner.setText(StockStatus.NOT_TRIGGERED.name.replace("_", " "), false)
        
        // Update amount required when quantity or buy price changes
        val updateAmountRequired = {
            val quantity = addStockBinding.quantityInput.text.toString().toIntOrNull() ?: settingsManager.getDefaultQuantity()
            val buyPrice = addStockBinding.buyPriceInput.text.toString().toDoubleOrNull() ?: 0.0
            val brokerageCharges = settingsManager.getBrokerageCharges()
            val amountRequired = (buyPrice * quantity) + brokerageCharges
            addStockBinding.amountRequiredText.text = "Amount Required: ₹${String.format("%.2f", amountRequired)}"
        }
        
        addStockBinding.quantityInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateAmountRequired() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        addStockBinding.buyPriceInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateAmountRequired() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // Save button click
        addStockBinding.saveButton.setOnClickListener {
            val selectedText = addStockBinding.stockNameInput.text.toString()
            val quantity = addStockBinding.quantityInput.text.toString()
            val buyPrice = addStockBinding.buyPriceInput.text.toString()
            val stopLoss = addStockBinding.stoplossInput.text.toString()
            val target = addStockBinding.targetInput.text.toString()
            
            if (selectedText.isEmpty() || quantity.isEmpty() || buyPrice.isEmpty() || stopLoss.isEmpty() || target.isEmpty()) {
                Snackbar.make(binding.root, "Please fill all fields", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Find the selected instrument
            val selectedIndex = displayNames.indexOfFirst { it.equals(selectedText, ignoreCase = true) }
            if (selectedIndex == -1) {
                Snackbar.make(binding.root, "Please select a valid stock from the dropdown list", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            try {
                showLoadingSpinner(addStockBinding, true)
                
                // Get selected status
                val selectedStatusText = addStockBinding.statusSpinner.text.toString().replace(" ", "_")
                val selectedStatus = try {
                    StockStatus.valueOf(selectedStatusText)
                } catch (e: Exception) {
                    StockStatus.NOT_TRIGGERED
                }
                
                val stock = Stock(
                    instrument = instruments[selectedIndex],
                    buyPrice = buyPrice.toDouble(),
                    stopLoss = stopLoss.toDouble(),
                    target = target.toDouble(),
                    quantity = quantity.toInt(),
                    onlyWatch = addStockBinding.onlyWatchCheckbox.isChecked,
                    status = selectedStatus
                )
                
                // Get all stocks and filter to only active ones (not in history)
                val allStocks = stockManager.getAllStocks()
                val activeStocks = allStocks.filter { it.status != StockStatus.HISTORY }
                
                // Check if stock already exists in ACTIVE stocks only (by instrument token)
                val alreadyExists = activeStocks.any { 
                    it.instrument.instrumentToken == stock.instrument.instrumentToken 
                }
                
                if (alreadyExists) {
                    showLoadingSpinner(addStockBinding, false)
                    Snackbar.make(binding.root, "This stock is already in your active list", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                val isFirstStock = allStocks.isEmpty()
                
                stockManager.saveStock(stock)
                logManager.addLog(
                    LogType.STOCK_ADDED,
                    stock.instrument.tradingSymbol,
                    "Qty: ${stock.quantity}, Buy: ${stock.buyPrice}, SL: ${stock.stopLoss}, Target: ${stock.target}"
                )
                
                // Start WebSocket if this is the first stock, otherwise just resubscribe
                if (isFirstStock) {
                    initializeWebSocket()
                } else {
                    resubscribeToStocks()
                }
                
                showLoadingSpinner(addStockBinding, false)
                Snackbar.make(binding.root, "Stock saved successfully!", Snackbar.LENGTH_SHORT).show()
                
                // Navigate back to stocks page
                showStocksPage()
                binding.navView.setCheckedItem(R.id.nav_stocks)
            } catch (e: NumberFormatException) {
                showLoadingSpinner(addStockBinding, false)
                Snackbar.make(binding.root, "Invalid number format", Snackbar.LENGTH_SHORT).show()
            }
        }
        
        // Refresh instruments button click
        addStockBinding.refreshInstrumentsButton.setOnClickListener {
            showLoadingSpinner(addStockBinding, true)
            Snackbar.make(binding.root, "Updating stock list...", Snackbar.LENGTH_SHORT).show()
            fetchAngelInstruments()
            // Loading spinner will be hidden after fetch completes
        }
        
        // Cancel button click
        addStockBinding.cancelButton.setOnClickListener {
            showStocksPage()
            binding.navView.setCheckedItem(R.id.nav_stocks)
        }
    }

    private fun showLogsPage() {
        val logsBinding = android.view.LayoutInflater.from(this)
            .inflate(R.layout.fragment_logs, null) as android.widget.LinearLayout
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(logsBinding)
        supportActionBar?.title = "Activity Logs"

        val logsList = logsBinding.findViewById<android.widget.ListView>(R.id.logs_list_view)
        val logsCountText = logsBinding.findViewById<TextView>(R.id.logs_count)
        val clearButton = logsBinding.findViewById<Button>(R.id.clear_logs_button)
        val refreshButton = logsBinding.findViewById<Button>(R.id.refresh_logs_button)

        fun updateLogsList() {
            val logs = logManager.getLogs()
            logsCountText.text = "Total Logs: ${logs.size}"
            
            if (logs.isEmpty()) {
                logsList.adapter = null
            } else {
                val adapter = LogAdapter(this@MainActivity, logs)
                logsList.adapter = adapter
            }
        }

        updateLogsList()

        clearButton.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to clear all logs?")
                .setPositiveButton("Clear") { _, _ ->
                    logManager.clearLogs()
                    updateLogsList()
                    Snackbar.make(binding.root, "Logs cleared", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        refreshButton.setOnClickListener {
            updateLogsList()
            Snackbar.make(binding.root, "Logs refreshed", Snackbar.LENGTH_SHORT).show()
        }
    }



    private fun getKiteAccessToken(kite_requestToken: String, loginBinding: FragmentLoginBinding) {
        Logger.d("MainActivity", "getKiteAccessToken() - Starting token exchange")
        Logger.d("MainActivity", "Kite Request token: $kite_requestToken")
        
        // Calculate checksum: SHA256(api_key + request_token + secret_key)
        val checksumInput = KITE_API_KEY + kite_requestToken + KITE_SECRET_KEY
        val checksum = calculateSHA256(checksumInput)
        Logger.d("MainActivity", "Checksum calculated: $checksum")

        // Prepare request body
        val formBody = FormBody.Builder()
            .add("api_key", KITE_API_KEY)
            .add("request_token", kite_requestToken)
            .add("checksum", checksum)
            .build()

        val request = Request.Builder()
            .url("https://api.kite.trade/session/token")
            .addHeader("X-Kite-Version", "3")
            .post(formBody)
            .build()
        
        Logger.d("MainActivity", "Sending POST request to: ${request.url}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e("MainActivity", "getKiteAccessToken() - API call failed", e)
                runOnUiThread {
                    loginBinding.tokenContainer.visibility = View.VISIBLE
                    loginBinding.apiResponseText.text = "Kite Error: ${e.message}"
                    Snackbar.make(binding.root, "Failed to get Kite access token", Snackbar.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Logger.d("MainActivity", "getKiteAccessToken() - Response code: ${response.code}")
                Logger.d("MainActivity", "getKiteAccessToken() - Response body: $responseBody")
                
                runOnUiThread {
                    loginBinding.tokenContainer.visibility = View.VISIBLE
                    loginBinding.apiResponseText.text = "Kite Response:\n$responseBody"
                    
                    try {
                        val accessTokenMatch = "\"access_token\":\"([^\"]+)\"".toRegex().find(responseBody)
                        val kite_accessToken = accessTokenMatch?.groupValues?.get(1)
                        
                        if (kite_accessToken != null) {
                            Logger.d("MainActivity", "getKiteAccessToken() - Token extracted successfully")
                            loginBinding.accessTokenText.text = "Kite: $kite_accessToken"
                            saveKiteAccessToken(kite_accessToken)
                            Snackbar.make(binding.root, "Kite login successful!", Snackbar.LENGTH_LONG).show()
                            
                            // Update existing token display
                            loginBinding.existingTokenContainer.visibility = View.VISIBLE
                            loginBinding.existingAccessToken.text = kite_accessToken
                        } else {
                            Logger.w("MainActivity", "getKiteAccessToken() - Failed to extract access token")
                            loginBinding.accessTokenText.text = "Failed to extract Kite access token"
                        }
                    } catch (e: Exception) {
                        Logger.e("MainActivity", "getKiteAccessToken() - Error parsing response", e)
                        loginBinding.accessTokenText.text = "Error parsing Kite response"
                    }
                }
            }
        })
    }
    
    private fun showOrderDetailsDialog(order: Order) {
        val details = buildString {
            appendLine("Order ID: ${order.orderId}")
            appendLine("Exchange Order ID: ${order.exchangeOrderId}")
            if (order.parentOrderId.isNotEmpty()) appendLine("Parent Order ID: ${order.parentOrderId}")
            appendLine("\nStock: ${order.tradingSymbol}")
            appendLine("Exchange: ${order.exchange}")
            if (order.instrumentToken.isNotEmpty()) appendLine("Instrument Token: ${order.instrumentToken}")
            appendLine("\nType: ${order.transactionType} ${order.orderType}")
            appendLine("Variety: ${order.variety}")
            appendLine("Product: ${order.product}")
            appendLine("Validity: ${order.validity}")
            appendLine("\nStatus: ${order.status}")
            if (order.statusMessage.isNotEmpty()) appendLine("Status Message: ${order.statusMessage}")
            appendLine("\nQuantity: ${order.quantity}")
            appendLine("Filled: ${order.filledQuantity}")
            appendLine("Pending: ${order.pendingQuantity}")
            if (order.cancelledQuantity > 0) appendLine("Cancelled: ${order.cancelledQuantity}")
            if (order.disclosedQuantity > 0) appendLine("Disclosed: ${order.disclosedQuantity}")
            appendLine("\nPrice: ₹${order.price}")
            if (order.triggerPrice > 0) appendLine("Trigger Price: ₹${order.triggerPrice}")
            if (order.averagePrice > 0) appendLine("Average Price: ₹${order.averagePrice}")
            appendLine("\nOrder Time: ${order.orderTimestamp}")
            if (order.exchangeTimestamp.isNotEmpty()) appendLine("Exchange Time: ${order.exchangeTimestamp}")
            if (order.exchangeUpdateTimestamp.isNotEmpty()) appendLine("Last Update: ${order.exchangeUpdateTimestamp}")
            appendLine("\nPlaced By: ${order.placedBy}")
            if (order.tag.isNotEmpty()) appendLine("Tag: ${order.tag}")
            if (order.guid.isNotEmpty()) appendLine("GUID: ${order.guid}")
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Order Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun fetchKiteOrders(callback: (List<Order>, String?) -> Unit) {
        val accessToken = getKiteAccessToken()
        
        if (accessToken == null) {
            callback(emptyList(), "Not logged in to Kite. Please login first.")
            return
        }
        
        Logger.d("MainActivity", "fetchKiteOrders() - Fetching orders from Kite API")
        
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.kite.trade/orders")
            .addHeader("Authorization", "token $KITE_API_KEY:$accessToken")
            .addHeader("X-Kite-Version", "3")
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e("MainActivity", "fetchKiteOrders() - API call failed", e)
                callback(emptyList(), "Network error: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Logger.d("MainActivity", "fetchKiteOrders() - Response code: ${response.code}")
                Logger.d("MainActivity", "fetchKiteOrders() - Response body: ${responseBody.take(200)}...")
                
                try {
                    if (response.code == 200) {
                        val jsonResponse = org.json.JSONObject(responseBody)
                        val status = jsonResponse.optString("status")
                        
                        if (status == "success") {
                            val dataArray = jsonResponse.optJSONArray("data")
                            val orders = mutableListOf<Order>()
                            
                            if (dataArray != null) {
                                for (i in 0 until dataArray.length()) {
                                    val orderJson = dataArray.getJSONObject(i)
                                    orders.add(Order.fromJson(orderJson))
                                }
                            }
                            
                            // Sort orders by most recent first (using exchange_update_timestamp or order_timestamp)
                            val sortedOrders = orders.sortedByDescending { order ->
                                val timestamp = if (order.exchangeUpdateTimestamp.isNotEmpty()) {
                                    order.exchangeUpdateTimestamp
                                } else {
                                    order.orderTimestamp
                                }
                                timestamp
                            }
                            
                            Logger.d("MainActivity", "fetchKiteOrders() - Successfully fetched ${sortedOrders.size} orders")
                            
                            // Check and update ORDER_PLACED stocks
                            checkAndUpdateOrderPlacedStocks(sortedOrders)
                            
                            callback(sortedOrders, null)
                        } else {
                            val message = jsonResponse.optString("message", "Unknown error")
                            Logger.w("MainActivity", "fetchKiteOrders() - API returned error: $message")
                            callback(emptyList(), message)
                        }
                    } else if (response.code == 403) {
                        Logger.w("MainActivity", "fetchKiteOrders() - Unauthorized (403)")
                        callback(emptyList(), "Session expired. Please login again.")
                    } else {
                        Logger.w("MainActivity", "fetchKiteOrders() - Unexpected response code: ${response.code}")
                        callback(emptyList(), "Error: HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    Logger.e("MainActivity", "fetchKiteOrders() - Error parsing response", e)
                    callback(emptyList(), "Error parsing response: ${e.message}")
                }
            }
        })
    }

    private fun calculateSHA256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun initializeWebSocket() {
        Logger.d("MainActivity", "initializeWebSocket() - Initializing AngelOne WebSocket")
        
        val angel_apiKey = ANGEL_API_KEY
        val angel_clientCode = getAngelClientId()
        val angel_jwtToken = getAngelJwtToken()
        val angel_feedToken = getAngelFeedToken()
        
        if (angel_apiKey.isBlank() || angel_clientCode == null || angel_jwtToken == null || angel_feedToken == null) {
            Logger.e("MainActivity", "initializeWebSocket() - AngelOne credentials missing!")
            Snackbar.make(binding.root, "Please login to AngelOne first", Snackbar.LENGTH_LONG).show()
            return
        }
        
        // Get Kite credentials for background order placement
        val kite_apiKey = KITE_API_KEY
        val kite_accessToken = getKiteAccessToken() ?: ""
        
        try {
            // Try to start foreground service for WebSocket
            val intent = Intent(this, StockMonitorService::class.java).apply {
                action = StockMonitorService.ACTION_START
                putExtra(StockMonitorService.EXTRA_ANGEL_API_KEY, angel_apiKey)
                putExtra(StockMonitorService.EXTRA_ANGEL_CLIENT_CODE, angel_clientCode)
                putExtra(StockMonitorService.EXTRA_ANGEL_JWT_TOKEN, angel_jwtToken)
                putExtra(StockMonitorService.EXTRA_ANGEL_FEED_TOKEN, angel_feedToken)
                putExtra(StockMonitorService.EXTRA_KITE_API_KEY, kite_apiKey)
                putExtra(StockMonitorService.EXTRA_KITE_ACCESS_TOKEN, kite_accessToken)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Logger.d("MainActivity", "initializeWebSocket() - Starting foreground service")
                startForegroundService(intent)
            } else {
                Logger.d("MainActivity", "initializeWebSocket() - Starting service")
                startService(intent)
            }
            Logger.d("MainActivity", "initializeWebSocket() - Service started successfully")
            
            // Set up listener for quote updates
            StockMonitorService.onQuoteUpdateListener = { token, quote ->
                runOnUiThread {
                    // Update ViewPager adapter if on stocks page
                    currentStocksBinding?.let { stocksBinding ->
                        stocksBinding.stocksViewPager.adapter?.notifyDataSetChanged()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("MainActivity", "Failed to start service, using local WebSocket", e)
            // Fallback to local WebSocket
            startLocalWebSocket(angel_apiKey, angel_clientCode, angel_jwtToken, angel_feedToken)
        }
    }

    private fun startLocalWebSocket(angel_apiKey: String, angel_clientCode: String, 
                                    angel_jwtToken: String, angel_feedToken: String) {
        Logger.d("MainActivity", "startLocalWebSocket() - Starting local AngelOne WebSocket fallback")
        localWebSocket?.disconnect()
        
        localWebSocket = AngelOneWebSocket(angel_apiKey, angel_clientCode, angel_jwtToken, angel_feedToken) { token, quote ->
            StockMonitorService.quotesCache[token] = quote
            runOnUiThread {
                currentStocksAdapter?.notifyDataSetChanged()
            }
        }
        
        // Set error callback
        localWebSocket?.onError = { error ->
            runOnUiThread {
                if (error.contains("401") || error.contains("403")) {
                    Snackbar.make(binding.root, "AngelOne authentication failed. Please login again.", Snackbar.LENGTH_LONG)
                        .setAction("Login") {
                            binding.navView.setCheckedItem(R.id.nav_login)
                            showLoginPage()
                        }
                        .show()
                } else {
                    Snackbar.make(binding.root, "WebSocket error: $error", Snackbar.LENGTH_LONG).show()
                }
            }
        }
        
        localWebSocket?.connect()
        Logger.d("MainActivity", "startLocalWebSocket() - WebSocket connection initiated")
        
        // Subscribe to saved stocks
        android.os.Handler(mainLooper).postDelayed({
            val stocks = stockManager.getAllStocks()
            val instrumentTokens = stocks.map { it.instrument.instrumentToken }
            if (instrumentTokens.isNotEmpty()) {
                Logger.d("MainActivity", "startLocalWebSocket() - Subscribing to ${instrumentTokens.size} stocks")
                localWebSocket?.subscribe(instrumentTokens)
            } else {
                Logger.d("MainActivity", "startLocalWebSocket() - No stocks to subscribe to")
            }
        }, 2000)
    }

    private fun resubscribeToStocks() {
        Logger.d("MainActivity", "resubscribeToStocks() - Resubscribing to updated stock list")
        val stocks = stockManager.getAllStocks()
        
        // Send ACTION_RESUBSCRIBE to ensure proper unsubscription
        // Use startService() since the service is already running as foreground
        val intent = Intent(this, StockMonitorService::class.java).apply {
            action = StockMonitorService.ACTION_RESUBSCRIBE
        }
        
        try {
            startService(intent)
            Logger.d("MainActivity", "resubscribeToStocks() - Sent ACTION_RESUBSCRIBE to service. Active stocks: ${stocks.filter { it.status != StockStatus.HISTORY }.size}, Total: ${stocks.size}")
        } catch (e: Exception) {
            Logger.e("MainActivity", "Failed to send resubscribe action to service", e)
        }
    }
    
    private fun notifyServiceToResubscribe() {
        // Called when showing stocks page - ensure subscriptions are correct
        resubscribeToStocks()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister broadcast receiver
        try {
            unregisterReceiver(stockUpdateReceiver)
        } catch (e: Exception) {
            Logger.e("MainActivity", "Error unregistering receiver: ${e.message}")
        }
        
        // Don't stop the service on destroy - it should keep running
        StockMonitorService.onQuoteUpdateListener = null
        StockMonitorService.onStockStatusChanged = null
        
        // Clean up local WebSocket if using it
        localWebSocket?.disconnect()
    }
    
    // ============= KITE-SPECIFIC METHODS =============
    
    private fun saveKiteRequestToken(token: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KITE_REQUEST_TOKEN, token)
            .apply()
    }

    private fun saveKiteAccessToken(token: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KITE_ACCESS_TOKEN, token)
            .apply()
    }

    fun getKiteAccessToken(): String? {
        val token = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_KITE_ACCESS_TOKEN, null)
        Logger.d("MainActivity", "getKiteAccessToken() - Retrieved token: ${if (token != null) "${token.take(10)}... (length: ${token.length})" else "null"}")
        return token
    }

    private fun fetchInstrumentsIfNeeded() {
        val lastFetchTime = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_INSTRUMENTS_FETCH, 0L)
        
        val currentTime = System.currentTimeMillis()
        val oneMonthInMillis = 30L * 24 * 60 * 60 * 1000 // 30 days
        
        // Check if instruments were never fetched or last fetch was more than a month ago
        if (lastFetchTime == 0L || (currentTime - lastFetchTime) > oneMonthInMillis) {
            Logger.d("MainActivity", "Instruments fetch needed (last fetch: $lastFetchTime)")
            fetchAngelInstruments()
        } else {
            Logger.d("MainActivity", "Instruments are up to date (last fetch: $lastFetchTime)")
        }
    }

    private fun fetchAngelInstruments() {
        Logger.d("MainActivity", "fetchAngelInstruments() - Starting instrument download from AngelOne")
        
        val request = Request.Builder()
            .url("https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json")
            .build()
        
        Logger.d("MainActivity", "fetchAngelInstruments() - Sending GET request")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e("MainActivity", "fetchAngelInstruments() - API call failed", e)
                runOnUiThread {
                    Snackbar.make(binding.root, "Failed to fetch instruments: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                Logger.d("MainActivity", "fetchAngelInstruments() - Response code: ${response.code}")
                try {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val instruments = mutableListOf<Instrument>()
                        val allInstruments = mutableListOf<Instrument>()
                        
                        // Parse JSON array
                        val jsonArray = responseBody.trim()
                        if (jsonArray.startsWith("[")) {
                            // Remove outer brackets and split by },{
                            val items = jsonArray.substring(1, jsonArray.length - 1)
                                .split("},{")
                            
                            for (item in items) {
                                val jsonItem = if (!item.startsWith("{")) "{$item" else item
                                val finalItem = if (!jsonItem.endsWith("}")) "$jsonItem}" else jsonItem
                                
                                val instrument = Instrument.fromAngelJson(finalItem)
                                if (instrument != null) {
                                    // Add all instruments to allInstruments
                                    allInstruments.add(instrument)
                                    
                                    // Filter for Add Stock screen: 
                                    // NSE: -EQ suffix + empty expiry
                                    // BSE: No expiry filter, accept all BSE instruments
                                    if (instrument.exchange == "NSE") {
                                        if (instrument.tradingSymbol.endsWith("-EQ") && instrument.expiry.isEmpty()) {
                                            instruments.add(instrument)
                                        }
                                    } else if (instrument.exchange == "BSE"  && instrument.expiry.isEmpty()) {
                                        // Add all BSE instruments
                                        instruments.add(instrument)
                                    }
                                }
                            }
                        }
                        
                        Logger.d("MainActivity", "fetchAngelInstruments() - Parsed ${instruments.size} -EQ instruments and ${allInstruments.size} total instruments")
                        
                        if (instruments.isNotEmpty()) {
                            stockManager.saveInstruments(instruments)
                            
                            // Save all unfiltered instruments to separate file
                            if (allInstruments.isNotEmpty()) {
                                stockManager.saveAllExchangesStocks(allInstruments)
                                Logger.d("MainActivity", "fetchAngelInstruments() - Saved ${allInstruments.size} instruments to All_exchanges_stocks.txt")
                            }
                            
                            // Save fetch timestamp
                            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putLong(KEY_LAST_INSTRUMENTS_FETCH, System.currentTimeMillis())
                                .apply()
                            
                            runOnUiThread {
                                currentAddStockBinding?.let { showLoadingSpinner(it, false) }
                                Snackbar.make(binding.root, "Loaded ${instruments.size} Stocks from NSE", Snackbar.LENGTH_SHORT).show()
                                // Refresh add stock page if currently visible
                                if (supportActionBar?.title == "Add Stock") {
                                    showAddStockPage()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("MainActivity", "fetchAngelInstruments() - Error parsing", e)
                    runOnUiThread {
                        currentAddStockBinding?.let { showLoadingSpinner(it, false) }
                        Snackbar.make(binding.root, "Error parsing Stocks from AngelOne", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
    
    // ============= ANGELONE-SPECIFIC METHODS =============
    
    private fun loginWithAngelOne(angel_clientId: String, angel_password: String, angel_totp: String, loginBinding: FragmentLoginBinding) {
        Logger.d("MainActivity", "loginWithAngelOne() - Starting AngelOne login")
        Logger.d("MainActivity", "Client ID: $angel_clientId")
        
        val jsonBody = """
            {
                "clientcode": "$angel_clientId",
                "password": "$angel_password",
                "totp": "$angel_totp"
            }
        """.trimIndent()
        
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("https://apiconnect.angelone.in/rest/auth/angelbroking/user/v1/loginByPassword")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("X-UserType", "USER")
            .addHeader("X-SourceID", "WEB")
            .addHeader("X-ClientLocalIP", ANGEL_CLIENT_IP)
            .addHeader("X-ClientPublicIP", ANGEL_PUBLIC_IP)
            .addHeader("X-MACAddress", ANGEL_MAC_ADDRESS)
            .addHeader("X-PrivateKey", ANGEL_API_KEY)
            .post(requestBody)
            .build()
        
        Logger.d("MainActivity", "loginWithAngelOne() - Sending POST request")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e("MainActivity", "loginWithAngelOne() - API call failed", e)
                runOnUiThread {
                    loginBinding.tokenContainer.visibility = View.VISIBLE
                    loginBinding.apiResponseText.text = "AngelOne Error: ${e.message}"
                    Snackbar.make(binding.root, "Failed to login to AngelOne", Snackbar.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Logger.d("MainActivity", "loginWithAngelOne() - Response code: ${response.code}")
                Logger.d("MainActivity", "loginWithAngelOne() - Response: $responseBody")
                
                runOnUiThread {
                    loginBinding.tokenContainer.visibility = View.VISIBLE
                    loginBinding.apiResponseText.text = "AngelOne Response:\\n$responseBody"
                    
                    try {
                        val jwtTokenMatch = "\"jwtToken\":\"([^\"]+)\"".toRegex().find(responseBody)
                        val refreshTokenMatch = "\"refreshToken\":\"([^\"]+)\"".toRegex().find(responseBody)
                        val feedTokenMatch = "\"feedToken\":\"([^\"]+)\"".toRegex().find(responseBody)
                        
                        val angel_jwtToken = jwtTokenMatch?.groupValues?.get(1)
                        val angel_refreshToken = refreshTokenMatch?.groupValues?.get(1)
                        val angel_feedToken = feedTokenMatch?.groupValues?.get(1)
                        
                        if (angel_jwtToken != null && angel_feedToken != null) {
                            Logger.d("MainActivity", "loginWithAngelOne() - Tokens extracted successfully")
                            loginBinding.accessTokenText.text = "AngelOne JWT: ${angel_jwtToken.take(50)}..."
                            
                            saveAngelTokens(angel_clientId, angel_jwtToken, angel_refreshToken ?: "", angel_feedToken)
                            Snackbar.make(binding.root, "AngelOne login successful!", Snackbar.LENGTH_LONG).show()
                            
                            loginBinding.angelLoginForm.visibility = View.GONE
                            loginBinding.loginPrompt.visibility = View.VISIBLE
                            
                            // Show last login date/time
                            loginBinding.existingTokenContainer.visibility = View.VISIBLE
                            val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", java.util.Locale.getDefault())
                            loginBinding.existingAngelToken.text = "Last login: ${dateFormat.format(java.util.Date())}"
                        } else {
                            Logger.w("MainActivity", "loginWithAngelOne() - Failed to extract tokens")
                            loginBinding.accessTokenText.text = "Failed to extract AngelOne tokens"
                            Snackbar.make(binding.root, "AngelOne login failed", Snackbar.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Logger.e("MainActivity", "loginWithAngelOne() - Error parsing response", e)
                        loginBinding.accessTokenText.text = "Error parsing AngelOne response"
                    }
                }
            }
        })
    }

    private fun saveAngelTokens(clientId: String, jwtToken: String, refreshToken: String, feedToken: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ANGEL_CLIENT_ID, clientId)
            .putString(KEY_ANGEL_JWT_TOKEN, jwtToken)
            .putString(KEY_ANGEL_REFRESH_TOKEN, refreshToken)
            .putString(KEY_ANGEL_FEED_TOKEN, feedToken)
            .putLong(KEY_ANGEL_LAST_LOGIN, System.currentTimeMillis())
            .apply()
        Logger.d("MainActivity", "saveAngelTokens() - Tokens saved successfully")
    }

    fun getAngelJwtToken(): String? {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ANGEL_JWT_TOKEN, null)
    }

    private fun getAngelFeedToken(): String? {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ANGEL_FEED_TOKEN, null)
    }

    fun getAngelClientId(): String? {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ANGEL_CLIENT_ID, null)
    }

    private fun getAngelLastLoginTime(): Long {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_ANGEL_LAST_LOGIN, 0L)
    }

    private fun showStockOptionsDialog(stock: Stock) {
        val options = if (stock.status == StockStatus.HISTORY) {
            arrayOf("Restore", "Delete")
        } else {
            arrayOf("Edit", "Delete", "Move to History")
        }
        
        AlertDialog.Builder(this)
            .setTitle(stock.instrument.tradingSymbol)
            .setItems(options) { dialog, which ->
                when {
                    stock.status == StockStatus.HISTORY && which == 0 -> restoreStockFromHistory(stock)
                    stock.status == StockStatus.HISTORY && which == 1 -> showDeleteStockDialog(stock)
                    stock.status != StockStatus.HISTORY && which == 0 -> showEditStockDialog(stock)
                    stock.status != StockStatus.HISTORY && which == 1 -> showDeleteStockDialog(stock)
                    stock.status != StockStatus.HISTORY && which == 2 -> moveStockToHistory(stock)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun moveStockToHistory(stock: Stock) {
        // Create a new stock with HISTORY status
        val historyStock = Stock(
            instrument = stock.instrument,
            buyPrice = stock.buyPrice,
            stopLoss = stock.stopLoss,
            target = stock.target,
            status = StockStatus.HISTORY
        )
        
        // Update the stock
        stockManager.updateStock(stock, historyStock)
        logManager.addLog(
            LogType.STOCK_MOVED_TO_HISTORY,
            stock.instrument.tradingSymbol,
            "Status: ${stock.status.name} -> HISTORY"
        )
        
        // Refresh the stocks page
        showStocksPage()
        Snackbar.make(binding.root, "Stock moved to history", Snackbar.LENGTH_SHORT).show()
    }
    
    private fun restoreStockFromHistory(stock: Stock) {
        // Create a new stock with NOT_TRIGGERED status
        val activeStock = Stock(
            instrument = stock.instrument,
            buyPrice = stock.buyPrice,
            stopLoss = stock.stopLoss,
            target = stock.target,
            status = StockStatus.NOT_TRIGGERED
        )
        
        // Update the stock
        stockManager.updateStock(stock, activeStock)
        logManager.addLog(
            LogType.STOCK_RESTORED,
            stock.instrument.tradingSymbol,
            "Restored from history"
        )
        
        // Refresh the stocks page
        showStocksPage()
        Snackbar.make(binding.root, "Stock restored from history", Snackbar.LENGTH_SHORT).show()
    }

    private fun showEditStockDialog(stock: Stock) {
        val dialogView = layoutInflater.inflate(R.layout.fragment_add_stock, null)
        val symbolInput = dialogView.findViewById<AutoCompleteTextView>(R.id.stock_name_input)
        val quantityInput = dialogView.findViewById<TextInputEditText>(R.id.quantity_input)
        val buyPriceInput = dialogView.findViewById<TextInputEditText>(R.id.buy_price_input)
        val stopLossInput = dialogView.findViewById<TextInputEditText>(R.id.stoploss_input)
        val targetInput = dialogView.findViewById<TextInputEditText>(R.id.target_input)
        val onlyWatchCheckbox = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.only_watch_checkbox)
        val statusSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.status_spinner)
        val amountRequiredText = dialogView.findViewById<TextView>(R.id.amount_required_text)
        
        // Hide buttons in edit mode
        dialogView.findViewById<Button>(R.id.save_button).visibility = View.GONE
        dialogView.findViewById<Button>(R.id.cancel_button).visibility = View.GONE
        dialogView.findViewById<Button>(R.id.refresh_instruments_button).visibility = View.GONE
        
        // Setup status dropdown
        val statusValues = StockStatus.values().map { it.name.replace("_", " ") }.toTypedArray()
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusValues)
        statusSpinner.setAdapter(statusAdapter)
        
        // Pre-fill with current values
        symbolInput.setText(stock.instrument.tradingSymbol)
        symbolInput.isEnabled = false // Don't allow changing symbol
        quantityInput.setText(stock.quantity.toString())
        buyPriceInput.setText(stock.buyPrice.toString())
        stopLossInput.setText(stock.stopLoss.toString())
        targetInput.setText(stock.target.toString())
        onlyWatchCheckbox.isChecked = stock.onlyWatch
        statusSpinner.setText(stock.status.name.replace("_", " "), false)
        
        // Update amount required when quantity or buy price changes
        val updateAmountRequired = {
            val quantity = quantityInput.text.toString().toIntOrNull() ?: settingsManager.getDefaultQuantity()
            val buyPrice = buyPriceInput.text.toString().toDoubleOrNull() ?: 0.0
            val brokerageCharges = settingsManager.getBrokerageCharges()
            val amountRequired = (buyPrice * quantity) + brokerageCharges
            amountRequiredText.text = "Amount Required: ₹${String.format("%.2f", amountRequired)}"
        }
        
        updateAmountRequired() // Initial calculation
        
        quantityInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateAmountRequired() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        buyPriceInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateAmountRequired() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        AlertDialog.Builder(this)
            .setTitle("Edit Stock")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val quantity = quantityInput.text.toString().toIntOrNull()
                val buyPrice = buyPriceInput.text.toString().toDoubleOrNull()
                val stopLoss = stopLossInput.text.toString().toDoubleOrNull()
                val target = targetInput.text.toString().toDoubleOrNull()
                
                if (quantity != null && buyPrice != null && stopLoss != null && target != null) {
                    // Get selected status
                    val selectedStatusText = statusSpinner.text.toString().replace(" ", "_")
                    val selectedStatus = try {
                        StockStatus.valueOf(selectedStatusText)
                    } catch (e: Exception) {
                        stock.status // Keep current status if parsing fails
                    }
                    
                    // Create updated stock with selected status
                    val updatedStock = Stock(
                        instrument = stock.instrument,
                        buyPrice = buyPrice,
                        stopLoss = stopLoss,
                        target = target,
                        quantity = quantity,
                        onlyWatch = onlyWatchCheckbox.isChecked,
                        status = selectedStatus
                    )
                    
                    // Update in storage
                    stockManager.updateStock(stock, updatedStock)
                    logManager.addLog(
                        LogType.STOCK_UPDATED,
                        stock.instrument.tradingSymbol,
                        "Qty: $quantity, Buy: $buyPrice, SL: $stopLoss, Target: $target, Watch: ${onlyWatchCheckbox.isChecked}"
                    )
                    
                    // Refresh the stocks page
                    showStocksPage()
                    
                    Snackbar.make(binding.root, "Stock updated successfully", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "Invalid input. Please enter valid numbers.", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteStockDialog(stock: Stock) {
        AlertDialog.Builder(this)
            .setTitle("Delete Stock")
            .setMessage("Are you sure you want to delete ${stock.instrument.tradingSymbol}?")
            .setPositiveButton("Delete") { _, _ ->
                // Remove from storage
                stockManager.deleteStock(stock)
                logManager.addLog(LogType.STOCK_DELETED, stock.instrument.tradingSymbol, "Stock deleted")
                
                // Resubscribe to remaining stocks
                resubscribeToStocks()
                
                // Refresh the stocks page
                showStocksPage()
                
                Snackbar.make(binding.root, "${stock.instrument.tradingSymbol} deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAndUpdateStockStatus(stock: Stock, quote: Quote) {
        val ltp = quote.ltp
        var newStatus = stock.status
        var statusChanged = false
        
        when (stock.status) {
            StockStatus.NOT_TRIGGERED -> {
                // Monitor buy price and LTP - when LTP >= buyPrice, place order
                if (ltp >= stock.buyPrice && !stock.onlyWatch) {
                    Logger.d("MainActivity", "Trigger detected for ${stock.instrument.tradingSymbol} - LTP: $ltp >= BuyPrice: ${stock.buyPrice}")
                    
                    // IMPORTANT: Update status to ORDER_PLACED IMMEDIATELY to prevent duplicate order placement
                    // This ensures that if another quote arrives before the API call completes,
                    // we won't try to place the order again
                    val orderPlacingStock = stock.copy(status = StockStatus.ORDER_PLACED, orderId = "PENDING")
                    stockManager.updateStock(stock, orderPlacingStock)
                    
                    // CRITICAL: Refresh the adapter immediately so subsequent quote updates see the new status
                    refreshStocksView()
                    
                    // Now place the order asynchronously
                    placeOrderForStock(orderPlacingStock)
                    
                    // Return early - no need to continue processing since we've updated the status
                    return
                }
            }
            StockStatus.ORDER_PLACED -> {
                // Order has been placed, don't update status here - tracking is handled separately
                return
            }
            StockStatus.TRIGGERED -> {
                // Monitor stop loss and target
                if (ltp < stock.stopLoss) {
                    newStatus = StockStatus.SL_HIT
                    statusChanged = true
                } else if (ltp >= stock.target) {
                    newStatus = StockStatus.TARGET_HIT
                    statusChanged = true
                }
            }
            StockStatus.SL_HIT, StockStatus.TARGET_HIT -> {
                // Already done, unsubscribe
                return
            }
            StockStatus.HISTORY -> {
                // History stocks are not monitored, skip
                return
            }
        }
        
        if (statusChanged && newStatus != StockStatus.ORDER_PLACED) {
            // Calculate percentage difference
            val percentageDiff = ((ltp - stock.buyPrice) / stock.buyPrice) * 100
            
            val updatedStock = Stock(
                instrument = stock.instrument,
                buyPrice = stock.buyPrice,
                stopLoss = stock.stopLoss,
                target = stock.target,
                quantity = stock.quantity,
                onlyWatch = stock.onlyWatch,
                status = if (newStatus == StockStatus.SL_HIT || newStatus == StockStatus.TARGET_HIT) {
                    StockStatus.HISTORY
                } else {
                    newStatus
                },
                orderId = stock.orderId,
                finalStatus = if (newStatus == StockStatus.SL_HIT || newStatus == StockStatus.TARGET_HIT) {
                    newStatus
                } else {
                    null
                },
                finalLTP = if (newStatus == StockStatus.SL_HIT || newStatus == StockStatus.TARGET_HIT) {
                    ltp
                } else {
                    null
                },
                finalPercentage = if (newStatus == StockStatus.SL_HIT || newStatus == StockStatus.TARGET_HIT) {
                    percentageDiff
                } else {
                    null
                }
            )
            
            stockManager.updateStock(stock, updatedStock)
            
            val statusMessage = when (newStatus) {
                StockStatus.TRIGGERED -> "triggered at LTP: ${String.format("%.2f", ltp)}"
                StockStatus.SL_HIT -> "Stop Loss hit at LTP: ${String.format("%.2f", ltp)} - moved to history"
                StockStatus.TARGET_HIT -> "Target hit at LTP: ${String.format("%.2f", ltp)} - moved to history"
                StockStatus.NOT_TRIGGERED -> "not triggered"
                StockStatus.ORDER_PLACED -> "order placed"
                StockStatus.HISTORY -> "moved to history"
            }
            
            logManager.addLog(LogType.STATUS_CHANGE, stock.instrument.tradingSymbol, statusMessage)
            
            val logType = when (newStatus) {
                StockStatus.SL_HIT -> LogType.SL_HIT
                StockStatus.TARGET_HIT -> LogType.TARGET_HIT
                else -> LogType.STATUS_CHANGE
            }
            
            logManager.addLog(logType, stock.instrument.tradingSymbol, statusMessage)
            
            android.widget.Toast.makeText(
                this,
                "${stock.instrument.tradingSymbol}: $statusMessage",
                android.widget.Toast.LENGTH_LONG
            ).show()
            
            // If SL hit or Target hit, unsubscribe from WebSocket and reload stocks page
            if (newStatus == StockStatus.SL_HIT || newStatus == StockStatus.TARGET_HIT) {
                resubscribeToStocks()
                if (currentStocksBinding != null) {
                    showStocksPage()
                }
            } else {
                // For other status changes, just reload stocks page
                if (currentStocksBinding != null) {
                    showStocksPage()
                }
            }
        }
    }

    private fun showLoadingSpinner(binding: FragmentAddStockBinding, show: Boolean) {
        binding.root.findViewById<View>(R.id.loading_overlay)?.visibility = 
            if (show) View.VISIBLE else View.GONE
        binding.root.findViewById<View>(R.id.loading_spinner_container)?.visibility = 
            if (show) View.VISIBLE else View.GONE
    }

    private fun showLoadingSpinner(binding: FragmentStocksBinding, show: Boolean) {
        binding.root.findViewById<View>(R.id.loading_overlay)?.visibility = 
            if (show) View.VISIBLE else View.GONE
        binding.root.findViewById<View>(R.id.loading_spinner_container)?.visibility = 
            if (show) View.VISIBLE else View.GONE
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                // Permission already granted, schedule alarm
                scheduleDailyLoginReminder()
            }
        } else {
            // For Android versions below 13, no runtime permission needed
            scheduleDailyLoginReminder()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, schedule alarm
                    scheduleDailyLoginReminder()
                    Snackbar.make(
                        binding.root,
                        "Notification permission granted. You'll receive daily login reminders.",
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    // Permission denied
                    Snackbar.make(
                        binding.root,
                        "Notification permission denied. You won't receive login reminders.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun scheduleDailyLoginReminder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Check if app can schedule exact alarms (required for Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Request exact alarm permission
                Snackbar.make(
                    binding.root,
                    "Please allow exact alarms in settings for daily reminders",
                    Snackbar.LENGTH_LONG
                ).setAction("Settings") {
                    startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }.show()
                return
            }
        }
        
        // Get reminder time from settings
        val reminderHour = settingsManager.getLoginReminderHour()
        val reminderMinute = settingsManager.getLoginReminderMinute()
        
        val intent = Intent(this, LoginReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Set alarm for configured time
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminderHour)
            set(Calendar.MINUTE, reminderMinute)
            set(Calendar.SECOND, 0)
            
            // If it's already past the reminder time today, schedule for tomorrow
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        // Cancel any existing alarm
        alarmManager.cancel(pendingIntent)
        
        // Schedule exact repeating alarm every day at configured time
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
        
        val timeFormat = String.format("%02d:%02d", reminderHour, reminderMinute)
        Logger.d("MainActivity", "Daily login reminder scheduled for $timeFormat")
        Snackbar.make(
            binding.root,
            "Daily login reminder scheduled for $timeFormat",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showPortfolioPage() {
        currentStocksBinding = null  // Clear reference when leaving stocks page
        binding.contentContainer.removeAllViews()
        supportActionBar?.title = "Portfolio"
        
        val fragment = PortfolioFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showAllExchangesStocksPage() {
        currentStocksBinding = null  // Clear reference when leaving stocks page
        val allStocksView = LayoutInflater.from(this)
            .inflate(R.layout.fragment_all_stocks, null) as LinearLayout
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(allStocksView)
        supportActionBar?.title = "All Exchanges Stocks"
        
        val exchangeSpinner = allStocksView.findViewById<Spinner>(R.id.exchange_spinner)
        val segmentSpinner = allStocksView.findViewById<Spinner>(R.id.segment_spinner)
        val instrumentTypeSpinner = allStocksView.findViewById<Spinner>(R.id.instrument_type_spinner)
        val searchInput = allStocksView.findViewById<EditText>(R.id.search_input)
        val applyFiltersButton = allStocksView.findViewById<Button>(R.id.apply_filters_button)
        val clearFiltersButton = allStocksView.findViewById<Button>(R.id.clear_filters_button)
        val stocksListView = allStocksView.findViewById<ListView>(R.id.all_stocks_list_view)
        val emptyView = allStocksView.findViewById<TextView>(R.id.empty_view)
        val resultsCount = allStocksView.findViewById<TextView>(R.id.results_count)
        
        // Load all instruments
        val allInstruments = stockManager.getAllExchangesStocks()
        
        if (allInstruments.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            stocksListView.visibility = View.GONE
            resultsCount.text = "Total: 0 instruments"
            return
        }
        
        // Create adapter
        val adapter = AllStocksAdapter(this, allInstruments)
        stocksListView.adapter = adapter
        
        // Get unique values for spinners
        val exchanges = listOf("All") + allInstruments.map { it.exchange }.distinct().sorted()
        val segments = listOf("All") + allInstruments.map { it.segment }.distinct().sorted()
        val instrumentTypes = listOf("All") + allInstruments.map { it.instrumentType }.distinct().sorted()
        
        // Setup spinners
        val exchangeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, exchanges)
        exchangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        exchangeSpinner.adapter = exchangeAdapter
        
        val segmentAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, segments)
        segmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        segmentSpinner.adapter = segmentAdapter
        
        val instrumentTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, instrumentTypes)
        instrumentTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        instrumentTypeSpinner.adapter = instrumentTypeAdapter
        
        resultsCount.text = "Total: ${allInstruments.size} instruments"
        
        // Apply filters button
        applyFiltersButton.setOnClickListener {
            val selectedExchange = exchangeSpinner.selectedItem.toString()
            val selectedSegment = segmentSpinner.selectedItem.toString()
            val selectedType = instrumentTypeSpinner.selectedItem.toString()
            val searchQuery = searchInput.text.toString().lowercase()
            
            var filtered = allInstruments
            
            // Apply exchange filter
            if (selectedExchange != "All") {
                filtered = filtered.filter { it.exchange == selectedExchange }
            }
            
            // Apply segment filter
            if (selectedSegment != "All") {
                filtered = filtered.filter { it.segment == selectedSegment }
            }
            
            // Apply instrument type filter
            if (selectedType != "All") {
                filtered = filtered.filter { it.instrumentType == selectedType }
            }
            
            // Apply search filter
            if (searchQuery.isNotEmpty()) {
                filtered = filtered.filter { 
                    it.tradingSymbol.lowercase().contains(searchQuery) ||
                    it.name.lowercase().contains(searchQuery)
                }
            }
            
            // Update adapter and display
            adapter.updateList(filtered)
            resultsCount.text = "Total: ${filtered.size} instruments"
            
            if (filtered.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                stocksListView.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                stocksListView.visibility = View.VISIBLE
            }
            
            Snackbar.make(binding.root, "Showing ${filtered.size} results", Snackbar.LENGTH_SHORT).show()
        }
        
        // Clear filters button
        clearFiltersButton.setOnClickListener {
            exchangeSpinner.setSelection(0)
            segmentSpinner.setSelection(0)
            instrumentTypeSpinner.setSelection(0)
            searchInput.text.clear()
            
            adapter.updateList(allInstruments)
            resultsCount.text = "Total: ${allInstruments.size} instruments"
            emptyView.visibility = View.GONE
            stocksListView.visibility = View.VISIBLE
            
            Snackbar.make(binding.root, "Filters cleared", Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun placeOrderForStock(stock: Stock) {
        Logger.d("MainActivity", "========== placeOrderForStock START ==========")
        Logger.d("MainActivity", "placeOrderForStock() called for ${stock.instrument.tradingSymbol}")
        Logger.d("MainActivity", "Current stock status: ${stock.status}")
        Logger.d("MainActivity", "Current stock orderId: '${stock.orderId}'")
        
        // Check if order is already being placed or has been placed
        if (stock.orderId.isNotEmpty() && stock.orderId != "PENDING") {
            Logger.w("MainActivity", "⚠️ Order already placed for ${stock.instrument.tradingSymbol}, orderId: ${stock.orderId}")
            Logger.w("MainActivity", "Skipping duplicate order placement")
            Logger.d("MainActivity", "========== placeOrderForStock SKIPPED ==========")
            return
        }
        
        // Double-check the current status from file to prevent race conditions
        val currentStock = stockManager.getAllStocks().find { it.instrument.instrumentToken == stock.instrument.instrumentToken }
        if (currentStock != null && currentStock.status == StockStatus.ORDER_PLACED && currentStock.orderId.isNotEmpty() && currentStock.orderId != "PENDING") {
            Logger.w("MainActivity", "⚠️ Stock already has ORDER_PLACED status with orderId: ${currentStock.orderId}")
            Logger.w("MainActivity", "This indicates a potential race condition - aborting order placement")
            Logger.d("MainActivity", "========== placeOrderForStock ABORTED ==========")
            return
        }
        
        // Get Kite credentials
        val accessToken = getKiteAccessToken()
        if (accessToken == null || accessToken.isEmpty()) {
            Logger.e("MainActivity", "No access token available for order placement")
            
            // Revert status back to NOT_TRIGGERED since we can't place the order
            val revertedStock = stock.copy(status = StockStatus.NOT_TRIGGERED, orderId = "")
            stockManager.updateStock(stock, revertedStock)
            
            Snackbar.make(binding.root, "Not authenticated with Kite", Snackbar.LENGTH_LONG).show()
            return
        }
        
        val apiKey = KITE_API_KEY
        val symbol = stock.instrument.tradingSymbol
        val exchange = stock.instrument.exchange
        val buyPrice = stock.buyPrice
        val quantity = stock.quantity
        
        Logger.d("MainActivity", "Order details:")
        Logger.d("MainActivity", "  Symbol: $symbol")
        Logger.d("MainActivity", "  Exchange: $exchange")
        Logger.d("MainActivity", "  Price: $buyPrice")
        Logger.d("MainActivity", "  Quantity: $quantity")
        Logger.d("MainActivity", "  API Key: ${apiKey.substring(0, minOf(10, apiKey.length))}...")
        
        // Call Kite API to place order
        KiteOrderManager.placeOrder(apiKey, accessToken, symbol, exchange, buyPrice, quantity) { orderId, error ->
            runOnUiThread {
                if (error != null) {
                    Logger.e("MainActivity", "Failed to place order: $error")
                    
                    // Revert status back to NOT_TRIGGERED since order placement failed
                    val revertedStock = stock.copy(status = StockStatus.NOT_TRIGGERED, orderId = "")
                    stockManager.updateStock(stock, revertedStock)
                    
                    Snackbar.make(binding.root, "Order failed: $error", Snackbar.LENGTH_LONG).show()
                } else if (orderId != null) {
                    Logger.d("MainActivity", "Order placed successfully! Order ID: $orderId")
                    
                    // Update stock with actual order ID (replacing "PENDING")
                    val updatedStock = stock.copy(orderId = orderId)
                    stockManager.updateStock(stock, updatedStock)
                    
                    // Note: Order status will be checked automatically by the orders API polling (every 20 seconds)
                    // No need to start individual tracking
                    
                    Snackbar.make(binding.root, "Order placed: $orderId. Status will be checked automatically.", Snackbar.LENGTH_LONG).show()
                    
                    Logger.d("MainActivity", "========== placeOrderForStock SUCCESS ==========")
                }
            }
        }
    }
    
    private fun startTrackingOrder(stock: Stock) {
        Logger.d("MainActivity", "startTrackingOrder() for orderId: ${stock.orderId}")
        
        // Create a handler for polling
        val handler = Handler(Looper.getMainLooper())
        val pollingRunnable = object : Runnable {
            private var attemptCount = 0
            private val maxAttempts = 120 // 120 * 5 seconds = 10 minutes max
            
            override fun run() {
                attemptCount++
                Logger.d("MainActivity", "========== POLLING ATTEMPT $attemptCount/${maxAttempts} ==========")
                Logger.d("MainActivity", "Polling order status for order: ${stock.orderId}")
                
                // Get current stock from manager (in case it changed)
                val currentStock = stockManager.getAllStocks().find { it.instrument.instrumentToken == stock.instrument.instrumentToken }
                if (currentStock == null) {
                    Logger.d("MainActivity", "Stock not found in manager, stopping tracking")
                    return
                }
                
                Logger.d("MainActivity", "Current stock status: ${currentStock.status}")
                Logger.d("MainActivity", "Current stock orderId: ${currentStock.orderId}")
                
                if (currentStock.orderId.isEmpty()) {
                    Logger.d("MainActivity", "Order ID cleared, stopping tracking")
                    return
                }
                
                // If stock is no longer in ORDER_PLACED status, stop tracking
                if (currentStock.status != StockStatus.ORDER_PLACED) {
                    Logger.d("MainActivity", "Stock status changed to ${currentStock.status}, stopping tracking")
                    return
                }
                
                val accessToken = getKiteAccessToken()
                if (accessToken == null || accessToken.isEmpty()) {
                    Logger.e("MainActivity", "No access token for order status check - retrying...")
                    // Retry after delay
                    if (attemptCount < maxAttempts) {
                        Logger.d("MainActivity", "Scheduling next poll in 5 seconds")
                        handler.postDelayed(this, 5000) // 5 seconds
                    }
                    return
                }
                
                val apiKey = KITE_API_KEY
                Logger.d("MainActivity", "Making API call to check order status...")
                
                // Check order status
                KiteOrderManager.checkOrderStatus(apiKey, accessToken, currentStock.orderId) { orderStatus, error ->
                    Logger.d("MainActivity", "Polling callback received:")
                    Logger.d("MainActivity", "  Order status: $orderStatus")
                    Logger.d("MainActivity", "  Error: $error")
                    
                    if (error != null) {
                        Logger.e("MainActivity", "Failed to check order status: $error")
                        // Retry on error
                        if (attemptCount < maxAttempts) {
                            Logger.d("MainActivity", "Error encountered, scheduling retry in 5 seconds...")
                            handler.postDelayed(this, 5000)
                        }
                    } else if (orderStatus != null) {
                        Logger.d("MainActivity", "Order status received: '$orderStatus'")
                        
                        val statusUpper = orderStatus.uppercase()
                        Logger.d("MainActivity", "Status (uppercase): '$statusUpper'")
                        
                        when {
                            statusUpper.contains("COMPLETE") || statusUpper == "COMPLETE" -> {
                                Logger.d("MainActivity", "✓ ORDER COMPLETED! Status contains 'COMPLETE'")
                                runOnUiThread {
                                    Logger.d("MainActivity", "Updating stock status to TRIGGERED")
                                    val triggeredStock = currentStock.copy(status = StockStatus.TRIGGERED)
                                    stockManager.updateStock(currentStock, triggeredStock)
                                    
                                    Snackbar.make(binding.root, "Order filled!", Snackbar.LENGTH_SHORT).show()
                                }
                                // Stop tracking
                            }
                            statusUpper.contains("REJECTED") || statusUpper.contains("CANCELLED") -> {
                                Logger.d("MainActivity", "✗ ORDER FAILED! Status: $orderStatus")
                                runOnUiThread {
                                    val revertedStock = currentStock.copy(
                                        status = StockStatus.NOT_TRIGGERED,
                                        orderId = ""
                                    )
                                    stockManager.updateStock(currentStock, revertedStock)
                                    
                                    Snackbar.make(binding.root, "Order $orderStatus", Snackbar.LENGTH_LONG).show()
                                }
                                // Stop tracking
                            }
                            else -> {
                                // Still pending, continue polling
                                Logger.d("MainActivity", "⏳ Order still pending. Status: $orderStatus")
                                if (attemptCount < maxAttempts) {
                                    Logger.d("MainActivity", "Scheduling next poll in 5 seconds...")
                                    handler.postDelayed(this, 5000)
                                } else {
                                    Logger.w("MainActivity", "Max polling attempts ($maxAttempts) reached, stopping tracking")
                                    runOnUiThread {
                                        Snackbar.make(binding.root, "Order tracking timeout", Snackbar.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    } else {
                        // No error but no status either, retry
                        Logger.w("MainActivity", "No status received and no error - retrying...")
                        if (attemptCount < maxAttempts) {
                            Logger.d("MainActivity", "Scheduling retry in 5 seconds...")
                            handler.postDelayed(this, 5000)
                        }
                    }
                    Logger.d("MainActivity", "========== END POLL CALLBACK ==========")
                }
            }
        }
        
        // Start polling after 1 second
        handler.postDelayed(pollingRunnable, 1000)
    }
    
    private fun resumeOrderTracking() {
        Logger.d("MainActivity", "resumeOrderTracking() - Checking for pending orders")
        
        // Check if Kite credentials are available
        val accessToken = getKiteAccessToken()
        val apiKey = KITE_API_KEY
        
        if (accessToken == null || accessToken.isEmpty()) {
            Logger.d("MainActivity", "No Kite access token available, skipping order tracking resume")
            return
        }
        
        if (apiKey.isEmpty()) {
            Logger.d("MainActivity", "No Kite API key available, skipping order tracking resume")
            return
        }
        
        Logger.d("MainActivity", "Kite credentials available, checking for ORDER_PLACED stocks")
        
        // Get all stocks with ORDER_PLACED status
        val orderPlacedStocks = stockManager.getAllStocks().filter { 
            it.status == StockStatus.ORDER_PLACED && it.orderId.isNotEmpty()
        }
        
        if (orderPlacedStocks.isEmpty()) {
            Logger.d("MainActivity", "No stocks with ORDER_PLACED status found")
            return
        }
        
        Logger.d("MainActivity", "Found ${orderPlacedStocks.size} stock(s) with ORDER_PLACED status:")
        orderPlacedStocks.forEach { stock ->
            Logger.d("MainActivity", "  - ${stock.instrument.tradingSymbol} (Order ID: ${stock.orderId})")
        }
        
        // Fetch orders immediately to check status
        fetchKiteOrders { orders, error ->
            if (error == null) {
                Logger.d("MainActivity", "Orders fetched for resume check, will be updated by checkAndUpdateOrderPlacedStocks")
            } else {
                Logger.w("MainActivity", "Failed to fetch orders on resume: $error")
            }
        }
        
        // Show a notification to the user
        if (orderPlacedStocks.size > 0) {
            runOnUiThread {
                Snackbar.make(
                    binding.root, 
                    "Checking status for ${orderPlacedStocks.size} pending order(s)", 
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun refreshStocksView() {
        Logger.d("MainActivity", "refreshStocksView() - Refreshing stocks adapter")
        
        // Always reload from file to get latest state
        val allStocks = stockManager.getAllStocks()
        
        // Separate active and history stocks
        val activeStocks = allStocks.filter { it.status != StockStatus.HISTORY }
        val historyStocks = allStocks.filter { it.status == StockStatus.HISTORY }
        
        Logger.d("MainActivity", "Loaded ${activeStocks.size} active and ${historyStocks.size} history stocks from file")
        
        currentStocksBinding?.let { stocksBinding ->
            // Store current tab position
            val currentTab = stocksBinding.stocksViewPager.currentItem
            
            // Create new adapter with updated stocks
            val adapter = StocksTabAdapter(
                activeStocks = activeStocks,
                historyStocks = historyStocks,
                onStockClick = { stock -> showStockOptionsDialog(stock) },
                onOrdersTabSelected = { fetchAndDisplayOrders() },
                onOrderClick = { order -> showOrderDetailsDialog(order) },
                quotesCache = StockMonitorService.quotesCache
            )
            
            stocksBinding.stocksViewPager.adapter = adapter
            
            // Restore tab position
            stocksBinding.stocksViewPager.setCurrentItem(currentTab, false)
            
            // Notify service to resubscribe to updated stock list
            notifyServiceToResubscribe()
            
            Logger.d("MainActivity", "Stocks view refreshed successfully - Tab: $currentTab")
        } ?: run {
            Logger.w("MainActivity", "currentStocksBinding is null - Not on stocks page, will refresh when navigated")
        }
    }
    
    private fun checkAndUpdateOrderPlacedStocks(orders: List<Order>) {
        Logger.d("MainActivity", "checkAndUpdateOrderPlacedStocks() - Checking ${orders.size} orders")
        
        // Get all stocks first for debugging
        val allStocks = stockManager.getAllStocks()
        Logger.d("MainActivity", "Total stocks in manager: ${allStocks.size}")
        
        // Log all stock statuses
        allStocks.forEachIndexed { index, stock ->
            Logger.d("MainActivity", "Stock $index: ${stock.instrument.tradingSymbol} - Status: ${stock.status} - OrderId: '${stock.orderId}'")
        }
        
        // Get all stocks with ORDER_PLACED status
        val orderPlacedStocks = allStocks.filter { 
            it.status == StockStatus.ORDER_PLACED && it.orderId.isNotEmpty()
        }
        
        if (orderPlacedStocks.isEmpty()) {
            Logger.d("MainActivity", "No stocks with ORDER_PLACED status to check")
            
            // Additional debugging - check if any have ORDER_PLACED but empty orderId
            val orderPlacedNoId = allStocks.filter { it.status == StockStatus.ORDER_PLACED }
            if (orderPlacedNoId.isNotEmpty()) {
                Logger.w("MainActivity", "Found ${orderPlacedNoId.size} stock(s) with ORDER_PLACED but empty orderId:")
                orderPlacedNoId.forEach { stock ->
                    Logger.w("MainActivity", "  - ${stock.instrument.tradingSymbol}")
                }
            }
            return
        }
        
        Logger.d("MainActivity", "Found ${orderPlacedStocks.size} stock(s) with ORDER_PLACED status")
        
        // For each ORDER_PLACED stock, find matching order and check status
        orderPlacedStocks.forEach { stock ->
            Logger.d("MainActivity", "Checking stock: ${stock.instrument.tradingSymbol} (Order ID: ${stock.orderId})")
            
            val matchingOrder = orders.find { it.orderId == stock.orderId }
            
            if (matchingOrder != null) {
                Logger.d("MainActivity", "Found matching order:")
                Logger.d("MainActivity", "  Order ID: ${matchingOrder.orderId}")
                Logger.d("MainActivity", "  Status: ${matchingOrder.status}")
                Logger.d("MainActivity", "  Filled Qty: ${matchingOrder.filledQuantity}/${matchingOrder.quantity}")
                
                val orderStatus = matchingOrder.status.uppercase()
                
                when {
                    orderStatus.contains("COMPLETE") || orderStatus == "COMPLETE" -> {
                        Logger.d("MainActivity", "✓ Order COMPLETED! Updating stock to TRIGGERED")
                        runOnUiThread {
                            val triggeredStock = stock.copy(status = StockStatus.TRIGGERED)
                            stockManager.updateStock(stock, triggeredStock)
                            
                            Snackbar.make(
                                binding.root, 
                                "${stock.instrument.tradingSymbol} order filled!", 
                                Snackbar.LENGTH_SHORT
                            ).show()
                            
                            // Refresh stocks view
                            refreshStocksView()
                        }
                    }
                    orderStatus.contains("REJECT") || orderStatus.contains("CANCEL") -> {
                        Logger.d("MainActivity", "✗ Order FAILED! Status: '$orderStatus' (raw: '${matchingOrder.status}') - Moving to HISTORY to prevent re-ordering")
                        runOnUiThread {
                            val failedStock = stock.copy(
                                status = StockStatus.HISTORY,
                                orderId = ""
                            )
                            stockManager.updateStock(stock, failedStock)
                            
                            Snackbar.make(
                                binding.root, 
                                "${stock.instrument.tradingSymbol} order ${matchingOrder.status} - Moved to history", 
                                Snackbar.LENGTH_LONG
                            ).show()
                            
                            // Refresh stocks view
                            refreshStocksView()
                        }
                    }
                    else -> {
                        Logger.d("MainActivity", "⏳ Order still pending: $orderStatus")
                    }
                }
            } else {
                Logger.w("MainActivity", "No matching order found for ${stock.instrument.tradingSymbol} (Order ID: ${stock.orderId})")
                Logger.w("MainActivity", "This could mean the order is too old or was placed with different credentials")
            }
        }
    }
    
    private fun showSettingsPage() {
        currentStocksBinding = null
        val settingsView = LayoutInflater.from(this).inflate(R.layout.fragment_settings, null)
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(settingsView)
        supportActionBar?.title = "Settings"
        
        val autoOrdersCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.auto_orders_checkbox)
        val pollingIntervalInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.polling_interval_input)
        val brokerageInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.brokerage_input)
        val defaultQuantityInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.default_quantity_input)
        val debugLogsCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.debug_logs_checkbox)
        val notifyOrderPlacedCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.notify_order_placed_checkbox)
        val notifyOrderFilledCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.notify_order_filled_checkbox)
        val notifySlHitCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.notify_sl_hit_checkbox)
        val notifyTargetHitCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.notify_target_hit_checkbox)
        val soundAlertsCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.sound_alerts_checkbox)
        val vibrationCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.vibration_checkbox)
        val showPercentageCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.show_percentage_checkbox)
        val compactViewCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.compact_view_checkbox)
        val themeSpinner = settingsView.findViewById<AutoCompleteTextView>(R.id.theme_spinner)
        val loginReminderCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.login_reminder_checkbox)
        val reminderHourInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.reminder_hour_input)
        val reminderMinuteInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.reminder_minute_input)
        val dailyOrderCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.daily_order_checkbox)
        val dailyOrderHourInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.daily_order_hour_input)
        val dailyOrderMinuteInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.daily_order_minute_input)
        val testDailyOrderButton = settingsView.findViewById<Button>(R.id.test_daily_order_button)
        val maxDailyOrdersInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.max_daily_orders_input)
        val maxPerStockInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.max_per_stock_input)
        val maxTotalInvestmentInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.max_total_investment_input)
        val requireConfirmationCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.require_confirmation_checkbox)
        val autoLogoutHoursInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.auto_logout_hours_input)
        val backgroundServiceCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.background_service_checkbox)
        val keepScreenAwakeCheckbox = settingsView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.keep_screen_awake_checkbox)
        val maxLogEntriesInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.max_log_entries_input)
        val kiteApiKeyInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.kite_api_key_input)
        val kiteSecretKeyInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.kite_secret_key_input)
        val angelApiKeyInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.angel_api_key_input)
        val angelClientIpInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.angel_client_ip_input)
        val angelPublicIpInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.angel_public_ip_input)
        val angelMacAddressInput = settingsView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.angel_mac_address_input)
        val exportDataButton = settingsView.findViewById<Button>(R.id.export_data_button)
        val importDataButton = settingsView.findViewById<Button>(R.id.import_data_button)
        val clearLogsButton = settingsView.findViewById<Button>(R.id.clear_logs_button)
        val clearHistoryButton = settingsView.findViewById<Button>(R.id.clear_history_button)
        val saveButton = settingsView.findViewById<Button>(R.id.save_settings_button)
        val resetButton = settingsView.findViewById<Button>(R.id.reset_settings_button)
        
        // Load current settings
        autoOrdersCheckbox.isChecked = settingsManager.isAutoOrdersEnabled()
        pollingIntervalInput.setText(settingsManager.getOrderPollingInterval().toString())
        brokerageInput.setText(settingsManager.getBrokerageCharges().toString())
        defaultQuantityInput.setText(settingsManager.getDefaultQuantity().toString())
        debugLogsCheckbox.isChecked = settingsManager.isDebugLogsEnabled()
        notifyOrderPlacedCheckbox.isChecked = settingsManager.isNotifyOrderPlaced()
        notifyOrderFilledCheckbox.isChecked = settingsManager.isNotifyOrderFilled()
        notifySlHitCheckbox.isChecked = settingsManager.isNotifySlHit()
        notifyTargetHitCheckbox.isChecked = settingsManager.isNotifyTargetHit()
        soundAlertsCheckbox.isChecked = settingsManager.isSoundAlertsEnabled()
        vibrationCheckbox.isChecked = settingsManager.isVibrationEnabled()
        showPercentageCheckbox.isChecked = settingsManager.shouldShowPercentage()
        compactViewCheckbox.isChecked = settingsManager.isCompactViewEnabled()
        themeSpinner.setText(settingsManager.getThemeMode(), false)
        loginReminderCheckbox.isChecked = settingsManager.isLoginReminderEnabled()
        reminderHourInput.setText(settingsManager.getLoginReminderHour().toString())
        reminderMinuteInput.setText(settingsManager.getLoginReminderMinute().toString())
        dailyOrderCheckbox.isChecked = isDailyOrderEnabled()
        dailyOrderHourInput.setText(settingsManager.getDailyOrderHour().toString())
        dailyOrderMinuteInput.setText(settingsManager.getDailyOrderMinute().toString())
        maxDailyOrdersInput.setText(settingsManager.getMaxDailyOrders().toString())
        maxPerStockInput.setText(settingsManager.getMaxInvestmentPerStock().toString())
        maxTotalInvestmentInput.setText(settingsManager.getMaxTotalInvestment().toString())
        requireConfirmationCheckbox.isChecked = settingsManager.isOrderConfirmationRequired()
        autoLogoutHoursInput.setText(settingsManager.getAutoLogoutHours().toString())
        backgroundServiceCheckbox.isChecked = settingsManager.isBackgroundServiceEnabled()
        keepScreenAwakeCheckbox.isChecked = settingsManager.isKeepScreenAwakeEnabled()
        maxLogEntriesInput.setText(settingsManager.getMaxLogEntries().toString())
        kiteApiKeyInput.setText(settingsManager.getKiteApiKey() ?: KITE_API_KEY)
        kiteSecretKeyInput.setText(settingsManager.getKiteSecretKey() ?: KITE_SECRET_KEY)
        angelApiKeyInput.setText(settingsManager.getAngelApiKey() ?: ANGEL_API_KEY)
        angelClientIpInput.setText(settingsManager.getAngelClientIp())
        angelPublicIpInput.setText(settingsManager.getAngelPublicIp())
        angelMacAddressInput.setText(settingsManager.getAngelMacAddress())
        
        // Setup theme dropdown
        val themeOptions = arrayOf("light", "dark", "system")
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, themeOptions)
        themeSpinner.setAdapter(themeAdapter)
        
        // Save button
        saveButton.setOnClickListener {
            settingsManager.setAutoOrdersEnabled(autoOrdersCheckbox.isChecked)
            val pollingInterval = pollingIntervalInput.text.toString().toIntOrNull() ?: 20
            settingsManager.setOrderPollingInterval(pollingInterval)
            val brokerage = brokerageInput.text.toString().toDoubleOrNull() ?: 20.0
            settingsManager.setBrokerageCharges(brokerage)
            val quantity = defaultQuantityInput.text.toString().toIntOrNull() ?: 1
            settingsManager.setDefaultQuantity(quantity)
            settingsManager.setDebugLogsEnabled(debugLogsCheckbox.isChecked)
            Logger.enableDebugLogs = debugLogsCheckbox.isChecked
            settingsManager.setNotifyOrderPlaced(notifyOrderPlacedCheckbox.isChecked)
            settingsManager.setNotifyOrderFilled(notifyOrderFilledCheckbox.isChecked)
            settingsManager.setNotifySlHit(notifySlHitCheckbox.isChecked)
            settingsManager.setNotifyTargetHit(notifyTargetHitCheckbox.isChecked)
            settingsManager.setSoundAlertsEnabled(soundAlertsCheckbox.isChecked)
            settingsManager.setVibrationEnabled(vibrationCheckbox.isChecked)
            settingsManager.setShowPercentage(showPercentageCheckbox.isChecked)
            settingsManager.setCompactViewEnabled(compactViewCheckbox.isChecked)
            settingsManager.setThemeMode(themeSpinner.text.toString())
            settingsManager.setLoginReminderEnabled(loginReminderCheckbox.isChecked)
            val hour = reminderHourInput.text.toString().toIntOrNull() ?: 8
            val minute = reminderMinuteInput.text.toString().toIntOrNull() ?: 30
            settingsManager.setLoginReminderHour(hour)
            settingsManager.setLoginReminderMinute(minute)
            if (loginReminderCheckbox.isChecked) {
                scheduleDailyLoginReminder()
            }
            
            // Handle daily order scheduling
            if (dailyOrderCheckbox.isChecked) {
                val dailyOrderHour = dailyOrderHourInput.text.toString().toIntOrNull() ?: 9
                val dailyOrderMinute = dailyOrderMinuteInput.text.toString().toIntOrNull() ?: 0
                settingsManager.setDailyOrderHour(dailyOrderHour)
                settingsManager.setDailyOrderMinute(dailyOrderMinute)
                scheduleDailyOrder()
            } else {
                cancelDailyOrder()
            }
            
            val maxDailyOrders = maxDailyOrdersInput.text.toString().toIntOrNull() ?: 100
            settingsManager.setMaxDailyOrders(maxDailyOrders)
            val maxPerStock = maxPerStockInput.text.toString().toDoubleOrNull() ?: 100000.0
            settingsManager.setMaxInvestmentPerStock(maxPerStock)
            val maxTotal = maxTotalInvestmentInput.text.toString().toDoubleOrNull() ?: 500000.0
            settingsManager.setMaxTotalInvestment(maxTotal)
            settingsManager.setOrderConfirmationRequired(requireConfirmationCheckbox.isChecked)
            val autoLogoutHours = autoLogoutHoursInput.text.toString().toIntOrNull() ?: 0
            settingsManager.setAutoLogoutHours(autoLogoutHours)
            settingsManager.setBackgroundServiceEnabled(backgroundServiceCheckbox.isChecked)
            settingsManager.setKeepScreenAwakeEnabled(keepScreenAwakeCheckbox.isChecked)
            val maxLogEntries = maxLogEntriesInput.text.toString().toIntOrNull() ?: 1000
            settingsManager.setMaxLogEntries(maxLogEntries)
            settingsManager.setKiteApiKey(kiteApiKeyInput.text.toString())
            settingsManager.setKiteSecretKey(kiteSecretKeyInput.text.toString())
            settingsManager.setAngelApiKey(angelApiKeyInput.text.toString())
            settingsManager.setAngelClientIp(angelClientIpInput.text.toString())
            settingsManager.setAngelPublicIp(angelPublicIpInput.text.toString())
            settingsManager.setAngelMacAddress(angelMacAddressInput.text.toString())
            
            Snackbar.make(binding.root, "Settings saved successfully", Snackbar.LENGTH_SHORT).show()
        }
        
        // Test daily order button
        testDailyOrderButton.setOnClickListener {
            testDailyOrder()
        }
        
        // Reset button
        resetButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Settings")
                .setMessage("Are you sure you want to reset all settings to defaults?")
                .setPositiveButton("Reset") { _, _ ->
                    settingsManager.clearAllSettings()
                    Logger.enableDebugLogs = settingsManager.isDebugLogsEnabled()
                    Snackbar.make(binding.root, "Settings reset to defaults", Snackbar.LENGTH_SHORT).show()
                    showSettingsPage()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // Export button
        exportDataButton.setOnClickListener {
            exportDataToJson()
        }
        
        // Import button
        importDataButton.setOnClickListener {
            importDataFromJson()
        }
        
        // Clear logs button
        clearLogsButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to delete all logs?")
                .setPositiveButton("Clear") { _, _ ->
                    Snackbar.make(binding.root, "Logs cleared", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // Clear history button
        clearHistoryButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to delete all history stocks?")
                .setPositiveButton("Clear") { _, _ ->
                    val historyStocks = stockManager.getAllStocks().filter { it.status == StockStatus.HISTORY }
                    historyStocks.forEach { stock ->
                        stockManager.deleteStock(stock)
                    }
                    Snackbar.make(binding.root, "History cleared", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun exportDataToJson() {
        try {
            val stocks = stockManager.getAllStocks()
            
            val jsonObject = org.json.JSONObject()
            
            // Add stocks
            val stocksArray = org.json.JSONArray()
            stocks.forEach { stock ->
                val stockJson = org.json.JSONObject()
                stockJson.put("symbol", stock.instrument.tradingSymbol)
                stockJson.put("token", stock.instrument.instrumentToken)
                stockJson.put("exchange", stock.instrument.exchange)
                stockJson.put("buyPrice", stock.buyPrice)
                stockJson.put("stopLoss", stock.stopLoss)
                stockJson.put("target", stock.target)
                stockJson.put("quantity", stock.quantity)
                stockJson.put("onlyWatch", stock.onlyWatch)
                stockJson.put("status", stock.status.name)
                stockJson.put("orderId", stock.orderId)
                stocksArray.put(stockJson)
            }
            jsonObject.put("stocks", stocksArray)
            
            // Add export metadata
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            jsonObject.put("exportDate", dateFormat.format(java.util.Date()))
            jsonObject.put("version", "1.0")
            
            // Write to file in app's files directory
            val fileName = "StocksMonitor_Export_${System.currentTimeMillis()}.json"
            val file = java.io.File(getExternalFilesDir(null), fileName)
            file.writeText(jsonObject.toString(2))
            
            val message = "Data exported successfully\n$fileName"
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            Logger.d("MainActivity", "Data exported to: ${file.absolutePath}")
        } catch (e: Exception) {
            Logger.e("MainActivity", "Error exporting data", e)
            Snackbar.make(binding.root, "Error exporting data: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun importDataFromJson() {
        try {
            // Create an intent to pick a file
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            
            startActivityForResult(Intent.createChooser(intent, "Select JSON file to import"), 1001)
        } catch (e: Exception) {
            Logger.e("MainActivity", "Error starting file picker", e)
            Snackbar.make(binding.root, "Error opening file picker: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            try {
                val uri = data.data ?: return
                val inputStream = contentResolver.openInputStream(uri) ?: return
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()
                
                val jsonObject = org.json.JSONObject(jsonString)
                
                // Import stocks
                val stocksArray = jsonObject.optJSONArray("stocks")
                var importedCount = 0
                if (stocksArray != null) {
                    for (i in 0 until stocksArray.length()) {
                        val stockJson = stocksArray.getJSONObject(i)
                        val instrument = Instrument(
                            instrumentToken = stockJson.getString("token"),
                            exchangeToken = "",
                            tradingSymbol = stockJson.getString("symbol"),
                            name = stockJson.getString("symbol"),
                            lastPrice = "0",
                            expiry = "",
                            strike = "",
                            tickSize = "0",
                            lotSize = "1",
                            instrumentType = "EQUITY",
                            segment = "NSE",
                            exchange = stockJson.getString("exchange")
                        )
                        val stock = Stock(
                            instrument = instrument,
                            buyPrice = stockJson.getDouble("buyPrice"),
                            stopLoss = stockJson.getDouble("stopLoss"),
                            target = stockJson.getDouble("target"),
                            quantity = stockJson.optInt("quantity", 1),
                            onlyWatch = stockJson.optBoolean("onlyWatch", false),
                            status = StockStatus.valueOf(stockJson.getString("status")),
                            orderId = stockJson.optString("orderId", "")
                        )
                        stockManager.saveStock(stock)
                        importedCount++
                    }
                }
                
                val message = "Import successful\nStocks imported: $importedCount"
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                Logger.d("MainActivity", "Imported $importedCount stocks from JSON")
            } catch (e: Exception) {
                Logger.e("MainActivity", "Error importing data", e)
                Snackbar.make(binding.root, "Error importing data: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    // Daily order scheduling methods
    fun scheduleDailyOrder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Check if app can schedule exact alarms (required for Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Snackbar.make(
                    binding.root,
                    "Please allow exact alarms in settings for daily orders",
                    Snackbar.LENGTH_LONG
                ).setAction("Settings") {
                    startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }.show()
                return
            }
        }
        
        // Get configured time from settings
        val orderHour = settingsManager.getDailyOrderHour()
        val orderMinute = settingsManager.getDailyOrderMinute()
        
        val intent = Intent(this, DailyOrderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001, // Unique request code for daily orders
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Set alarm for configured time
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, orderHour)
            set(Calendar.MINUTE, orderMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If it's already past the configured time today, schedule for tomorrow
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        // Cancel any existing alarm
        alarmManager.cancel(pendingIntent)
        
        // Schedule exact alarm at configured time
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
        
        // Save preference
        val sharedPrefs = getSharedPreferences("StocksMonitorPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(DailyOrderReceiver.PREF_DAILY_ORDER_ENABLED, true).apply()
        
        val timeFormat = String.format("%02d:%02d", orderHour, orderMinute)
        Logger.d("MainActivity", "Daily order scheduled for $timeFormat (next: ${calendar.time})")
        Snackbar.make(
            binding.root,
            "Daily order scheduled for $timeFormat (Mon-Fri)\nToken: ${DailyOrderReceiver.ORDER_TOKEN}, Qty: ${DailyOrderReceiver.ORDER_QUANTITY}",
            Snackbar.LENGTH_LONG
        ).show()
    }
    
    fun cancelDailyOrder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DailyOrderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        
        // Save preference
        val sharedPrefs = getSharedPreferences("StocksMonitorPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(DailyOrderReceiver.PREF_DAILY_ORDER_ENABLED, false).apply()
        
        Logger.d("MainActivity", "Daily order cancelled")
        Snackbar.make(binding.root, "Daily order cancelled", Snackbar.LENGTH_SHORT).show()
    }
    
    fun isDailyOrderEnabled(): Boolean {
        val sharedPrefs = getSharedPreferences("StocksMonitorPrefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(DailyOrderReceiver.PREF_DAILY_ORDER_ENABLED, false)
    }
    
    fun testDailyOrder() {
        // Trigger the receiver immediately for testing
        Logger.d("MainActivity", "Triggering test daily order")
        val intent = Intent(this, DailyOrderReceiver::class.java)
        sendBroadcast(intent)
        Snackbar.make(binding.root, "Test daily order triggered - check logs and notifications", Snackbar.LENGTH_LONG).show()
    }
}