package com.example.angelonestrategyexecutor.data.repository

import android.util.Log
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.example.angelonestrategyexecutor.data.model.PlaceOrderRequest
import com.example.angelonestrategyexecutor.data.model.PlaceOrderResponse
import com.example.angelonestrategyexecutor.data.network.AngelOneApiClient

/**
 * Singleton repository for placing orders via AngelOne Smart API.
 */
object OrderRepository {

    private const val TAG = "OrderRepo"

    /**
     * Place a BUY LIMIT order for the given option.
     *
     * @param tradingSymbol  e.g. "RELIANCE28MAR25CE2800"
     * @param symbolToken    option instrument token
     * @param quantity       total quantity (lotSize × lots)
     * @param limitPrice     limit price (option LTP at trigger time)
     * @return [PlaceOrderResponse] on success, null on failure
     */
    suspend fun placeBuyOrder(
        tradingSymbol: String,
        symbolToken: String,
        quantity: Int,
        limitPrice: Double,
        isMarketOrder: Boolean = false,
        exchange: String = "NFO",
        productType: String = "CARRYFORWARD",
    ): PlaceOrderResponse? {
        val creds = AuthState.credentials.value
        if (creds == null || creds.isExpired) {
            Log.e(TAG, "Cannot place order — not logged in or session expired")
            return null
        }

        val request = PlaceOrderRequest(
            variety = "NORMAL",
            tradingSymbol = tradingSymbol,
            symbolToken = symbolToken,
            transactionType = "BUY",
            exchange = exchange,
            orderType = if (isMarketOrder) "MARKET" else "LIMIT",
            productType = productType,
            duration = "DAY",
            price = if (isMarketOrder) "0" else "%.2f".format(limitPrice),
            quantity = quantity.toString(),
        )

        return try {
            Log.d(TAG, "════════════════════════════════════════════════")
            Log.d(TAG, "  PLACE ORDER REQUEST")
            Log.d(TAG, "════════════════════════════════════════════════")
            Log.d(TAG, "  variety       : ${request.variety}")
            Log.d(TAG, "  exchange      : ${request.exchange}")
            Log.d(TAG, "  tradingSymbol : ${request.tradingSymbol}")
            Log.d(TAG, "  symbolToken   : ${request.symbolToken}")
            Log.d(TAG, "  transactionType: ${request.transactionType}")
            Log.d(TAG, "  orderType     : ${request.orderType}")
            Log.d(TAG, "  productType   : ${request.productType}")
            Log.d(TAG, "  duration      : ${request.duration}")
            Log.d(TAG, "  quantity      : ${request.quantity}")
            Log.d(TAG, "  price         : ${request.price}")
            Log.d(TAG, "  squareOff     : ${request.squareOff}")
            Log.d(TAG, "  stoploss      : ${request.stoploss}")
            Log.d(TAG, "  apiKey        : ${creds.apiKey}")
            Log.d(TAG, "  clientCode    : ${creds.clientCode}")
            Log.d(TAG, "════════════════════════════════════════════════")

            val response = AngelOneApiClient.service.placeOrder(
                authorization = "Bearer ${creds.jwtToken}",
                apiKey = creds.apiKey,
                userType = "USER",
                sourceId = "WEB",
                clientLocalIp = "192.168.1.1",
                clientPublicIp = "192.168.1.1",
                macAddress = "AA:BB:CC:DD:EE:FF",
                request = request,
            )

            Log.d(TAG, "════════════════════════════════════════════════")
            Log.d(TAG, "  PLACE ORDER RESPONSE")
            Log.d(TAG, "════════════════════════════════════════════════")
            Log.d(TAG, "  status        : ${response.status}")
            Log.d(TAG, "  message       : ${response.message}")
            Log.d(TAG, "  errorCode     : ${response.errorCode}")
            Log.d(TAG, "  orderId       : ${response.data?.orderId ?: "—"}")
            Log.d(TAG, "  uniqueOrderId : ${response.data?.uniqueOrderId ?: "—"}")
            Log.d(TAG, "  script        : ${response.data?.script ?: "—"}")
            Log.d(TAG, "════════════════════════════════════════════════")

            response
        } catch (e: Exception) {
            Log.e(TAG, "Order placement failed: ${e.message}", e)
            null
        }
    }

    /**
     * Place a SELL order for the given option (to exit / square-off).
     *
     * @param tradingSymbol  e.g. "RELIANCE28MAR25CE2800"
     * @param symbolToken    option instrument token
     * @param quantity       total quantity (lotSize × lots)
     * @param limitPrice     limit price (option LTP at trigger time)
     * @param isMarketOrder  true → MARKET order (price=0)
     * @param exchange       exchange segment e.g. "NFO"
     * @return [PlaceOrderResponse] on success, null on failure
     */
    suspend fun placeSellOrder(
        tradingSymbol: String,
        symbolToken: String,
        quantity: Int,
        limitPrice: Double,
        isMarketOrder: Boolean = false,
        exchange: String = "NFO",
        productType: String = "CARRYFORWARD",
    ): PlaceOrderResponse? {
        val creds = AuthState.credentials.value
        if (creds == null || creds.isExpired) {
            Log.e(TAG, "Cannot place sell order — not logged in or session expired")
            return null
        }

        val request = PlaceOrderRequest(
            variety = "NORMAL",
            tradingSymbol = tradingSymbol,
            symbolToken = symbolToken,
            transactionType = "SELL",
            exchange = exchange,
            orderType = if (isMarketOrder) "MARKET" else "LIMIT",
            productType = productType,
            duration = "DAY",
            price = if (isMarketOrder) "0" else "%.2f".format(limitPrice),
            quantity = quantity.toString(),
        )

        return try {
            Log.d(TAG, "════════════════════════════════════════════════")
            Log.d(TAG, "  PLACE SELL ORDER REQUEST")
            Log.d(TAG, "════════════════════════════════════════════════")
            Log.d(TAG, "  variety       : ${request.variety}")
            Log.d(TAG, "  exchange      : ${request.exchange}")
            Log.d(TAG, "  tradingSymbol : ${request.tradingSymbol}")
            Log.d(TAG, "  symbolToken   : ${request.symbolToken}")
            Log.d(TAG, "  transactionType: ${request.transactionType}")
            Log.d(TAG, "  orderType     : ${request.orderType}")
            Log.d(TAG, "  productType   : ${request.productType}")
            Log.d(TAG, "  duration      : ${request.duration}")
            Log.d(TAG, "  quantity      : ${request.quantity}")
            Log.d(TAG, "  price         : ${request.price}")
            Log.d(TAG, "════════════════════════════════════════════════")

            val response = AngelOneApiClient.service.placeOrder(
                authorization = "Bearer ${creds.jwtToken}",
                apiKey = creds.apiKey,
                userType = "USER",
                sourceId = "WEB",
                clientLocalIp = "192.168.1.1",
                clientPublicIp = "192.168.1.1",
                macAddress = "AA:BB:CC:DD:EE:FF",
                request = request,
            )

            Log.d(TAG, "════════════════════════════════════════════════")
            Log.d(TAG, "  PLACE SELL ORDER RESPONSE")
            Log.d(TAG, "════════════════════════════════════════════════")
            Log.d(TAG, "  status        : ${response.status}")
            Log.d(TAG, "  message       : ${response.message}")
            Log.d(TAG, "  errorCode     : ${response.errorCode}")
            Log.d(TAG, "  orderId       : ${response.data?.orderId ?: "—"}")
            Log.d(TAG, "  uniqueOrderId : ${response.data?.uniqueOrderId ?: "—"}")
            Log.d(TAG, "════════════════════════════════════════════════")

            response
        } catch (e: Exception) {
            Log.e(TAG, "Sell order placement failed: ${e.message}", e)
            null
        }
    }
}
