package com.example.stocksmonitor

import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KiteWebSocket(
    private val apiKey: String,
    private val accessToken: String,
    private val onQuoteUpdate: (String, Quote) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    var onError: ((String) -> Unit)? = null
    
    fun connect() {
        if (apiKey.isBlank()) {
            Log.e("KiteWebSocket", "connect() - API Key is empty! Cannot connect.")
            return
        }
        
        if (accessToken.isBlank()) {
            Log.e("KiteWebSocket", "connect() - Access Token is empty! Cannot connect.")
            return
        }
        
        val url = "wss://ws.kite.trade?api_key=$apiKey&access_token=$accessToken"
        Log.d("KiteWebSocket", "connect() - API Key: $apiKey")
        Log.d("KiteWebSocket", "connect() - Access Token length: ${accessToken.length}")
        Log.d("KiteWebSocket", "connect() - Access Token: $accessToken")
        Log.d("KiteWebSocket", "connect() - Full WebSocket URL: $url")
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("KiteWebSocket", "WebSocket Connected")
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("KiteWebSocket", "Text message: $text")
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                try {
                    parseTickerData(bytes.toByteArray())
                } catch (e: Exception) {
                    Log.e("KiteWebSocket", "Error parsing ticker data", e)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("KiteWebSocket", "onClosing() - WebSocket closing: code=$code, reason=$reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("KiteWebSocket", "onClosed() - WebSocket closed: code=$code, reason=$reason")
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("KiteWebSocket", "onFailure() - WebSocket error: ${t.message}", t)
                response?.let {
                    Log.e("KiteWebSocket", "onFailure() - Response code: ${it.code}, message: ${it.message}")
                }
                onError?.invoke("${response?.code ?: "Unknown"}: ${t.message}")
            }
        })
    }
    
    fun subscribe(instrumentTokens: List<String>) {
        if (webSocket == null) {
            Log.e("KiteWebSocket", "subscribe() - WebSocket is null! Cannot subscribe.")
            return
        }
        
        Log.d("KiteWebSocket", "subscribe() - Request to subscribe to ${instrumentTokens.size} instruments")
        // Convert string tokens to integers
        val tokens = instrumentTokens.mapNotNull { it.toIntOrNull() }
        Log.d("KiteWebSocket", "subscribe() - Converted tokens: $tokens")
        
        val message = JSONObject().apply {
            put("a", "subscribe")
            put("v", JSONArray(tokens))
        }
        val messageStr = message.toString()
        Log.d("KiteWebSocket", "subscribe() - Sending message: $messageStr")
        webSocket?.send(messageStr)
        Log.d("KiteWebSocket", "subscribe() - Subscribed to: $tokens")
    }
    
    fun setMode(instrumentTokens: List<String>, mode: String = "quote") {
        if (webSocket == null) {
            Log.e("KiteWebSocket", "setMode() - WebSocket is null! Cannot set mode.")
            return
        }
        
        Log.d("KiteWebSocket", "setMode() - Request to set mode '$mode' for ${instrumentTokens.size} instruments")
        // Convert string tokens to integers
        val tokens = instrumentTokens.mapNotNull { it.toIntOrNull() }
        Log.d("KiteWebSocket", "setMode() - Converted tokens: $tokens")
        
        val message = JSONObject().apply {
            put("a", "mode")
            put("v", JSONArray().apply {
                put(mode)
                put(JSONArray(tokens))
            })
        }
        val messageStr = message.toString()
        Log.d("KiteWebSocket", "setMode() - Sending message: $messageStr")
        webSocket?.send(messageStr)
        Log.d("KiteWebSocket", "setMode() - Set mode to $mode for: $tokens")
    }
    
    private fun parseTickerData(data: ByteArray) {
        Log.d("KiteWebSocket", "parseTickerData() - Received ${data.size} bytes of ticker data")
        
        if (data.size < 2) {
            Log.w("KiteWebSocket", "parseTickerData() - Insufficient data")
            return
        }
        
        var offset = 0
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        
        // Read number of packets (first 2 bytes)
        val numberOfPackets = buffer.short.toInt()
        offset += 2
        Log.d("KiteWebSocket", "parseTickerData() - Number of packets: $numberOfPackets")
        
        // Process each packet
        for (packetIndex in 0 until numberOfPackets) {
            if (offset + 2 > data.size) break
            
            // Read packet length (next 2 bytes)
            buffer.position(offset)
            val packetLength = buffer.short.toInt()
            offset += 2
            
            if (offset + packetLength > data.size) {
                Log.w("KiteWebSocket", "parseTickerData() - Packet length exceeds data size")
                break
            }
            
            Log.d("KiteWebSocket", "parseTickerData() - Packet $packetIndex: length=$packetLength bytes")
            
            // Parse packet based on length
            buffer.position(offset)
            
            when {
                packetLength == 8 -> { // LTP mode
                    val instrumentToken = buffer.int
                    val ltp = buffer.int / 100.0
                    
                    Log.d("KiteWebSocket", "parseTickerData() - LTP mode: token=$instrumentToken, ltp=$ltp")
                    onQuoteUpdate(instrumentToken.toString(), Quote(ltp, 0.0, ltp, 0.0, 0.0, 0.0, 0.0))
                }
                packetLength == 44 -> { // Quote mode
                    val instrumentToken = buffer.int
                    val ltp = buffer.int / 100.0
                    val lastTradeQty = buffer.int
                    val avgTradePrice = buffer.int / 100.0
                    val volume = buffer.int
                    val buyQty = buffer.int
                    val sellQty = buffer.int
                    val open = buffer.int / 100.0
                    val high = buffer.int / 100.0
                    val low = buffer.int / 100.0
                    val close = buffer.int / 100.0
                    
                    // Calculate percent change
                    val percentChange = if (close > 0) {
                        ((ltp - close) / close) * 100
                    } else {
                        0.0
                    }
                    
                    Log.d("KiteWebSocket", "parseTickerData() - Quote mode: token=$instrumentToken, ltp=$ltp, change=$percentChange%, open=$open, high=$high, low=$low, close=$close")
                    
                    val quote = Quote(ltp, percentChange, ltp, open, high, low, close)
                    onQuoteUpdate(instrumentToken.toString(), quote)
                }
                packetLength == 184 -> { // Full mode with market depth
                    val instrumentToken = buffer.int
                    val ltp = buffer.int / 100.0
                    val lastTradeQty = buffer.int
                    val avgTradePrice = buffer.int / 100.0
                    val volume = buffer.int
                    val buyQty = buffer.int
                    val sellQty = buffer.int
                    val open = buffer.int / 100.0
                    val high = buffer.int / 100.0
                    val low = buffer.int / 100.0
                    val close = buffer.int / 100.0
                    
                    // Skip the rest of full mode data (last traded timestamp, OI, market depth, etc.)
                    // We only need basic quote data
                    
                    val percentChange = if (close > 0) {
                        ((ltp - close) / close) * 100
                    } else {
                        0.0
                    }
                    
                    Log.d("KiteWebSocket", "parseTickerData() - Full mode: token=$instrumentToken, ltp=$ltp, change=$percentChange%")
                    
                    val quote = Quote(ltp, percentChange, ltp, open, high, low, close)
                    onQuoteUpdate(instrumentToken.toString(), quote)
                }
                else -> {
                    Log.d("KiteWebSocket", "parseTickerData() - Unknown packet length: $packetLength, skipping")
                }
            }
            
            offset += packetLength
        }
        
        Log.d("KiteWebSocket", "parseTickerData() - Processed $numberOfPackets packets")
    }
    
    fun disconnect() {
        Log.d("KiteWebSocket", "disconnect() - Closing WebSocket connection")
        webSocket?.close(1000, "Closing connection")
        webSocket = null
    }
}
