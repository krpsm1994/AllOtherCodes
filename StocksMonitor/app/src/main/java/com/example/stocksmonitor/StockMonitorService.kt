package com.example.stocksmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

class StockMonitorService : Service() {
    
    private var angelWebSocket: AngelOneWebSocket? = null
    private lateinit var stockManager: StockManager
    private lateinit var logManager: LogManager
    private lateinit var settingsManager: SettingsManager
    private var kiteApiKey: String = ""
    private var kiteAccessToken: String = ""
    private val orderPollingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var orderPollingRunnable: Runnable? = null
    
    companion object {
        private const val CHANNEL_ID = "StockMonitorChannel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESUBSCRIBE = "ACTION_RESUBSCRIBE"
        const val EXTRA_ANGEL_API_KEY = "EXTRA_ANGEL_API_KEY"
        const val EXTRA_ANGEL_CLIENT_CODE = "EXTRA_ANGEL_CLIENT_CODE"
        const val EXTRA_ANGEL_JWT_TOKEN = "EXTRA_ANGEL_JWT_TOKEN"
        const val EXTRA_ANGEL_FEED_TOKEN = "EXTRA_ANGEL_FEED_TOKEN"
        const val EXTRA_KITE_API_KEY = "EXTRA_KITE_API_KEY"
        const val EXTRA_KITE_ACCESS_TOKEN = "EXTRA_KITE_ACCESS_TOKEN"
        const val ACTION_STOCK_UPDATED = "com.example.stocksmonitor.STOCK_UPDATED"
        
        var quotesCache = mutableMapOf<String, Quote>()
        var onQuoteUpdateListener: ((String, Quote) -> Unit)? = null
        var onStockStatusChanged: (() -> Unit)? = null
    }
    
    override fun onCreate() {
        super.onCreate()
        stockManager = StockManager(this)
        logManager = LogManager(this)
        settingsManager = SettingsManager(this)
        
        // Load Kite credentials
        val prefs = getSharedPreferences("StocksMonitorPrefs", Context.MODE_PRIVATE)
        kiteAccessToken = prefs.getString("kite_access_token", "") ?: ""
        kiteApiKey = "" // Will be set from constants when needed
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val apiKey = intent.getStringExtra(EXTRA_ANGEL_API_KEY) ?: return START_NOT_STICKY
                val clientCode = intent.getStringExtra(EXTRA_ANGEL_CLIENT_CODE) ?: return START_NOT_STICKY
                val jwtToken = intent.getStringExtra(EXTRA_ANGEL_JWT_TOKEN) ?: return START_NOT_STICKY
                val feedToken = intent.getStringExtra(EXTRA_ANGEL_FEED_TOKEN) ?: return START_NOT_STICKY
                
                // Get Kite credentials (optional)
                kiteApiKey = intent.getStringExtra(EXTRA_KITE_API_KEY) ?: ""
                kiteAccessToken = intent.getStringExtra(EXTRA_KITE_ACCESS_TOKEN) ?: ""
                
                startForegroundService(apiKey, clientCode, jwtToken, feedToken)
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
            ACTION_RESUBSCRIBE -> {
                Logger.d("StockMonitorService", "onStartCommand() - ACTION_RESUBSCRIBE received")
                resubscribeToStocks()
            }
        }
        
        return START_STICKY
    }
    
    private fun startForegroundService(angel_apiKey: String, angel_clientCode: String, 
                                      angel_jwtToken: String, angel_feedToken: String) {
        Logger.d("StockMonitorService", "startForegroundService() - Starting service")
        Logger.d("StockMonitorService", "startForegroundService() - API Key: $angel_apiKey")
        Logger.d("StockMonitorService", "startForegroundService() - Client Code: $angel_clientCode")
        Logger.d("StockMonitorService", "startForegroundService() - JWT Token length: ${angel_jwtToken.length}")
        
        // Check if WebSocket is already initialized
        if (angelWebSocket != null) {
            Logger.d("StockMonitorService", "startForegroundService() - WebSocket already initialized, just resubscribing")
            subscribeToSavedStocks()
            return
        }
        
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            Logger.d("StockMonitorService", "startForegroundService() - Foreground service started")
        } catch (e: Exception) {
            Logger.e("StockMonitorService", "Failed to start foreground service", e)
        }
        
        // Initialize AngelOne WebSocket
        Logger.d("StockMonitorService", "startForegroundService() - Creating AngelOneWebSocket instance")
        angelWebSocket = AngelOneWebSocket(angel_apiKey, angel_clientCode, angel_jwtToken, angel_feedToken) { token, quote ->
            quotesCache[token] = quote
            onQuoteUpdateListener?.invoke(token, quote)
            
            // Update portfolio holdings with new price
            val portfolioManager = com.example.stocksmonitor.PortfolioManager(this)
            val holdings = portfolioManager.getHoldings()
            val allInstruments = stockManager.getAllExchangesStocks()
            
            for (holding in holdings) {
                val instrument = allInstruments.find { 
                    it.instrumentToken == token && it.tradingSymbol == holding.symbol 
                }
                if (instrument != null) {
                    val updatedHolding = portfolioManager.updateHoldingPrice(holding.symbol, quote.ltp)
                    if (updatedHolding != null) {
                        Logger.d("StockMonitorService", "Updated portfolio holding ${holding.symbol}: LTP=${quote.ltp}, P&L=${updatedHolding.pnl}")
                    }
                }
            }
            
            // Check and place orders in background
            checkAndPlaceOrders(token, quote)
        }
        
        angelWebSocket?.onError = { error ->
            Logger.e("StockMonitorService", "WebSocket error: $error")
            if (error.contains("401") || error.contains("403")) {
                Logger.e("StockMonitorService", "Authentication failed - stopping service")
                stopForegroundService()
            }
        }
        
        angelWebSocket?.connect()
        
        // Subscribe to all saved stocks
        subscribeToSavedStocks()
        
        // Start order polling if we have Kite credentials
        if (kiteApiKey.isNotEmpty() && kiteAccessToken.isNotEmpty()) {
            startOrderPolling()
        }
    }
    
    private fun subscribeToSavedStocks() {
        // Wait a bit for WebSocket to connect
        android.os.Handler(mainLooper).postDelayed({
            val stocks = stockManager.getAllStocks()
            val portfolioManager = com.example.stocksmonitor.PortfolioManager(this)
            val holdings = portfolioManager.getHoldings()
            
            // Collect all instrument tokens to subscribe to
            val instrumentTokens = mutableListOf<String>()
            
            if (stocks.isNotEmpty()) {
                // Only subscribe to active stocks (not in history)
                val activeStocks = stocks.filter { it.status != StockStatus.HISTORY }
                instrumentTokens.addAll(activeStocks.map { it.instrument.instrumentToken })
            }
            
            // Add portfolio holdings by looking up their instrument tokens
            if (holdings.isNotEmpty()) {
                val allInstruments = stockManager.getAllExchangesStocks()
                holdings.forEach { holding ->
                    val instrument = allInstruments.find { 
                        it.tradingSymbol == holding.symbol 
                    }
                    if (instrument != null && instrument.instrumentToken !in instrumentTokens) {
                        instrumentTokens.add(instrument.instrumentToken)
                    }
                }
            }
            
            if (instrumentTokens.isNotEmpty()) {
                // First unsubscribe from all (in case previous subscription had different stocks)
                angelWebSocket?.unsubscribeAll()
                
                // Then subscribe to active stocks and portfolio holdings
                angelWebSocket?.subscribe(instrumentTokens)
                Logger.d("StockMonitorService", "Subscribed to ${instrumentTokens.size} instruments (${stocks.filter { it.status != StockStatus.HISTORY }.size} active stocks + ${holdings.size} portfolio holdings)")
            } else {
                // No active stocks or holdings, unsubscribe from all
                angelWebSocket?.unsubscribeAll()
                Logger.d("StockMonitorService", "No stocks or holdings to subscribe to")
            }
        }, 2000)
    }
    
    fun resubscribeToStocks() {
        subscribeToSavedStocks()
    }
    
    private fun checkAndPlaceOrders(token: String, quote: Quote) {
        // Check if auto-orders is enabled
        if (!settingsManager.isAutoOrdersEnabled()) {
            return
        }
        
        // Find stock by instrument token
        val stocks = stockManager.getAllStocks()
        val stock = stocks.find { it.instrument.instrumentToken == token } ?: return
        
        // Only process NOT_TRIGGERED stocks
        if (stock.status != StockStatus.NOT_TRIGGERED) return
        
        // Only process if not watch-only
        if (stock.onlyWatch) return
        
        // Check if LTP >= buyPrice
        val ltp = quote.ltp
        if (ltp >= stock.buyPrice) {
            Logger.d("StockMonitorService", "🔔 Order trigger detected for ${stock.instrument.tradingSymbol} - LTP: $ltp >= BuyPrice: ${stock.buyPrice}")
            
            // Update status to ORDER_PLACED immediately
            val orderPlacingStock = stock.copy(status = StockStatus.ORDER_PLACED, orderId = "PENDING")
            stockManager.updateStock(stock, orderPlacingStock)
            
            // Check if we have Kite credentials
            if (kiteAccessToken.isEmpty()) {
                Logger.e("StockMonitorService", "No Kite access token available")
                // Revert status
                val revertedStock = stock.copy(status = StockStatus.NOT_TRIGGERED, orderId = "")
                stockManager.updateStock(orderPlacingStock, revertedStock)
                return
            }
            
            if (kiteApiKey.isEmpty()) {
                Logger.e("StockMonitorService", "No Kite API key available")
                // Revert status
                val revertedStock = stock.copy(status = StockStatus.NOT_TRIGGERED, orderId = "")
                stockManager.updateStock(orderPlacingStock, revertedStock)
                return
            }
            
            // Place order
            placeOrderInBackground(orderPlacingStock)
        }
    }
    
    private fun placeOrderInBackground(stock: Stock) {
        Logger.d("StockMonitorService", "Placing order for ${stock.instrument.tradingSymbol}")
        
        KiteOrderManager.placeOrder(
            kiteApiKey,
            kiteAccessToken,
            stock.instrument.tradingSymbol,
            stock.instrument.exchange,
            stock.buyPrice,
            stock.quantity
        ) { orderId, error ->
            if (error != null) {
                Logger.e("StockMonitorService", "Failed to place order: $error")
                
                // Revert status
                val revertedStock = stock.copy(status = StockStatus.NOT_TRIGGERED, orderId = "")
                stockManager.updateStock(stock, revertedStock)
                
                logManager.addLog(LogType.STATUS_CHANGE, stock.instrument.tradingSymbol, "Order failed: $error")
            } else if (orderId != null) {
                Logger.d("StockMonitorService", "✅ Order placed successfully! Order ID: $orderId")
                
                // Update with actual order ID
                val updatedStock = stock.copy(orderId = orderId)
                stockManager.updateStock(stock, updatedStock)
                
                logManager.addLog(LogType.STOCK_ADDED, stock.instrument.tradingSymbol, "Order placed: $orderId")
            }
        }
    }
    
    private fun startOrderPolling() {
        val pollingIntervalSeconds = settingsManager.getOrderPollingInterval()
        val pollingIntervalMs = (pollingIntervalSeconds * 1000).toLong()
        Logger.d("StockMonitorService", "Starting order polling (every $pollingIntervalSeconds seconds)")
        
        orderPollingRunnable = object : Runnable {
            override fun run() {
                pollOrderStatus()
                // Schedule next poll
                orderPollingHandler.postDelayed(this, pollingIntervalMs)
            }
        }
        
        // Start first poll after 5 seconds
        orderPollingHandler.postDelayed(orderPollingRunnable!!, 5000)
    }
    
    private fun stopOrderPolling() {
        orderPollingRunnable?.let {
            orderPollingHandler.removeCallbacks(it)
        }
        orderPollingRunnable = null
        Logger.d("StockMonitorService", "Stopped order polling")
    }
    
    private fun pollOrderStatus() {
        Logger.d("StockMonitorService", "Polling order status...")
        
        // Check if we have credentials
        if (kiteApiKey.isEmpty() || kiteAccessToken.isEmpty()) {
            Logger.w("StockMonitorService", "No Kite credentials for order polling")
            return
        }
        
        // Get stocks with ORDER_PLACED status
        val orderPlacedStocks = stockManager.getAllStocks().filter {
            it.status == StockStatus.ORDER_PLACED && it.orderId.isNotEmpty()
        }
        
        if (orderPlacedStocks.isEmpty()) {
            Logger.d("StockMonitorService", "No pending orders to check")
            return
        }
        
        Logger.d("StockMonitorService", "Checking status for ${orderPlacedStocks.size} pending order(s)")
        
        // Fetch orders from Kite API
        fetchKiteOrders { orders, error ->
            if (error != null) {
                Logger.e("StockMonitorService", "Failed to fetch orders: $error")
                return@fetchKiteOrders
            }
            
            checkAndUpdateOrderPlacedStocks(orders)
        }
    }
    
    private fun fetchKiteOrders(callback: (List<Order>, String?) -> Unit) {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("https://api.kite.trade/orders")
            .addHeader("Authorization", "token $kiteApiKey:$kiteAccessToken")
            .addHeader("X-Kite-Version", "3")
            .build()
        
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Logger.e("StockMonitorService", "API call failed", e)
                callback(emptyList(), "Network error: ${e.message}")
            }
            
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string() ?: ""
                
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
                            
                            callback(orders, null)
                        } else {
                            val message = jsonResponse.optString("message", "Unknown error")
                            callback(emptyList(), message)
                        }
                    } else {
                        callback(emptyList(), "Error: HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    Logger.e("StockMonitorService", "Error parsing response", e)
                    callback(emptyList(), "Error parsing response: ${e.message}")
                }
            }
        })
    }
    
    private fun checkAndUpdateOrderPlacedStocks(orders: List<Order>) {
        Logger.d("StockMonitorService", "checkAndUpdateOrderPlacedStocks() - Checking ${orders.size} orders")
        
        val orderPlacedStocks = stockManager.getAllStocks().filter {
            it.status == StockStatus.ORDER_PLACED && it.orderId.isNotEmpty()
        }
        
        if (orderPlacedStocks.isEmpty()) {
            Logger.d("StockMonitorService", "No stocks with ORDER_PLACED status to check")
            return
        }
        
        Logger.d("StockMonitorService", "Found ${orderPlacedStocks.size} stock(s) with ORDER_PLACED status")
        
        orderPlacedStocks.forEach { stock ->
            Logger.d("StockMonitorService", "Checking stock: ${stock.instrument.tradingSymbol} (Order ID: ${stock.orderId})")
            
            val matchingOrder = orders.find { it.orderId == stock.orderId }
            
            if (matchingOrder != null) {
                val orderStatus = matchingOrder.status.uppercase()
                Logger.d("StockMonitorService", "Found matching order - Status: '$orderStatus' (raw: '${matchingOrder.status}')")
                
                when {
                    orderStatus.contains("COMPLETE") || orderStatus == "COMPLETE" -> {
                        Logger.d("StockMonitorService", "✓ Order COMPLETED for ${stock.instrument.tradingSymbol}")
                        val triggeredStock = stock.copy(status = StockStatus.TRIGGERED)
                        stockManager.updateStock(stock, triggeredStock)
                        logManager.addLog(LogType.STATUS_CHANGE, stock.instrument.tradingSymbol, "Order filled")
                        
                        // Notify MainActivity to refresh UI
                        onStockStatusChanged?.invoke()
                        sendBroadcast(Intent(ACTION_STOCK_UPDATED))
                    }
                    orderStatus.contains("REJECT") || orderStatus.contains("CANCEL") -> {
                        Logger.d("StockMonitorService", "✗ Order FAILED for ${stock.instrument.tradingSymbol}: $orderStatus - Moving to HISTORY")
                        val failedStock = stock.copy(
                            status = StockStatus.HISTORY,
                            orderId = ""
                        )
                        stockManager.updateStock(stock, failedStock)
                        logManager.addLog(LogType.STATUS_CHANGE, stock.instrument.tradingSymbol, "Order $orderStatus - Moved to history")
                        
                        // Notify MainActivity to refresh UI
                        onStockStatusChanged?.invoke()
                        sendBroadcast(Intent(ACTION_STOCK_UPDATED))
                    }
                    else -> {
                        Logger.d("StockMonitorService", "⏳ Order pending for ${stock.instrument.tradingSymbol}: $orderStatus")
                    }
                }
            } else {
                Logger.w("StockMonitorService", "No matching order found for ${stock.instrument.tradingSymbol} (Order ID: ${stock.orderId})")
            }
        }
    }
    
    private fun stopForegroundService() {
        stopOrderPolling()
        angelWebSocket?.disconnect()
        angelWebSocket = null
        stopForeground(true)
        stopSelf()
    }
    
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Stock Monitor")
            .setContentText("Monitoring stock prices in real-time")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stock Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps stock price monitoring running in background"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        angelWebSocket?.disconnect()
    }
}

