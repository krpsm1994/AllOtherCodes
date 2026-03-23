package com.example.stocksmonitor

import android.content.Context
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class PortfolioManager(private val context: Context) {
    private val client = OkHttpClient()
    private val holdingsFile = File(context.filesDir, "portfolio_holdings.txt")
    private val holdingsLock = Any()
    
    companion object {
        private const val TAG = "PortfolioManager"
    }
    
    // Fetch Kite Holdings (Long-term positions)
    fun fetchKiteHoldings(
        apiKey: String,
        accessToken: String,
        callback: (List<Holding>, String?) -> Unit
    ) {
        val request = Request.Builder()
            .url("https://api.kite.trade/portfolio/holdings")
            .addHeader("Authorization", "token $apiKey:$accessToken")
            .addHeader("X-Kite-Version", "3")
            .build()
        
        Logger.d(TAG, "fetchKiteHoldings() - Fetching long-term holdings from Kite")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e(TAG, "fetchKiteHoldings() - Network error: ${e.message}")
                callback(emptyList(), "Network error: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Logger.d(TAG, "fetchKiteHoldings() - Response code: ${response.code}")
                
                try {
                    if (response.code == 200) {
                        val jsonResponse = JSONObject(responseBody)
                        val status = jsonResponse.optString("status")
                        
                        if (status == "success") {
                            val dataArray = jsonResponse.optJSONArray("data")
                            val holdings = mutableListOf<Holding>()
                            
                            if (dataArray != null) {
                                for (i in 0 until dataArray.length()) {
                                    val holding = Holding.fromKiteHolding(dataArray.getJSONObject(i))
                                    if (holding != null) {
                                        holdings.add(holding)
                                    }
                                }
                            }
                            
                            Logger.d(TAG, "fetchKiteHoldings() - Successfully fetched ${holdings.size} holdings")
                            callback(holdings, null)
                        } else {
                            Logger.w(TAG, "fetchKiteHoldings() - API returned error status")
                            callback(emptyList(), "API error: $status")
                        }
                    } else if (response.code == 403) {
                        Logger.w(TAG, "fetchKiteHoldings() - Unauthorized (403)")
                        callback(emptyList(), "Session expired. Please login again.")
                    } else {
                        Logger.w(TAG, "fetchKiteHoldings() - Unexpected response code: ${response.code}")
                        callback(emptyList(), "Error: HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "fetchKiteHoldings() - Error parsing response: ${e.message}")
                    callback(emptyList(), "Error parsing response: ${e.message}")
                }
            }
        })
    }
    
    // Fetch Kite Positions (Short-term positions)
    fun fetchKitePositions(
        apiKey: String,
        accessToken: String,
        callback: (List<Holding>, String?) -> Unit
    ) {
        val request = Request.Builder()
            .url("https://api.kite.trade/portfolio/positions")
            .addHeader("Authorization", "token $apiKey:$accessToken")
            .addHeader("X-Kite-Version", "3")
            .build()
        
        Logger.d(TAG, "fetchKitePositions() - Fetching short-term positions from Kite")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e(TAG, "fetchKitePositions() - Network error: ${e.message}")
                callback(emptyList(), "Network error: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Logger.d(TAG, "fetchKitePositions() - Response code: ${response.code}")
                
                try {
                    if (response.code == 200) {
                        val jsonResponse = JSONObject(responseBody)
                        val status = jsonResponse.optString("status")
                        
                        if (status == "success") {
                            val dataObject = jsonResponse.optJSONObject("data")
                            val net = dataObject?.optJSONArray("net")
                            val positions = mutableListOf<Holding>()
                            
                            if (net != null) {
                                for (i in 0 until net.length()) {
                                    val position = Holding.fromKitePosition(net.getJSONObject(i))
                                    if (position != null) {
                                        positions.add(position)
                                    }
                                }
                            }
                            
                            Logger.d(TAG, "fetchKitePositions() - Successfully fetched ${positions.size} positions")
                            callback(positions, null)
                        } else {
                            Logger.w(TAG, "fetchKitePositions() - API returned error status")
                            callback(emptyList(), "API error: $status")
                        }
                    } else if (response.code == 403) {
                        Logger.w(TAG, "fetchKitePositions() - Unauthorized (403)")
                        callback(emptyList(), "Session expired. Please login again.")
                    } else {
                        Logger.w(TAG, "fetchKitePositions() - Unexpected response code: ${response.code}")
                        callback(emptyList(), "Error: HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "fetchKitePositions() - Error parsing response: ${e.message}")
                    callback(emptyList(), "Error parsing response: ${e.message}")
                }
            }
        })
    }
    
    // Fetch AngelOne Holdings
    fun fetchAngelHoldings(
        jwtToken: String,
        apiKey: String,
        clientCode: String,
        callback: (List<Holding>, String?) -> Unit
    ) {
        // Try without body first, as some APIs don't require mode parameter
        val request = Request.Builder()
            .url("https://apiconnect.angelone.in/rest/secure/angelbroking/portfolio/v1/getAllHolding")
            .addHeader("Authorization", "Bearer $jwtToken")
            .addHeader("X-API-KEY", apiKey)
            .addHeader("X-CLIENT-CODE", clientCode)
            .addHeader("x-api-key", apiKey)  // Also try lowercase
            .addHeader("x-client-code", clientCode)  // Also try lowercase
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post("{}".toRequestBody("application/json".toMediaType()))  // Empty JSON object
            .build()
        
        Logger.d(TAG, "fetchAngelHoldings() - Fetching holdings from AngelOne")
        Logger.d(TAG, "fetchAngelHoldings() - URL: ${request.url}")
        Logger.d(TAG, "fetchAngelHoldings() - JWT Token (first 30 chars): ${jwtToken.take(30)}...")
        Logger.d(TAG, "fetchAngelHoldings() - API Key: $apiKey")
        Logger.d(TAG, "fetchAngelHoldings() - Client Code: $clientCode")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e(TAG, "fetchAngelHoldings() - Network error: ${e.message}")
                callback(emptyList(), "Network error: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Logger.d(TAG, "fetchAngelHoldings() - Response code: ${response.code}")
                Logger.d(TAG, "fetchAngelHoldings() - Response headers: ${response.headers}")
                Logger.d(TAG, "fetchAngelHoldings() - Full response body: $responseBody")
                Logger.d(TAG, "fetchAngelHoldings() - Response body length: ${responseBody.length}")
                Logger.d(TAG, "fetchAngelHoldings() - Response content type: ${response.header("Content-Type")}")
                
                try {
                    if (response.code == 200) {
                        if (responseBody.isEmpty()) {
                            Logger.w(TAG, "fetchAngelHoldings() - Empty response body")
                            callback(emptyList(), null)
                            return
                        }
                        
                        // Check if response is HTML (error page) instead of JSON
                        if (responseBody.trim().startsWith("<")) {
                            Logger.w(TAG, "fetchAngelHoldings() - Response appears to be HTML (error page), not JSON")
                            Logger.w(TAG, "fetchAngelHoldings() - HTML response: ${responseBody.take(500)}")
                            callback(emptyList(), "API returned HTML instead of JSON - possibly authentication error")
                            return
                        }
                        
                        val jsonResponse = JSONObject(responseBody)
                        Logger.d(TAG, "fetchAngelHoldings() - JSON object keys: ${jsonResponse.keys().asSequence().toList()}")
                        Logger.d(TAG, "fetchAngelHoldings() - Full JSON response: $jsonResponse")
                        
                        val status = jsonResponse.optString("status", "")
                        Logger.d(TAG, "fetchAngelHoldings() - Status field value: $status")
                        
                        // Check for message field (error indication)
                        val message = jsonResponse.optString("message", "")
                        if (message.isNotEmpty()) {
                            Logger.d(TAG, "fetchAngelHoldings() - Message field value: $message")
                        }
                        
                        // Check for data array (could be in "data" or "holdings" field)
                        var dataArray = jsonResponse.optJSONArray("data")
                        if (dataArray == null) {
                            dataArray = jsonResponse.optJSONArray("holdings")
                        }
                        
                        val holdings = mutableListOf<Holding>()
                        
                        if (dataArray != null) {
                            Logger.d(TAG, "fetchAngelHoldings() - Found array with ${dataArray.length()} items")
                            for (i in 0 until dataArray.length()) {
                                try {
                                    val itemJson = dataArray.getJSONObject(i)
                                    Logger.d(TAG, "fetchAngelHoldings() - Item $i keys: ${itemJson.keys().asSequence().toList()}")
                                    if (i < 2) {  // Log first 2 items in detail
                                        Logger.d(TAG, "fetchAngelHoldings() - Item $i content: $itemJson")
                                    }
                                    
                                    val holding = Holding.fromAngelHolding(itemJson)
                                    if (holding != null) {
                                        holdings.add(holding)
                                        Logger.d(TAG, "fetchAngelHoldings() - Successfully parsed: ${holding.symbol} qty=${holding.qty}")
                                    } else {
                                        Logger.w(TAG, "fetchAngelHoldings() - Failed to parse item $i")
                                    }
                                } catch (e: Exception) {
                                    Logger.e(TAG, "fetchAngelHoldings() - Error parsing item $i: ${e.message}")
                                }
                            }
                        } else {
                            Logger.w(TAG, "fetchAngelHoldings() - No 'data' or 'holdings' array found in response")
                        }
                        
                        if (status.isEmpty() || status == "true" || status == "success" || holdings.isNotEmpty()) {
                            Logger.d(TAG, "fetchAngelHoldings() - Successfully fetched ${holdings.size} holdings")
                            callback(holdings, null)
                        } else if (status.isNotEmpty()) {
                            Logger.w(TAG, "fetchAngelHoldings() - API returned error status: $status")
                            callback(emptyList(), "API error: $status")
                        } else {
                            Logger.d(TAG, "fetchAngelHoldings() - No holdings or status info, but returning empty list")
                            callback(emptyList(), null)
                        }
                    } else if (response.code == 401) {
                        Logger.w(TAG, "fetchAngelHoldings() - Unauthorized (401) - JWT token may be expired or invalid")
                        Logger.w(TAG, "fetchAngelHoldings() - Error body: $responseBody")
                        callback(emptyList(), "Unauthorized - JWT token may be expired")
                    } else if (response.code == 403) {
                        Logger.w(TAG, "fetchAngelHoldings() - Forbidden (403) - API key or client code may be invalid")
                        Logger.w(TAG, "fetchAngelHoldings() - Error body: $responseBody")
                        callback(emptyList(), "Forbidden - Check API key and client code")
                    } else {
                        Logger.w(TAG, "fetchAngelHoldings() - Unexpected response code: ${response.code}")
                        Logger.w(TAG, "fetchAngelHoldings() - Error body: $responseBody")
                        callback(emptyList(), "Error: HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "fetchAngelHoldings() - Error parsing response: ${e.message}")
                    Logger.e(TAG, "fetchAngelHoldings() - Full response was: $responseBody")
                    callback(emptyList(), "Error parsing response: ${e.message}")
                }
            }
        })
    }
    
    // Save holdings to file
    fun saveHoldings(holdings: List<Holding>) {
        synchronized(holdingsLock) {
            try {
                val content = holdings.joinToString("\n") { holding ->
                    "${holding.symbol}|${holding.isin}|${holding.qty}|${holding.avgPrice}|${holding.ltp}|${holding.currentValue}|${holding.pnl}|${holding.pnlPercent}|${holding.source}|${holding.holdingType}"
                }
                holdingsFile.writeText(content)
                Logger.d(TAG, "saveHoldings() - Saved ${holdings.size} holdings to file")
            } catch (e: Exception) {
                Logger.e(TAG, "saveHoldings() - Error saving holdings: ${e.message}")
            }
        }
    }
    
    // Load holdings from file
    fun getHoldings(): List<Holding> {
        synchronized(holdingsLock) {
            return try {
                if (!holdingsFile.exists()) {
                    return emptyList()
                }
                
                val holdings = mutableListOf<Holding>()
                holdingsFile.readLines().forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = line.split("|")
                        if (parts.size >= 10) {
                            val holding = Holding(
                                symbol = parts[0],
                                isin = parts[1],
                                qty = parts[2].toIntOrNull() ?: 0,
                                avgPrice = parts[3].toDoubleOrNull() ?: 0.0,
                                ltp = parts[4].toDoubleOrNull() ?: 0.0,
                                currentValue = parts[5].toDoubleOrNull() ?: 0.0,
                                pnl = parts[6].toDoubleOrNull() ?: 0.0,
                                pnlPercent = parts[7].toDoubleOrNull() ?: 0.0,
                                source = parts[8],
                                holdingType = parts[9]
                            )
                            holdings.add(holding)
                        }
                    }
                }
                holdings
            } catch (e: Exception) {
                Logger.e(TAG, "getHoldings() - Error loading holdings: ${e.message}")
                emptyList()
            }
        }
    }
    
    // Update holding with new LTP and calculate P&L
    fun updateHoldingPrice(symbol: String, newLtp: Double): Holding? {
        synchronized(holdingsLock) {
            val holdings = getHoldings().toMutableList()
            val updatedHoldings = mutableListOf<Holding>()
            var updatedHolding: Holding? = null
            
            for (holding in holdings) {
                if (holding.symbol == symbol) {
                    val updated = holding.calculatePnL(newLtp)
                    updatedHoldings.add(updated)
                    updatedHolding = updated
                } else {
                    updatedHoldings.add(holding)
                }
            }
            
            if (updatedHolding != null) {
                saveHoldings(updatedHoldings)
            }
            
            return updatedHolding
        }
    }
    
    // Get portfolio summary
    fun getPortfolioSummary(): Triple<Double, Double, Double> {
        val holdings = getHoldings()
        var totalValue = 0.0
        var totalInvested = 0.0
        var totalPnL = 0.0
        
        for (holding in holdings) {
            totalValue += holding.currentValue
            totalInvested += (holding.qty * holding.avgPrice)
            totalPnL += holding.pnl
        }
        
        return Triple(totalValue, totalInvested, totalPnL)
    }
    
    // Combine duplicate holdings for the same symbol
    fun combineHoldings(holdings: List<Holding>): List<Holding> {
        val combined = mutableMapOf<String, Holding>()
        
        for (holding in holdings) {
            val key = "${holding.symbol}_${holding.source}_${holding.holdingType}"
            
            if (combined.containsKey(key)) {
                val existing = combined[key]!!
                
                // Calculate weighted average price
                val totalQty = existing.qty + holding.qty
                val totalCost = (existing.qty * existing.avgPrice) + (holding.qty * holding.avgPrice)
                val newAvgPrice = if (totalQty > 0) totalCost / totalQty else 0.0
                
                // Use the latest LTP
                val newLtp = if (holding.ltp > 0) holding.ltp else existing.ltp
                
                // Recalculate P&L
                val newCurrentValue = totalQty * newLtp
                val newPnL = newCurrentValue - totalCost
                val newPnLPercent = if (newAvgPrice > 0) ((newLtp - newAvgPrice) / newAvgPrice) * 100 else 0.0
                
                combined[key] = Holding(
                    symbol = existing.symbol,
                    isin = if (existing.isin.isNotEmpty()) existing.isin else holding.isin,
                    qty = totalQty,
                    avgPrice = newAvgPrice,
                    ltp = newLtp,
                    currentValue = newCurrentValue,
                    pnl = newPnL,
                    pnlPercent = newPnLPercent,
                    source = existing.source,
                    holdingType = existing.holdingType
                )
                
                Logger.d(TAG, "combineHoldings() - Combined ${holding.symbol}: ${existing.qty} + ${holding.qty} = $totalQty")
            } else {
                combined[key] = holding
            }
        }
        
        return combined.values.toList()
    }
}
