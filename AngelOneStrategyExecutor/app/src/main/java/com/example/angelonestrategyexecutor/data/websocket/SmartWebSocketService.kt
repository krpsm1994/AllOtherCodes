package com.example.angelonestrategyexecutor.data.websocket

import android.util.Log
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

/**
 * AngelOne SmartStream WebSocket 2.0 service.
 *
 * Connects to `wss://smartapisocket.angelone.in/smart-stream`,
 * subscribes to instrument tokens in LTP mode (mode = 1),
 * parses binary responses, and delivers LTP via [LtpCallback].
 */
class SmartWebSocketService {

    companion object {
        private const val TAG = "SmartWS"
        private const val WS_URL = "wss://smartapisocket.angelone.in/smart-stream"
        private const val HEARTBEAT_INTERVAL_MS = 25_000L  // 25s (< 30s requirement)
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val MAX_RECONNECT_ATTEMPTS = 10

        // Exchange types
        const val EXCHANGE_NSE_CM = 1   // NSE Cash (equity)
        const val EXCHANGE_NSE_FO = 2   // NSE F&O (options/futures)
        const val EXCHANGE_BSE_CM = 3
        const val EXCHANGE_BSE_FO = 4
        const val EXCHANGE_MCX_FO = 5

        // Subscription modes
        const val MODE_LTP = 1
        const val MODE_QUOTE = 2
        const val MODE_SNAP_QUOTE = 3
    }

    /** Callback for LTP updates */
    interface LtpCallback {
        fun onLtpUpdate(token: String, exchangeType: Int, ltp: Double)
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onError(error: String)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // no timeout for WebSocket
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var heartbeatTimer: Timer? = null
    private var callback: LtpCallback? = null
    private var reconnectAttempts = 0
    private var isManualDisconnect = false

    // Track current subscriptions for re-subscribe on reconnect
    private val activeSubscriptions = mutableMapOf<Int, MutableSet<String>>() // exchangeType -> set of tokens

    fun setCallback(callback: LtpCallback) {
        this.callback = callback
    }

    /**
     * Connect to the AngelOne SmartStream WebSocket.
     * Requires [AuthState] to have valid credentials (call after login).
     */
    fun connect() {
        val creds = AuthState.credentials.value
        if (creds == null) {
            callback?.onError("Not logged in. Please login first.")
            return
        }

        isManualDisconnect = false
        reconnectAttempts = 0

        val request = Request.Builder()
            .url(WS_URL)
            .addHeader("Authorization", "Bearer ${creds.jwtToken}")
            .addHeader("x-api-key", creds.apiKey)
            .addHeader("x-client-code", creds.clientCode)
            .addHeader("x-feed-token", creds.feedToken)
            .build()

        Log.d(TAG, "Connecting to WebSocket...")
        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    /**
     * Disconnect from the WebSocket.
     */
    fun disconnect() {
        isManualDisconnect = true
        stopHeartbeat()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        Log.d(TAG, "Disconnected from WebSocket")
    }

    /**
     * Subscribe to tokens for LTP updates.
     *
     * @param exchangeType Exchange type (e.g., [EXCHANGE_NSE_CM], [EXCHANGE_NSE_FO])
     * @param tokens List of instrument tokens (as strings)
     */
    fun subscribeLtp(exchangeType: Int, tokens: List<String>) {
        if (tokens.isEmpty()) return

        // Track subscriptions
        activeSubscriptions.getOrPut(exchangeType) { mutableSetOf() }.addAll(tokens)

        val request = SubscribeRequest(
            correlationID = "sub_${System.currentTimeMillis()}",
            action = 1,  // Subscribe
            params = SubscribeParams(
                mode = MODE_LTP,
                tokenList = listOf(
                    TokenListEntry(exchangeType = exchangeType, tokens = tokens)
                )
            )
        )

        val json = gson.toJson(request)
        Log.d(TAG, "Subscribing LTP: $json")
        webSocket?.send(json)
    }

    /**
     * Unsubscribe from tokens.
     */
    fun unsubscribeLtp(exchangeType: Int, tokens: List<String>) {
        if (tokens.isEmpty()) return

        activeSubscriptions[exchangeType]?.removeAll(tokens.toSet())

        val request = SubscribeRequest(
            correlationID = "unsub_${System.currentTimeMillis()}",
            action = 0,  // Unsubscribe
            params = SubscribeParams(
                mode = MODE_LTP,
                tokenList = listOf(
                    TokenListEntry(exchangeType = exchangeType, tokens = tokens)
                )
            )
        )

        val json = gson.toJson(request)
        Log.d(TAG, "Unsubscribing LTP: $json")
        webSocket?.send(json)
    }

    val isConnected: Boolean get() = webSocket != null

    // ──────────────────────────────────────────────────────────────────────────

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            reconnectAttempts = 0
            startHeartbeat()
            callback?.onConnected()

            // Re-subscribe to any active tokens
            resubscribeAll()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Text message: $text")
            // Could be a pong or error JSON
            if (text == "pong") return

            // Try to parse as error
            try {
                val error = gson.fromJson(text, WsErrorResponse::class.java)
                if (error.errorCode != null) {
                    Log.e(TAG, "WS error: ${error.errorCode} – ${error.errorMessage}")
                    callback?.onError("${error.errorCode}: ${error.errorMessage}")
                }
            } catch (_: Exception) { }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            parseBinaryLtp(bytes.toByteArray())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code – $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code – $reason")
            stopHeartbeat()
            this@SmartWebSocketService.webSocket = null
            callback?.onDisconnected("Closed ($code): $reason")

            if (!isManualDisconnect) {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}", t)
            stopHeartbeat()
            this@SmartWebSocketService.webSocket = null
            callback?.onError(t.message ?: "WebSocket connection failed")

            if (!isManualDisconnect) {
                scheduleReconnect()
            }
        }
    }

    /**
     * Parse a binary LTP packet (Little-Endian).
     *
     * Layout (LTP mode, 51 bytes):
     *   [0]       Subscription Mode (1 byte)
     *   [1]       Exchange Type     (1 byte)
     *   [2-26]    Token             (25 bytes, null-terminated UTF-8)
     *   [27-34]   Sequence Number   (int64 LE)
     *   [35-42]   Exchange Timestamp(int64 LE)
     *   [43-50]   LTP              (int64 LE, value in paise ÷ 100)
     */
    private fun parseBinaryLtp(data: ByteArray) {
        if (data.size < 51) {
            Log.w(TAG, "Binary packet too small: ${data.size} bytes")
            return
        }

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val mode = buf.get(0).toInt() and 0xFF
        val exchangeType = buf.get(1).toInt() and 0xFF

        // Token: 25 bytes starting at offset 2, null-terminated
        val tokenBytes = ByteArray(25)
        System.arraycopy(data, 2, tokenBytes, 0, 25)
        val token = String(tokenBytes, Charsets.UTF_8).trimEnd('\u0000').trim()

        // LTP at offset 43 (8 bytes, int64 LE) — divide by 100 for rupees
        val ltpRaw = buf.getLong(43)
        val ltp = ltpRaw / 100.0

        Log.d(TAG, "LTP tick: token=$token, exchange=$exchangeType, ltp=$ltp (raw=$ltpRaw)")

        callback?.onLtpUpdate(token, exchangeType, ltp)
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = Timer("ws-heartbeat", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        webSocket?.send("ping")
                    } catch (e: Exception) {
                        Log.e(TAG, "Heartbeat failed: ${e.message}")
                    }
                }
            }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS)
        }
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached ($MAX_RECONNECT_ATTEMPTS)")
            callback?.onError("Max reconnect attempts reached. Please restart.")
            return
        }

        reconnectAttempts++
        val delay = RECONNECT_DELAY_MS * reconnectAttempts
        Log.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")

        Timer("ws-reconnect", true).schedule(object : TimerTask() {
            override fun run() {
                if (!isManualDisconnect && webSocket == null) {
                    connect()
                }
            }
        }, delay)
    }

    private fun resubscribeAll() {
        activeSubscriptions.forEach { (exchangeType, tokens) ->
            if (tokens.isNotEmpty()) {
                subscribeLtp(exchangeType, tokens.toList())
            }
        }
    }

    // ── JSON models for subscribe/unsubscribe requests ────────────────────────

    private data class SubscribeRequest(
        val correlationID: String,
        val action: Int,
        val params: SubscribeParams,
    )

    private data class SubscribeParams(
        val mode: Int,
        val tokenList: List<TokenListEntry>,
    )

    private data class TokenListEntry(
        val exchangeType: Int,
        val tokens: List<String>,
    )

    private data class WsErrorResponse(
        val correlationID: String?,
        val errorCode: String?,
        val errorMessage: String?,
    )
}
