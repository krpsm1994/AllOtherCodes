package com.example.stocksmonitor

import org.json.JSONObject

data class Order(
    val orderId: String,
    val tradingSymbol: String,
    val exchange: String,
    val orderType: String,
    val transactionType: String,
    val status: String,
    val quantity: Int,
    val price: Double,
    val averagePrice: Double,
    val orderTimestamp: String,
    val statusMessage: String,
    // Additional fields from Kite API
    val exchangeOrderId: String,
    val parentOrderId: String,
    val variety: String,
    val instrumentToken: String,
    val validity: String,
    val product: String,
    val disclosedQuantity: Int,
    val triggerPrice: Double,
    val filledQuantity: Int,
    val pendingQuantity: Int,
    val cancelledQuantity: Int,
    val exchangeTimestamp: String,
    val exchangeUpdateTimestamp: String,
    val placedBy: String,
    val tag: String,
    val guid: String
) {
    companion object {
        fun fromJson(json: JSONObject): Order {
            return Order(
                orderId = json.optString("order_id", ""),
                tradingSymbol = json.optString("tradingsymbol", ""),
                exchange = json.optString("exchange", ""),
                orderType = json.optString("order_type", ""),
                transactionType = json.optString("transaction_type", ""),
                status = json.optString("status", ""),
                quantity = json.optInt("quantity", 0),
                price = json.optDouble("price", 0.0),
                averagePrice = json.optDouble("average_price", 0.0),
                orderTimestamp = json.optString("order_timestamp", ""),
                statusMessage = json.optString("status_message", ""),
                exchangeOrderId = json.optString("exchange_order_id", ""),
                parentOrderId = json.optString("parent_order_id", ""),
                variety = json.optString("variety", ""),
                instrumentToken = json.optString("instrument_token", ""),
                validity = json.optString("validity", ""),
                product = json.optString("product", ""),
                disclosedQuantity = json.optInt("disclosed_quantity", 0),
                triggerPrice = json.optDouble("trigger_price", 0.0),
                filledQuantity = json.optInt("filled_quantity", 0),
                pendingQuantity = json.optInt("pending_quantity", 0),
                cancelledQuantity = json.optInt("cancelled_quantity", 0),
                exchangeTimestamp = json.optString("exchange_timestamp", ""),
                exchangeUpdateTimestamp = json.optString("exchange_update_timestamp", ""),
                placedBy = json.optString("placed_by", ""),
                tag = json.optString("tag", ""),
                guid = json.optString("guid", "")
            )
        }
    }
}
