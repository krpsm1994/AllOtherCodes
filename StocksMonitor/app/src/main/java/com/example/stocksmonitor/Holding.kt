package com.example.stocksmonitor

data class Holding(
    val symbol: String,
    val isin: String = "",
    val qty: Int = 0,
    val avgPrice: Double = 0.0,
    val ltp: Double = 0.0,  // Last traded price (updates from websocket)
    val currentValue: Double = 0.0,  // qty * ltp
    val pnl: Double = 0.0,  // Current P&L in rupees
    val pnlPercent: Double = 0.0,  // Current P&L percentage
    val source: String = "kite",  // "kite", "angel"
    val holdingType: String = "long",  // "long" or "short"
) {
    // Calculate current P&L based on current LTP
    fun calculatePnL(currentPrice: Double): Holding {
        val newCurrentValue = qty * currentPrice
        val newPnL = newCurrentValue - (qty * avgPrice)
        val newPnLPercent = if (avgPrice > 0) ((currentPrice - avgPrice) / avgPrice) * 100 else 0.0
        
        return this.copy(
            ltp = currentPrice,
            currentValue = newCurrentValue,
            pnl = newPnL,
            pnlPercent = newPnLPercent
        )
    }
    
    // Create from Kite Holdings API response
    companion object {
        fun fromKiteHolding(json: org.json.JSONObject): Holding? {
            return try {
                val symbol = json.optString("tradingsymbol", "")
                val isin = json.optString("isin", "")
                var qty = json.optInt("quantity", 0)
                
                // If quantity is 0, check for t1_quantity (trading day 1 quantity)
                if (qty == 0) {
                    qty = json.optInt("t1_quantity", 0)
                }
                // Check other quantity fields
                if (qty == 0) {
                    qty = json.optInt("realised_quantity", 0)
                }
                
                val avgPrice = json.optDouble("average_price", 0.0)
                val ltp = json.optDouble("last_price", 0.0)
                
                if (symbol.isEmpty()) return null
                
                val currentValue = qty * ltp
                val pnl = currentValue - (qty * avgPrice)
                val pnlPercent = if (avgPrice > 0) ((ltp - avgPrice) / avgPrice) * 100 else 0.0
                
                Holding(
                    symbol = symbol,
                    isin = isin,
                    qty = qty,
                    avgPrice = avgPrice,
                    ltp = ltp,
                    currentValue = currentValue,
                    pnl = pnl,
                    pnlPercent = pnlPercent,
                    source = "kite",
                    holdingType = "long"
                )
            } catch (e: Exception) {
                com.example.stocksmonitor.Logger.e("Holding", "fromKiteHolding() - Error parsing: ${e.message}")
                null
            }
        }
        
        fun fromKitePosition(json: org.json.JSONObject): Holding? {
            return try {
                val symbol = json.optString("tradingsymbol", "")
                var qty = json.optInt("quantity", 0)
                
                // If quantity is 0, check for net_quantity or day_quantity
                if (qty == 0) {
                    qty = json.optInt("net_quantity", 0)
                }
                if (qty == 0) {
                    qty = json.optInt("day_quantity", 0)
                }
                
                val avgPrice = json.optDouble("average_price", 0.0)
                val ltp = json.optDouble("last_price", 0.0)
                
                if (symbol.isEmpty() || qty == 0) return null
                
                val currentValue = qty * ltp
                val pnl = currentValue - (qty * avgPrice)
                val pnlPercent = if (avgPrice > 0) ((ltp - avgPrice) / avgPrice) * 100 else 0.0
                
                Holding(
                    symbol = symbol,
                    isin = "",
                    qty = qty,
                    avgPrice = avgPrice,
                    ltp = ltp,
                    currentValue = currentValue,
                    pnl = pnl,
                    pnlPercent = pnlPercent,
                    source = "kite",
                    holdingType = "short"
                )
            } catch (e: Exception) {
                com.example.stocksmonitor.Logger.e("Holding", "fromKitePosition() - Error parsing: ${e.message}")
                null
            }
        }
        
        fun fromAngelHolding(json: org.json.JSONObject): Holding? {
            return try {
                com.example.stocksmonitor.Logger.d("Holding", "fromAngelHolding() - Parsing JSON with keys: ${json.keys().asSequence().toList()}")
                
                val symbol = json.optString("tradingsymbol", "")
                com.example.stocksmonitor.Logger.d("Holding", "fromAngelHolding() - tradingsymbol: $symbol")
                
                val isin = json.optString("isin", "")
                com.example.stocksmonitor.Logger.d("Holding", "fromAngelHolding() - isin: $isin")
                
                var qty = json.optInt("holdingqty", 0)
                com.example.stocksmonitor.Logger.d("Holding", "fromAngelHolding() - holdingqty: $qty")
                
                // Check alternative quantity fields
                if (qty == 0) {
                    qty = json.optInt("quantity", 0)
                    if (qty != 0) com.example.stocksmonitor.Logger.d("Holding", "fromAngelHolding() - Using quantity: $qty")
                }
                if (qty == 0) {
                    qty = json.optInt("qty", 0)
                    if (qty != 0) com.example.stocksmonitor.Logger.d("Holding", "fromAngelHolding() - Using qty: $qty")
                }
                if (qty == 0) {
                    qty = json.optInt("t1qty", 0)
                    if (qty != 0) com.example.stocksmonitor.Logger.d("Holding", "fromAngelHolding() - Using t1qty: $qty")
                }
                
                val avgPrice = json.optDouble("avgPrice", 0.0).let { 
                    if (it == 0.0) json.optDouble("averageprice", 0.0) else it 
                }
                com.example.stocksmonitor.Logger.d("Holding", "fromAngelHolding() - avgPrice: $avgPrice")
                
                val ltp = json.optDouble("LTP", 0.0).let {
                    if (it == 0.0) json.optDouble("ltp", 0.0) else it
                }
                com.example.stocksmonitor.Logger.d("Holding", "fromAngelHolding() - LTP: $ltp")
                
                if (symbol.isEmpty()) {
                    com.example.stocksmonitor.Logger.w("Holding", "fromAngelHolding() - Empty symbol, skipping")
                    return null
                }
                
                val currentValue = qty * ltp
                val pnl = currentValue - (qty * avgPrice)
                val pnlPercent = if (avgPrice > 0) ((ltp - avgPrice) / avgPrice) * 100 else 0.0
                
                com.example.stocksmonitor.Logger.d("Holding", "fromAngelHolding() - Successfully created holding: $symbol qty=$qty avgPrice=$avgPrice ltp=$ltp pnl=$pnl")
                
                Holding(
                    symbol = symbol,
                    isin = isin,
                    qty = qty,
                    avgPrice = avgPrice,
                    ltp = ltp,
                    currentValue = currentValue,
                    pnl = pnl,
                    pnlPercent = pnlPercent,
                    source = "angel",
                    holdingType = "long"
                )
            } catch (e: Exception) {
                com.example.stocksmonitor.Logger.e("Holding", "fromAngelHolding() - Error parsing: ${e.message}")
                null
            }
        }
    }
}
