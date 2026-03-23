package com.example.stocksmonitor

data class Instrument(
    val instrumentToken: String,
    val exchangeToken: String,
    val tradingSymbol: String,
    val name: String,
    val lastPrice: String,
    val expiry: String,
    val strike: String,
    val tickSize: String,
    val lotSize: String,
    val instrumentType: String,
    val segment: String,
    val exchange: String
) {
    fun toFileString(): String {
        return "$instrumentToken,$exchangeToken,$tradingSymbol,$name,$lastPrice,$expiry,$strike,$tickSize,$lotSize,$instrumentType,$segment,$exchange"
    }

    companion object {
        fun fromCsvLine(line: String): Instrument? {
            return try {
                val parts = line.split(",")
                if (parts.size >= 12) {
                    Instrument(
                        instrumentToken = parts[0].trim(),
                        exchangeToken = parts[1].trim(),
                        tradingSymbol = parts[2].trim(),
                        name = parts[3].trim(),
                        lastPrice = parts[4].trim(),
                        expiry = parts[5].trim(),
                        strike = parts[6].trim(),
                        tickSize = parts[7].trim(),
                        lotSize = parts[8].trim(),
                        instrumentType = parts[9].trim(),
                        segment = parts[10].trim(),
                        exchange = parts[11].trim()
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }

        fun fromAngelJson(json: String): Instrument? {
            return try {
                // Simple JSON parsing for AngelOne format
                // Example: {"token":"1","symbol":"YESBANK-EQ","name":"YES BANK LIMITED","expiry":"","strike":"-1.000000","lotsize":"1","instrumenttype":"","exch_seg":"NSE","tick_size":"5.000000"}
                
                val token = extractJsonValue(json, "token") ?: ""
                val symbol = extractJsonValue(json, "symbol") ?: ""
                val name = extractJsonValue(json, "name") ?: ""
                val expiry = extractJsonValue(json, "expiry") ?: ""
                val strike = extractJsonValue(json, "strike") ?: ""
                val lotsize = extractJsonValue(json, "lotsize") ?: "1"
                val instrumenttype = extractJsonValue(json, "instrumenttype") ?: ""
                val exch_seg = extractJsonValue(json, "exch_seg") ?: ""
                val tick_size = extractJsonValue(json, "tick_size") ?: ""
                
                if (token.isNotEmpty() && symbol.isNotEmpty()) {
                    Instrument(
                        instrumentToken = token,
                        exchangeToken = token, // AngelOne uses same token
                        tradingSymbol = symbol,
                        name = name,
                        lastPrice = "0",
                        expiry = expiry,
                        strike = strike,
                        tickSize = tick_size,
                        lotSize = lotsize,
                        instrumentType = if (symbol.endsWith("-EQ")) "EQ" else instrumenttype,
                        segment = exch_seg,
                        exchange = if (exch_seg.contains("NSE")) "NSE" else exch_seg
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }

        private fun extractJsonValue(json: String, key: String): String? {
            return try {
                val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
                val regex = Regex(pattern)
                val match = regex.find(json)
                match?.groupValues?.get(1)
            } catch (e: Exception) {
                null
            }
        }
    }
}
