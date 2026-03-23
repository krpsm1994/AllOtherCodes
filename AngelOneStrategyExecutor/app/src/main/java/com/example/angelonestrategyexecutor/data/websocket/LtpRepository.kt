package com.example.angelonestrategyexecutor.data.websocket

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton repository that manages the SmartWebSocket connection
 * and exposes live LTP prices as observable StateFlows.
 *
 * Usage:
 *  1. After login, call [connect]
 *  2. Call [subscribeTokens] with the instrument tokens you want LTPs for
 *  3. Observe [ltpMap] for live price updates
 */
object LtpRepository : SmartWebSocketService.LtpCallback {

    private const val TAG = "LtpRepo"

    private val wsService = SmartWebSocketService()

    /**
     * Map of token → latest LTP price.
     * Key is the instrument token string (e.g. "10626").
     */
    private val _ltpMap = MutableStateFlow<Map<String, Double>>(emptyMap())
    val ltpMap: StateFlow<Map<String, Double>> = _ltpMap.asStateFlow()

    /** WebSocket connection state. */
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Latest error message, if any. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    enum class ConnectionState { CONNECTED, CONNECTING, DISCONNECTED }

    init {
        wsService.setCallback(this)
    }

    /**
     * Connect to the WebSocket. Call after login when [AuthState] has credentials.
     */
    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            Log.d(TAG, "Already connected")
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        _error.value = null
        wsService.connect()
    }

    /**
     * Disconnect from the WebSocket.
     */
    fun disconnect() {
        wsService.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Subscribe to LTP updates for equity tokens (NSE Cash Market).
     */
    fun subscribeEquityTokens(tokens: List<String>) {
        val valid = tokens.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        Log.d(TAG, "Subscribing equity tokens: $valid")
        wsService.subscribeLtp(SmartWebSocketService.EXCHANGE_NSE_CM, valid)
    }

    /**
     * Subscribe to LTP updates for F&O tokens (NSE F&O).
     */
    fun subscribeFnoTokens(tokens: List<String>) {
        val valid = tokens.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        Log.d(TAG, "Subscribing FnO tokens: $valid")
        wsService.subscribeLtp(SmartWebSocketService.EXCHANGE_NSE_FO, valid)
    }

    /**
     * Subscribe to both equity and option tokens from stock entries at once.
     *
     * @param equityTokens List of equity instrument tokens
     * @param optionTokens List of option instrument tokens
     */
    fun subscribeTokens(equityTokens: List<String>, optionTokens: List<String>) {
        subscribeEquityTokens(equityTokens)
        subscribeFnoTokens(optionTokens)
    }

    /**
     * Subscribe to LTP updates for tokens on a specific exchange type.
     * Use this when you know the exact exchange type per token.
     */
    fun subscribeByExchange(exchangeType: Int, tokens: List<String>) {
        val valid = tokens.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        Log.d(TAG, "Subscribing ${valid.size} tokens on exchange=$exchangeType: $valid")
        wsService.subscribeLtp(exchangeType, valid)
    }

    /**
     * Unsubscribe equity tokens.
     */
    fun unsubscribeEquityTokens(tokens: List<String>) {
        if (tokens.isEmpty()) return
        wsService.unsubscribeLtp(SmartWebSocketService.EXCHANGE_NSE_CM, tokens)
    }

    /**
     * Unsubscribe F&O tokens.
     */
    fun unsubscribeFnoTokens(tokens: List<String>) {
        if (tokens.isEmpty()) return
        wsService.unsubscribeLtp(SmartWebSocketService.EXCHANGE_NSE_FO, tokens)
    }

    /**
     * Unsubscribe tokens on a specific exchange type.
     */
    fun unsubscribeByExchange(exchangeType: Int, tokens: List<String>) {
        val valid = tokens.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        Log.d(TAG, "Unsubscribing ${valid.size} tokens on exchange=$exchangeType: $valid")
        wsService.unsubscribeLtp(exchangeType, valid)
    }

    /**
     * Remove tokens from the LTP map (e.g. after delete/unsubscribe).
     */
    fun removeTokens(tokens: List<String>) {
        if (tokens.isEmpty()) return
        _ltpMap.update { current -> current - tokens.toSet() }
        Log.d(TAG, "Removed ${tokens.size} tokens from LTP map")
    }

    /**
     * Get the current LTP for a given instrument token, or null if not available.
     */
    fun getLtp(token: String): Double? = _ltpMap.value[token]

    val isConnected: Boolean get() = _connectionState.value == ConnectionState.CONNECTED

    // ── SmartWebSocketService.LtpCallback ─────────────────────────────────

    override fun onLtpUpdate(token: String, exchangeType: Int, ltp: Double) {
        _ltpMap.update { current ->
            current + (token to ltp)
        }
    }

    override fun onConnected() {
        Log.d(TAG, "WebSocket connected")
        _connectionState.value = ConnectionState.CONNECTED
        _error.value = null
    }

    override fun onDisconnected(reason: String) {
        Log.d(TAG, "WebSocket disconnected: $reason")
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun onError(error: String) {
        Log.e(TAG, "WebSocket error: $error")
        _error.value = error
    }
}
