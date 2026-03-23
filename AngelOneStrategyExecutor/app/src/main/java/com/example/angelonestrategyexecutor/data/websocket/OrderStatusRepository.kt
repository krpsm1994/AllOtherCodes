package com.example.angelonestrategyexecutor.data.websocket

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton repository that manages the Order WebSocket connection
 * and exposes live order status updates as observable StateFlows.
 *
 * Usage:
 *  1. After placing the first order, call [connect]
 *  2. Observe [orderUpdates] for real-time order status changes
 *  3. Match updates to your stock list by orderId
 */
object OrderStatusRepository : OrderWebSocketService.OrderUpdateCallback {

    private const val TAG = "OrderStatusRepo"

    private val wsService = OrderWebSocketService()

    /**
     * Map of orderId → latest order update.
     * Updated in real-time from the WebSocket.
     */
    private val _orderUpdates = MutableStateFlow<Map<String, OrderWebSocketService.OrderUpdate>>(emptyMap())
    val orderUpdates: StateFlow<Map<String, OrderWebSocketService.OrderUpdate>> = _orderUpdates.asStateFlow()

    /** Connection state of the Order WebSocket. */
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Latest error. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    enum class ConnectionState { CONNECTED, CONNECTING, DISCONNECTED }

    init {
        wsService.setCallback(this)
    }

    /**
     * Connect to the Order WebSocket. Call once after the first order is placed.
     * Safe to call multiple times — will not reconnect if already connected.
     */
    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            Log.d(TAG, "Order WebSocket already ${_connectionState.value}, skipping connect")
            return
        }
        Log.d(TAG, "Connecting Order WebSocket...")
        _connectionState.value = ConnectionState.CONNECTING
        _error.value = null
        wsService.connect()
    }

    fun disconnect() {
        wsService.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    val isConnected: Boolean get() = _connectionState.value == ConnectionState.CONNECTED

    /**
     * Get the latest update for a given orderId.
     */
    fun getUpdate(orderId: String): OrderWebSocketService.OrderUpdate? =
        _orderUpdates.value[orderId]

    // ── OrderWebSocketService.OrderUpdateCallback ────────────────────────

    override fun onOrderUpdate(update: OrderWebSocketService.OrderUpdate) {
        if (update.orderId.isBlank()) {
            Log.w(TAG, "Received order update with blank orderId, skipping")
            return
        }
        Log.d(TAG, "══ ORDER UPDATE RECEIVED ══")
        Log.d(TAG, "  orderId    : ${update.orderId}")
        Log.d(TAG, "  status     : ${update.orderStatus} (${update.orderStatusCode})")
        Log.d(TAG, "  txnType    : ${update.transactionType}")
        Log.d(TAG, "  symbol     : ${update.tradingSymbol}")
        Log.d(TAG, "  avgPrice   : ${update.averagePrice}")
        Log.d(TAG, "  filled     : ${update.filledShares}")
        Log.d(TAG, "  text       : ${update.text}")
        Log.d(TAG, "  Map size before: ${_orderUpdates.value.size}")
        Log.d(TAG, "  Existing keys: ${_orderUpdates.value.keys}")
        _orderUpdates.value = _orderUpdates.value + (update.orderId to update)
        Log.d(TAG, "  Map size after: ${_orderUpdates.value.size}")
    }

    override fun onConnected() {
        Log.d(TAG, "Order WebSocket connected")
        _connectionState.value = ConnectionState.CONNECTED
        _error.value = null
    }

    override fun onDisconnected(reason: String) {
        Log.d(TAG, "Order WebSocket disconnected: $reason")
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun onError(error: String) {
        Log.e(TAG, "Order WebSocket error: $error")
        _error.value = error
    }
}
