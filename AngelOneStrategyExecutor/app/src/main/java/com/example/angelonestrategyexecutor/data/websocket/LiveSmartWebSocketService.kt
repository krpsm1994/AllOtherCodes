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
 * Dedicated WebSocket service for the live watchlist strategy.
 *
 * Subscribes in SNAP_QUOTE mode (mode = 3) to receive last-traded-price PLUS
 * cumulative-day-volume data needed for real-time 15-min OHLCV candle construction.
 *
 * SNAP_QUOTE binary packet layout (minimum 75 bytes needed for volume field):
 *  Offset  Bytes  Type        Description
 *  ------  -----  ----------  --------------------------------
 *   0       1     uint8       Subscription mode (3 = SNAP_QUOTE)
 *   1       1     uint8       Exchange type
 *   2      25     UTF-8       Instrument token (null-terminated)
 *  27       8     int64 LE    Sequence number
 *  35       8     int64 LE    Exchange timestamp (epoch seconds)
 *  43       8     int64 LE    LTP (paise; divide by 100 for ₹)
 *  51       8     int64 LE    Last traded quantity           ← 8 bytes, NOT 4!
 *  59       8     int64 LE    Average traded price (paise)
 *  67       8     int64 LE    Volume traded today (cumulative)   ← needed for volume
 *  ...     ...    ...         (further OHLC / OI / band fields)
 *
 * Note: If fewer than 75 bytes are received, cumulativeDayVolume
 * is reported as -1 and candle volume tracking will be unavailable for that tick.
 */
class LiveSmartWebSocketService {

    companion object {
        private const val TAG = "LiveSmartWS"
        private const val WS_URL = "wss://smartapisocket.angelone.in/smart-stream"
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val MAX_RECONNECT_ATTEMPTS = 10

        const val EXCHANGE_NSE_CM = 1
        const val EXCHANGE_NSE_FO = 2
        const val EXCHANGE_BSE_CM = 3
        const val EXCHANGE_BSE_FO = 4

        private const val MODE_SNAP_QUOTE = 3

        /** Minimum packet size that includes the cumulative volume field (offset 63–70). */
        private const val SNAP_QUOTE_MIN_BYTES_FOR_VOLUME = 75
    }

    // ── Public data structures ─────────────────────────────────────────────────

    /**
     * Price + volume tick delivered on every instrument update.
     *
     * @param token             Instrument token string.
     * @param exchangeType      AngelOne exchange-type integer (1 = NSE CM, 2 = NSE F&O, 3 = BSE CM …).
     * @param ltp               Last traded price in ₹ (rupees).
     * @param cumulativeDayVolume  Total volume traded today since market open; -1 if unavailable.
     */
    data class SnapQuoteTick(
        val token: String,
        val exchangeType: Int,
        val ltp: Double,
        val cumulativeDayVolume: Long,
    )

    interface TickCallback {
        fun onTick(tick: SnapQuoteTick)
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onError(error: String)
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var heartbeatTimer: Timer? = null
    private var callback: TickCallback? = null
    private var reconnectAttempts = 0
    private var isManualDisconnect = false

    /** exchangeType → set of subscribed tokens. Kept for re-subscribe after reconnect. */
    private val activeSubscriptions = mutableMapOf<Int, MutableSet<String>>()

    // ── Public API ─────────────────────────────────────────────────────────────

    fun setCallback(cb: TickCallback) {
        callback = cb
    }

    fun connect() {
        val creds = AuthState.credentials.value ?: run {
            callback?.onError("Not logged in – cannot open live candle WebSocket")
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

        Log.d(TAG, "Connecting for SNAP_QUOTE (live candle) streaming…")
        webSocket = okHttpClient.newWebSocket(request, createListener())
    }

    fun subscribe(exchangeType: Int, tokens: List<String>) {
        if (tokens.isEmpty()) return
        activeSubscriptions.getOrPut(exchangeType) { mutableSetOf() }.addAll(tokens)
        sendFrame(exchangeType, tokens, action = 1)
    }

    fun unsubscribe(exchangeType: Int, tokens: List<String>) {
        if (tokens.isEmpty()) return
        activeSubscriptions[exchangeType]?.removeAll(tokens.toSet())
        sendFrame(exchangeType, tokens, action = 0)
    }

    fun disconnect() {
        isManualDisconnect = true
        stopHeartbeat()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        activeSubscriptions.clear()
        Log.d(TAG, "Disconnected from live-candle WebSocket")
    }

    val isConnected: Boolean get() = webSocket != null

    // ── Internal ───────────────────────────────────────────────────────────────

    private fun sendFrame(exchangeType: Int, tokens: List<String>, action: Int) {
        // Build the JSON payload using a plain Map so we avoid depending on the private
        // SubscribeRequest classes in SmartWebSocketService.
        val payload = mapOf(
            "correlationID" to "lw_${System.currentTimeMillis()}",
            "action" to action,
            "params" to mapOf(
                "mode" to MODE_SNAP_QUOTE,
                "tokenList" to listOf(
                    mapOf("exchangeType" to exchangeType, "tokens" to tokens)
                )
            )
        )
        webSocket?.send(gson.toJson(payload))
        Log.d(TAG, "${if (action == 1) "Sub" else "Unsub"}scribed SNAP_QUOTE: ${tokens.size} tokens on exchange=$exchangeType")
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            Log.d(TAG, "Connected")
            reconnectAttempts = 0
            startHeartbeat()
            callback?.onConnected()
            resubscribeAll()
        }

        override fun onMessage(ws: WebSocket, text: String) {
            if (text == "pong") return
            try {
                @Suppress("UNCHECKED_CAST")
                val map = gson.fromJson(text, Map::class.java) as? Map<String, Any>
                val errorCode = map?.get("errorCode") as? String
                if (!errorCode.isNullOrBlank()) {
                    val msg = map?.get("errorMessage") as? String ?: ""
                    Log.e(TAG, "WS error response: $errorCode – $msg")
                    callback?.onError("$errorCode: $msg")
                }
            } catch (_: Exception) {}
        }

        override fun onMessage(ws: WebSocket, bytes: ByteString) {
            parsePacket(bytes.toByteArray())
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            ws.close(1000, null)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closed: $code – $reason")
            stopHeartbeat()
            this@LiveSmartWebSocketService.webSocket = null
            callback?.onDisconnected("Closed ($code): $reason")
            if (!isManualDisconnect) scheduleReconnect()
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Failure: ${t.message}", t)
            stopHeartbeat()
            this@LiveSmartWebSocketService.webSocket = null
            callback?.onError(t.message ?: "WebSocket connection failure")
            if (!isManualDisconnect) scheduleReconnect()
        }
    }

    private fun parsePacket(data: ByteArray) {
        if (data.size < 51) {
            Log.w(TAG, "Packet too small to parse: ${data.size} bytes")
            return
        }
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val exchangeType = buf.get(1).toInt() and 0xFF

        val tokenBytes = ByteArray(25)
        System.arraycopy(data, 2, tokenBytes, 0, 25)
        val token = String(tokenBytes, Charsets.UTF_8).trimEnd('\u0000').trim()

        val ltp = buf.getLong(43) / 100.0

        val cumulativeVolume: Long = if (data.size >= SNAP_QUOTE_MIN_BYTES_FOR_VOLUME) {
            buf.getLong(67)
        } else {
            -1L
        }

        Log.v(TAG, "Tick: token=$token, ltp=$ltp, cumulativeVol=$cumulativeVolume, exchType=$exchangeType")
        callback?.onTick(SnapQuoteTick(token, exchangeType, ltp, cumulativeVolume))
    }

    private fun resubscribeAll() {
        activeSubscriptions.forEach { (exchType, tokens) ->
            if (tokens.isNotEmpty()) sendFrame(exchType, tokens.toList(), action = 1)
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = Timer("lw-heartbeat", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try { webSocket?.send("ping") } catch (e: Exception) {
                        Log.e(TAG, "Heartbeat error: ${e.message}")
                    }
                }
            }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS)
        }
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached")
            callback?.onError("Max reconnect attempts reached – please restart the service")
            return
        }
        reconnectAttempts++
        val delay = RECONNECT_DELAY_MS * reconnectAttempts
        Log.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")
        Timer("lw-reconnect", true).schedule(object : TimerTask() {
            override fun run() {
                if (!isManualDisconnect && webSocket == null) connect()
            }
        }, delay)
    }
}
