package com.example.angelonestrategyexecutor.data.websocket

import android.util.Log
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for AngelOne Order Status updates.
 *
 * Connects to `wss://tns.angelone.in/smart-order-update`
 * with `Authorization: Bearer <jwt>` header.
 *
 * Receives JSON messages with order status updates
 * (AB00=connected, AB01=open, AB02=cancelled, AB03=rejected,
 *  AB04=modified, AB05=complete, AB09=open-pending, AB10=trigger-pending).
 *
 * Sends ping every 10s to keep connection alive.
 */
class OrderWebSocketService {

    companion object {
        private const val TAG = "OrderWS"
        private const val WS_URL = "wss://tns.angelone.in/smart-order-update"
        private const val PING_INTERVAL_MS = 10_000L   // spec says every 10s
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val MAX_RECONNECT_ATTEMPTS = 10

        /** Map order-status code to human-readable status. */
        fun mapOrderStatusCode(code: String): String = when (code) {
            "AB00" -> "connected"
            "AB01" -> "open"
            "AB02" -> "cancelled"
            "AB03" -> "rejected"
            "AB04" -> "modified"
            "AB05" -> "complete"
            "AB06" -> "after market order req received"
            "AB07" -> "cancelled after market order"
            "AB08" -> "modify after market order req received"
            "AB09" -> "open pending"
            "AB10" -> "trigger pending"
            "AB11" -> "modify pending"
            else   -> code
        }
    }

    /** Callback for order updates. */
    interface OrderUpdateCallback {
        fun onOrderUpdate(update: OrderUpdate)
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onError(error: String)
    }

    /** Parsed order update from the WebSocket. */
    data class OrderUpdate(
        val orderId: String,
        val orderStatus: String,        // e.g. "open", "complete", "rejected"
        val orderStatusCode: String,    // e.g. "AB01", "AB05", "AB03"
        val tradingSymbol: String,
        val symbolToken: String,
        val exchange: String,
        val transactionType: String,    // BUY / SELL
        val orderType: String,          // LIMIT / MARKET
        val quantity: String,
        val price: Double,
        val averagePrice: Double,
        val filledShares: String,
        val unfilledShares: String,
        val text: String,               // rejection/status message
        val updateTime: String,
        val variety: String,
        val productType: String,
        val duration: String,
        val triggerPrice: Double,
    )

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var pingTimer: Timer? = null
    private var callback: OrderUpdateCallback? = null
    private var reconnectAttempts = 0
    private var isManualDisconnect = false

    fun setCallback(callback: OrderUpdateCallback) {
        this.callback = callback
    }

    fun connect() {
        val creds = AuthState.credentials.value
        if (creds == null) {
            callback?.onError("Not logged in. Cannot connect to Order WebSocket.")
            return
        }

        isManualDisconnect = false
        reconnectAttempts = 0

        val request = Request.Builder()
            .url(WS_URL)
            .addHeader("Authorization", "Bearer ${creds.jwtToken}")
            .build()

        Log.d(TAG, "Connecting to Order WebSocket…")
        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    fun disconnect() {
        isManualDisconnect = true
        stopPingTimer()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        Log.d(TAG, "Disconnected (manual)")
    }

    val isConnected: Boolean get() = webSocket != null

    // ── WebSocket Listener ────────────────────────────────────────────────

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Order WebSocket OPEN")
            reconnectAttempts = 0
            startPingTimer()
            callback?.onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Order WS message: $text")
            try {
                // Skip non-JSON messages (e.g. "pong", plain text acks)
                val trimmed = text.trim()
                if (!trimmed.startsWith("{")) {
                    Log.d(TAG, "Skipping non-JSON message: $trimmed")
                    return
                }

                val json = gson.fromJson(trimmed, JsonObject::class.java)
                val statusCode = json.get("order-status")?.asString ?: ""

                // AB00 is the initial connection acknowledgement — skip
                if (statusCode == "AB00") {
                    Log.d(TAG, "Order WS connected ack received")
                    return
                }

                val orderData = json.getAsJsonObject("orderData") ?: return
                val update = OrderUpdate(
                    orderId = orderData.get("orderid")?.asString ?: "",
                    orderStatus = orderData.get("orderstatus")?.asString ?: mapOrderStatusCode(statusCode),
                    orderStatusCode = statusCode,
                    tradingSymbol = orderData.get("tradingsymbol")?.asString ?: "",
                    symbolToken = orderData.get("symboltoken")?.asString ?: "",
                    exchange = orderData.get("exchange")?.asString ?: "",
                    transactionType = orderData.get("transactiontype")?.asString ?: "",
                    orderType = orderData.get("ordertype")?.asString ?: "",
                    quantity = orderData.get("quantity")?.asString ?: "0",
                    price = orderData.get("price")?.asDouble ?: 0.0,
                    averagePrice = orderData.get("averageprice")?.asDouble ?: 0.0,
                    filledShares = orderData.get("filledshares")?.asString ?: "0",
                    unfilledShares = orderData.get("unfilledshares")?.asString ?: "0",
                    text = orderData.get("text")?.asString ?: "",
                    updateTime = orderData.get("updatetime")?.asString ?: "",
                    variety = orderData.get("variety")?.asString ?: "",
                    productType = orderData.get("producttype")?.asString ?: "",
                    duration = orderData.get("duration")?.asString ?: "",
                    triggerPrice = orderData.get("triggerprice")?.asDouble ?: 0.0,
                )

                Log.d(TAG, "════════════════════════════════════════════════")
                Log.d(TAG, "  ORDER UPDATE")
                Log.d(TAG, "════════════════════════════════════════════════")
                Log.d(TAG, "  orderId       : ${update.orderId}")
                Log.d(TAG, "  status        : ${update.orderStatus} (${update.orderStatusCode})")
                Log.d(TAG, "  tradingSymbol : ${update.tradingSymbol}")
                Log.d(TAG, "  symbolToken   : ${update.symbolToken}")
                Log.d(TAG, "  exchange      : ${update.exchange}")
                Log.d(TAG, "  txnType       : ${update.transactionType}")
                Log.d(TAG, "  orderType     : ${update.orderType}")
                Log.d(TAG, "  qty           : ${update.quantity}")
                Log.d(TAG, "  price         : ${update.price}")
                Log.d(TAG, "  avgPrice      : ${update.averagePrice}")
                Log.d(TAG, "  filled        : ${update.filledShares}")
                Log.d(TAG, "  unfilled      : ${update.unfilledShares}")
                Log.d(TAG, "  text          : ${update.text}")
                Log.d(TAG, "  updateTime    : ${update.updateTime}")
                Log.d(TAG, "════════════════════════════════════════════════")

                callback?.onOrderUpdate(update)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse order message: ${e.message}", e)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Order WS failure: ${t.message}, code=${response?.code}")
            stopPingTimer()
            callback?.onError(t.message ?: "WebSocket failure")
            callback?.onDisconnected("Error: ${t.message}")

            if (!isManualDisconnect) {
                scheduleReconnect()
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Order WS closing: code=$code, reason=$reason")
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Order WS closed: code=$code, reason=$reason")
            stopPingTimer()
            callback?.onDisconnected(reason)

            if (!isManualDisconnect) {
                scheduleReconnect()
            }
        }
    }

    // ── Ping / Keep-alive ─────────────────────────────────────────────────

    private fun startPingTimer() {
        stopPingTimer()
        pingTimer = Timer("OrderWS-Ping", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        webSocket?.send("ping")
                    } catch (e: Exception) {
                        Log.w(TAG, "Ping send failed: ${e.message}")
                    }
                }
            }, PING_INTERVAL_MS, PING_INTERVAL_MS)
        }
    }

    private fun stopPingTimer() {
        pingTimer?.cancel()
        pingTimer = null
    }

    // ── Reconnect ─────────────────────────────────────────────────────────

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached ($MAX_RECONNECT_ATTEMPTS)")
            callback?.onError("Failed to reconnect after $MAX_RECONNECT_ATTEMPTS attempts")
            return
        }
        reconnectAttempts++
        val delay = RECONNECT_DELAY_MS * reconnectAttempts
        Log.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts)")
        Timer().schedule(object : TimerTask() {
            override fun run() {
                if (!isManualDisconnect) {
                    connect()
                }
            }
        }, delay)
    }
}
