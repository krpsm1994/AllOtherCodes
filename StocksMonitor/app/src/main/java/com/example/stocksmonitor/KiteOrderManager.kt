package com.example.stocksmonitor

import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object KiteOrderManager {
    
    fun placeOrder(
        apiKey: String,
        accessToken: String,
        symbol: String,
        exchange: String,
        buyPrice: Double,
        quantity: Int,
        callback: (String?, String?) -> Unit
    ) {
        val client = OkHttpClient()
        
        // Remove -EQ suffix if present (Kite API doesn't accept it)
        val cleanSymbol = if (symbol.endsWith("-EQ")) symbol.substring(0, symbol.length - 3) else symbol
        
        val body = FormBody.Builder()
            .add("variety", "regular")
            .add("tradingsymbol", cleanSymbol)
            .add("exchange", exchange)
            .add("transaction_type", "BUY")
            .add("order_type", "LIMIT")
            .add("product", "CNC")  // CNC = Carry forward (delivery), MIS = intraday
            .add("price", String.format("%.2f", buyPrice))  // Format price to 2 decimal places
            .add("quantity", quantity.toString())
            .add("disclosed_quantity", "0")
            .add("trigger_price", "0")
            .build()
        
        val request = Request.Builder()
            .url("https://api.kite.trade/orders/regular")
            .addHeader("Authorization", "token $apiKey:$accessToken")
            .addHeader("X-Kite-Version", "3")
            .post(body)
            .build()
        
        Logger.d("KiteOrderManager", "placeOrder() - Placing order:")
        Logger.d("KiteOrderManager", "  Symbol: $cleanSymbol (original: $symbol)")
        Logger.d("KiteOrderManager", "  Exchange: $exchange")
        Logger.d("KiteOrderManager", "  Product: CNC (Carry Forward/Delivery)")
        Logger.d("KiteOrderManager", "  Price: ${String.format("%.2f", buyPrice)}")
        Logger.d("KiteOrderManager", "  Quantity: $quantity")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e("KiteOrderManager", "placeOrder() - Failed to place order", e)
                callback(null, "Network error: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Logger.d("KiteOrderManager", "placeOrder() - Response code: ${response.code}")
                Logger.d("KiteOrderManager", "placeOrder() - Response body: $responseBody")
                
                try {
                    if (response.code == 200) {
                        val jsonResponse = JSONObject(responseBody)
                        val status = jsonResponse.optString("status")
                        
                        if (status == "success") {
                            val data = jsonResponse.optJSONObject("data")
                            val orderId = data?.optString("order_id", "")
                            
                            if (!orderId.isNullOrEmpty()) {
                                Logger.d("KiteOrderManager", "placeOrder() - Order placed successfully: $orderId")
                                callback(orderId, null)
                            } else {
                                callback(null, "Failed to extract order ID from response")
                            }
                        } else {
                            val message = jsonResponse.optString("message", "Unknown error")
                            Logger.w("KiteOrderManager", "placeOrder() - API returned error: $message")
                            callback(null, message)
                        }
                    } else {
                        Logger.w("KiteOrderManager", "placeOrder() - HTTP ${response.code}")
                        
                        // Try to extract error message from JSON if available
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            val message = jsonResponse.optString("message", "HTTP ${response.code}")
                            Logger.w("KiteOrderManager", "placeOrder() - Error message: $message")
                            callback(null, message)
                        } catch (e: Exception) {
                            Logger.e("KiteOrderManager", "placeOrder() - Error response: $responseBody")
                            callback(null, "HTTP ${response.code}: $responseBody")
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("KiteOrderManager", "placeOrder() - Error parsing response", e)
                    callback(null, "Error parsing response: ${e.message}")
                }
            }
        })
    }
    
    fun placeMarketOrder(
        apiKey: String,
        accessToken: String,
        symbol: String,
        exchange: String,
        quantity: Int,
        callback: (String?, String?) -> Unit
    ) {
        val client = OkHttpClient()
        
        // Remove -EQ suffix if present (Kite API doesn't accept it)
        val cleanSymbol = if (symbol.endsWith("-EQ")) symbol.substring(0, symbol.length - 3) else symbol
        
        val body = FormBody.Builder()
            .add("variety", "regular")
            .add("tradingsymbol", cleanSymbol)
            .add("exchange", exchange)
            .add("transaction_type", "BUY")
            .add("order_type", "MARKET")  // MARKET order instead of LIMIT
            .add("product", "CNC")  // CNC = Carry forward (delivery)
            .add("quantity", quantity.toString())
            .add("disclosed_quantity", "0")
            .add("trigger_price", "0")
            .build()
        
        val request = Request.Builder()
            .url("https://api.kite.trade/orders/regular")
            .addHeader("Authorization", "token $apiKey:$accessToken")
            .addHeader("X-Kite-Version", "3")
            .post(body)
            .build()
        
        Logger.d("KiteOrderManager", "placeMarketOrder() - Placing MARKET order:")
        Logger.d("KiteOrderManager", "  Symbol: $cleanSymbol (original: $symbol)")
        Logger.d("KiteOrderManager", "  Exchange: $exchange")
        Logger.d("KiteOrderManager", "  Order Type: MARKET")
        Logger.d("KiteOrderManager", "  Product: CNC (Carry Forward/Delivery)")
        Logger.d("KiteOrderManager", "  Quantity: $quantity")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e("KiteOrderManager", "placeMarketOrder() - Failed to place order", e)
                callback(null, "Network error: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Logger.d("KiteOrderManager", "placeMarketOrder() - Response code: ${response.code}")
                Logger.d("KiteOrderManager", "placeMarketOrder() - Response body: $responseBody")
                
                try {
                    if (response.code == 200) {
                        val jsonResponse = JSONObject(responseBody)
                        val status = jsonResponse.optString("status")
                        
                        if (status == "success") {
                            val data = jsonResponse.optJSONObject("data")
                            val orderId = data?.optString("order_id", "")
                            
                            if (!orderId.isNullOrEmpty()) {
                                Logger.d("KiteOrderManager", "placeMarketOrder() - Order placed successfully: $orderId")
                                callback(orderId, null)
                            } else {
                                callback(null, "Failed to extract order ID from response")
                            }
                        } else {
                            val message = jsonResponse.optString("message", "Unknown error")
                            Logger.w("KiteOrderManager", "placeMarketOrder() - API returned error: $message")
                            callback(null, message)
                        }
                    } else {
                        Logger.w("KiteOrderManager", "placeMarketOrder() - HTTP ${response.code}")
                        
                        // Try to extract error message from JSON if available
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            val message = jsonResponse.optString("message", "HTTP ${response.code}")
                            Logger.w("KiteOrderManager", "placeMarketOrder() - Error message: $message")
                            callback(null, message)
                        } catch (e: Exception) {
                            Logger.e("KiteOrderManager", "placeMarketOrder() - Error response: $responseBody")
                            callback(null, "HTTP ${response.code}: $responseBody")
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("KiteOrderManager", "placeMarketOrder() - Error parsing response", e)
                    callback(null, "Error parsing response: ${e.message}")
                }
            }
        })
    }
    
    fun checkOrderStatus(
        apiKey: String,
        accessToken: String,
        orderId: String,
        callback: (String?, String?) -> Unit
    ) {
        val client = OkHttpClient()
        
        val request = Request.Builder()
            .url("https://api.kite.trade/orders/$orderId")
            .addHeader("Authorization", "token $apiKey:$accessToken")
            .addHeader("X-Kite-Version", "3")
            .get()
            .build()
        
        Logger.d("KiteOrderManager", "checkOrderStatus() - Fetching status for order: $orderId")
        Logger.d("KiteOrderManager", "  URL: https://api.kite.trade/orders/$orderId")
        Logger.d("KiteOrderManager", "  Authorization header present: Yes")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e("KiteOrderManager", "checkOrderStatus() - Network failure for order $orderId", e)
                Logger.e("KiteOrderManager", "  Error: ${e.message}")
                callback(null, "Network error: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Logger.d("KiteOrderManager", "checkOrderStatus() - Response received for order $orderId")
                Logger.d("KiteOrderManager", "  Response code: ${response.code}")
                Logger.d("KiteOrderManager", "  Response body length: ${responseBody.length}")
                Logger.d("KiteOrderManager", "  Response body: $responseBody")
                
                try {
                    if (response.code == 200) {
                        Logger.d("KiteOrderManager", "checkOrderStatus() - Parsing JSON response")
                        
                        val jsonResponse = JSONObject(responseBody)
                        Logger.d("KiteOrderManager", "  JSON parsed successfully")
                        
                        val status = jsonResponse.optString("status")
                        Logger.d("KiteOrderManager", "  Top-level status field: $status")
                        
                        if (status == "success") {
                            val data = jsonResponse.optJSONObject("data")
                            if (data != null) {
                                Logger.d("KiteOrderManager", "  Data object found")
                                val orderStatus = data.optString("status", "")
                                Logger.d("KiteOrderManager", "  Order status from data: $orderStatus")
                                Logger.d("KiteOrderManager", "  Order state from data: ${data.optString("state", "N/A")}")
                                Logger.d("KiteOrderManager", "  Order filled quantity: ${data.optString("filled_quantity", "0")}")
                                Logger.d("KiteOrderManager", "  Order pending quantity: ${data.optString("pending_quantity", "0")}")
                                
                                if (orderStatus.isNotEmpty()) {
                                    Logger.d("KiteOrderManager", "checkOrderStatus() - Returning status: $orderStatus")
                                    callback(orderStatus, null)
                                } else {
                                    Logger.w("KiteOrderManager", "checkOrderStatus() - Order status is empty")
                                    callback(null, "Order status is empty in response")
                                }
                            } else {
                                Logger.e("KiteOrderManager", "checkOrderStatus() - Data object is null")
                                callback(null, "No data in response")
                            }
                        } else {
                            val message = jsonResponse.optString("message", "Unknown error")
                            Logger.w("KiteOrderManager", "checkOrderStatus() - API status not success: $message")
                            callback(null, message)
                        }
                    } else {
                        Logger.w("KiteOrderManager", "checkOrderStatus() - HTTP ${response.code}")
                        Logger.w("KiteOrderManager", "  Response: $responseBody")
                        
                        // Try to extract error message from JSON
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            val message = jsonResponse.optString("message", "HTTP ${response.code}")
                            Logger.w("KiteOrderManager", "  Error message: $message")
                            callback(null, message)
                        } catch (e: Exception) {
                            Logger.e("KiteOrderManager", "checkOrderStatus() - Failed to parse error response")
                            callback(null, "HTTP ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("KiteOrderManager", "checkOrderStatus() - Error parsing response for order $orderId", e)
                    Logger.e("KiteOrderManager", "  Exception: ${e.message}")
                    Logger.e("KiteOrderManager", "  Response body was: $responseBody")
                    callback(null, "Error parsing response: ${e.message}")
                }
            }
        })
    }
}
