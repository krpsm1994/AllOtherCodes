package com.example.stocksmonitor

import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class AngelOneWebSocket(
    private val angel_apiKey: String,
    private val angel_clientCode: String,
    private val angel_jwtToken: String,
    private val angel_feedToken: String,
    private val onQuoteUpdate: (String, Quote) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)  // Automatic ping every 25 seconds
        .build()
    var onError: ((String) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pingRunnable: Runnable? = null
    private val subscribedTokens = mutableSetOf<String>()
    private var reconnectAttempt = 0
    private var maxReconnectAttempts = 50
    private var reconnectDelayMs = 1000L  // Start with 1 second
    private var reconnectRunnable: Runnable? = null
    
    fun connect() {
        if (angel_apiKey.isBlank() || angel_clientCode.isBlank() || 
            angel_jwtToken.isBlank() || angel_feedToken.isBlank()) {
            Logger.e("AngelOneWebSocket", "connect() - Missing required credentials!")
            return
        }
        
        val url = "wss://smartapisocket.angelone.in/smart-stream"
        Logger.d("AngelOneWebSocket", "connect() - Connecting to: $url")
        Logger.d("AngelOneWebSocket", "connect() - Client Code: $angel_clientCode")
        Logger.d("AngelOneWebSocket", "connect() - API Key: $angel_apiKey")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $angel_jwtToken")
            .addHeader("x-api-key", angel_apiKey)
            .addHeader("x-client-code", angel_clientCode)
            .addHeader("x-feed-token", angel_feedToken)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Logger.d("AngelOneWebSocket", "WebSocket Connected successfully")
                resetReconnectCounter()  // Reset reconnect attempts on successful connection
                startHeartbeat()
                
                // Automatically resubscribe to previously subscribed tokens
                if (subscribedTokens.isNotEmpty()) {
                    Logger.d("AngelOneWebSocket", "Auto-resubscribing to ${subscribedTokens.size} tokens after reconnection")
                    handler.postDelayed({
                        subscribe(subscribedTokens.toList())
                    }, 1000)  // Wait 1 second after connection before subscribing
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Logger.d("AngelOneWebSocket", "Text message: $text")
                if (text == "pong") {
                    Logger.d("AngelOneWebSocket", "Heartbeat pong received")
                }
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                try {
                    parseAngelOneData(bytes.toByteArray())
                } catch (e: Exception) {
                    Logger.e("AngelOneWebSocket", "Error parsing data", e)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Logger.d("AngelOneWebSocket", "WebSocket closing: code=$code, reason=$reason")
                stopHeartbeat()
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Logger.d("AngelOneWebSocket", "WebSocket closed: code=$code, reason=$reason")
                stopHeartbeat()
                // Only reconnect if it's an unexpected closure (not code 1000 which is normal)
                if (code != 1000) {
                    Logger.d("AngelOneWebSocket", "Unexpected closure, scheduling reconnect")
                    scheduleReconnect()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Logger.e("AngelOneWebSocket", "WebSocket error: ${t.message}", t)
                response?.let {
                    Logger.e("AngelOneWebSocket", "Response code: ${it.code}, message: ${it.message}")
                    val errorMessage = it.header("x-error-message")
                    if (errorMessage != null) {
                        Logger.e("AngelOneWebSocket", "Error header: $errorMessage")
                    }
                }
                stopHeartbeat()
                onError?.invoke("${response?.code ?: "Unknown"}: ${t.message}")
                
                // Attempt to reconnect
                scheduleReconnect()
            }
        })
    }
    
    private fun startHeartbeat() {
        Logger.d("AngelOneWebSocket", "Starting heartbeat (ping every 25 seconds)")
        pingRunnable = object : Runnable {
            override fun run() {
                webSocket?.send("ping")
                Logger.d("AngelOneWebSocket", "Sent ping")
                handler.postDelayed(this, 25000) // 25 seconds
            }
        }
        handler.postDelayed(pingRunnable!!, 25000)
    }
    
    private fun stopHeartbeat() {
        pingRunnable?.let {
            handler.removeCallbacks(it)
            pingRunnable = null
        }
        Logger.d("AngelOneWebSocket", "Heartbeat stopped")
    }
    
    fun subscribe(instrumentTokens: List<String>) {
        if (webSocket == null) {
            Logger.e("AngelOneWebSocket", "subscribe() - WebSocket is null!")
            return
        }
        
        Logger.d("AngelOneWebSocket", "subscribe() - Subscribing to ${instrumentTokens.size} instruments")
        
        // Track subscribed tokens
        subscribedTokens.addAll(instrumentTokens)
        
        // AngelOne format: mode 1 = LTP, mode 2 = Quote, mode 3 = Snap Quote
        // Exchange type 1 = NSE_CM
        val tokenList = JSONArray()
        val exchangeObj = JSONObject().apply {
            put("exchangeType", 1) // NSE_CM
            put("tokens", JSONArray(instrumentTokens))
        }
        tokenList.put(exchangeObj)
        
        val params = JSONObject().apply {
            put("mode", 2) // Quote mode
            put("tokenList", tokenList)
        }
        
        val message = JSONObject().apply {
            put("correlationID", "stocks_${System.currentTimeMillis()}")
            put("action", 1) // Subscribe
            put("params", params)
        }
        
        val messageStr = message.toString()
        Logger.d("AngelOneWebSocket", "subscribe() - Sending: $messageStr")
        webSocket?.send(messageStr)
    }
    
    fun unsubscribe(instrumentTokens: List<String>) {
        if (webSocket == null) {
            Logger.e("AngelOneWebSocket", "unsubscribe() - WebSocket is null!")
            return
        }
        
        Logger.d("AngelOneWebSocket", "unsubscribe() - Unsubscribing from ${instrumentTokens.size} instruments")
        
        // Remove from tracked tokens
        subscribedTokens.removeAll(instrumentTokens.toSet())
        
        val tokenList = JSONArray()
        val exchangeObj = JSONObject().apply {
            put("exchangeType", 1)
            put("tokens", JSONArray(instrumentTokens))
        }
        tokenList.put(exchangeObj)
        
        val params = JSONObject().apply {
            put("mode", 2)
            put("tokenList", tokenList)
        }
        
        val message = JSONObject().apply {
            put("correlationID", "unsub_${System.currentTimeMillis()}")
            put("action", 0) // Unsubscribe
            put("params", params)
        }
        
        val messageStr = message.toString()
        Logger.d("AngelOneWebSocket", "unsubscribe() - Sending: $messageStr")
        webSocket?.send(messageStr)
    }
    
    fun unsubscribeAll() {
        if (subscribedTokens.isNotEmpty()) {
            Logger.d("AngelOneWebSocket", "unsubscribeAll() - Unsubscribing from all ${subscribedTokens.size} tokens")
            unsubscribe(subscribedTokens.toList())
            subscribedTokens.clear()
        }
    }
    
    private fun parseAngelOneData(data: ByteArray) {
        
        if (data.size < 51) {
            Logger.w("AngelOneWebSocket", "Insufficient data size: ${data.size}")
            return
        }
        
        try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            
            // Byte 0: Subscription mode (1=LTP, 2=Quote, 3=SnapQuote)
            val mode = buffer.get(0).toInt()
            
            // Byte 1: Exchange type
            val exchangeType = buffer.get(1).toInt()
            
            // Bytes 2-26: Token (25 bytes, null-terminated string)
            val tokenBytes = ByteArray(25)
            buffer.position(2)
            buffer.get(tokenBytes)
            val token = String(tokenBytes).trim('\u0000')
            
            // Bytes 27-34: Sequence number (long)
            buffer.position(27)
            val sequenceNumber = buffer.long
            
            // Bytes 35-42: Exchange timestamp (long)
            val exchangeTimestamp = buffer.long
            
            // Bytes 43-50: Last Traded Price (long, in paise)
            val ltpRaw = buffer.long
            val ltp = ltpRaw / 100.0
            Logger.d("AngelOneWebSocket", "Token: $token = LTP: $ltp")
            
            if (mode == 1) {
                // LTP mode - packet ends here (51 bytes)
                val quote = Quote(ltp, 0.0, ltp, 0.0, 0.0, 0.0, 0.0)
                onQuoteUpdate(token, quote)
                return
            }
            
            if (data.size < 123) {
                Logger.w("AngelOneWebSocket", "Insufficient data for Quote mode: ${data.size}")
                return
            }
            
            // Continue parsing for Quote mode (123 bytes total)
            // Bytes 51-58: Last traded quantity
            val lastTradedQty = buffer.long
            
            // Bytes 59-66: Average traded price
            val avgTradedPriceRaw = buffer.long
            val avgTradedPrice = avgTradedPriceRaw / 100.0
            
            // Bytes 67-74: Volume
            val volume = buffer.long
            
            // Bytes 75-82: Total buy quantity
            val totalBuyQty = buffer.double
            
            // Bytes 83-90: Total sell quantity
            val totalSellQty = buffer.double
            
            // Bytes 91-98: Open price
            val openRaw = buffer.long
            val open = openRaw / 100.0
            
            // Bytes 99-106: High price
            val highRaw = buffer.long
            val high = highRaw / 100.0
            
            // Bytes 107-114: Low price
            val lowRaw = buffer.long
            val low = lowRaw / 100.0
            
            // Bytes 115-122: Close price
            val closeRaw = buffer.long
            val close = closeRaw / 100.0
            
            // Calculate percent change
            val percentChange = if (close > 0) {
                ((ltp - close) / close) * 100
            } else {
                0.0
            }
            
           // Logger.d("AngelOneWebSocket", "Quote - Token: $token, LTP: $ltp, Change: $percentChange%, Open: $open, High: $high, Low: $low, Close: $close")
            
            val quote = Quote(ltp, percentChange, ltp, open, high, low, close)
            onQuoteUpdate(token, quote)
            
        } catch (e: Exception) {
            Logger.e("AngelOneWebSocket", "Error parsing AngelOne data", e)
        }
    }
    
    fun disconnect() {
        Logger.d("AngelOneWebSocket", "disconnect() - Closing WebSocket")
        stopHeartbeat()
        cancelReconnect()
        webSocket?.close(1000, "Client closing")
        webSocket = null
        reconnectAttempt = 0
    }
    
    private fun scheduleReconnect() {
        if (reconnectAttempt < maxReconnectAttempts) {
            reconnectAttempt++
            // Exponential backoff: 1s, 2s, 4s, 8s, etc., capped at 60s
            reconnectDelayMs = (1000L * Math.pow(2.0, (reconnectAttempt - 1).toDouble())).toLong()
            reconnectDelayMs = reconnectDelayMs.coerceAtMost(60000L)
            
            Logger.d("AngelOneWebSocket", "scheduleReconnect() - Attempt $reconnectAttempt/$maxReconnectAttempts in ${reconnectDelayMs}ms")
            
            cancelReconnect()
            reconnectRunnable = Runnable {
                Logger.d("AngelOneWebSocket", "Attempting to reconnect (attempt $reconnectAttempt/$maxReconnectAttempts)")
                connect()
            }
            handler.postDelayed(reconnectRunnable!!, reconnectDelayMs)
        } else {
            Logger.e("AngelOneWebSocket", "Max reconnection attempts reached ($maxReconnectAttempts)")
            onError?.invoke("Failed to reconnect after $maxReconnectAttempts attempts")
        }
    }
    
    private fun cancelReconnect() {
        reconnectRunnable?.let {
            handler.removeCallbacks(it)
            reconnectRunnable = null
        }
    }
    
    fun resetReconnectCounter() {
        reconnectAttempt = 0
        reconnectDelayMs = 1000L
        cancelReconnect()
    }
}


