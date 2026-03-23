package com.example.stocksmonitor

enum class StockStatus {
    NOT_TRIGGERED,
    ORDER_PLACED,
    TRIGGERED,
    SL_HIT,
    TARGET_HIT,
    HISTORY
}

data class Stock(
    val instrument: Instrument,
    val buyPrice: Double,
    val stopLoss: Double,
    val target: Double,
    val quantity: Int = 1,
    val onlyWatch: Boolean = false,
    val status: StockStatus = StockStatus.NOT_TRIGGERED,
    val orderId: String = "",  // Kite order ID when order is placed
    val finalStatus: StockStatus? = null,  // Status when moved to history (SL_HIT or TARGET_HIT)
    val finalLTP: Double? = null,          // LTP when moved to history
    val finalPercentage: Double? = null    // Price difference percentage when moved to history
) {
    fun toFileString(): String {
        val finalStatusStr = finalStatus?.name ?: "null"
        val finalLTPStr = finalLTP?.toString() ?: "null"
        val finalPercentageStr = finalPercentage?.toString() ?: "null"
        return "${instrument.toFileString()}|$buyPrice|$stopLoss|$target|$quantity|$onlyWatch|${status.name}|$orderId|$finalStatusStr|$finalLTPStr|$finalPercentageStr"
    }

    companion object {
        fun fromFileString(line: String): Stock? {
            return try {
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val instrument = Instrument.fromCsvLine(parts[0])
                    if (instrument != null) {
                        // Detect file format: new format has quantity (int) at index 4, old format has status (enum) at index 4
                        // Check if parts[4] is a valid integer to determine format
                        val isNewFormat = parts.size > 4 && parts[4].toIntOrNull() != null
                        
                        val quantity: Int
                        val onlyWatch: Boolean
                        val status: StockStatus
                        val finalStatus: StockStatus?
                        val finalLTP: Double?
                        val finalPercentage: Double?
                        
                        if (isNewFormat) {
                            // New format with orderId: instrument|buyPrice|stopLoss|target|quantity|onlyWatch|status|orderId|finalStatus|finalLTP|finalPercentage
                            quantity = if (parts.size > 4) parts[4].toIntOrNull() ?: 1 else 1
                            onlyWatch = if (parts.size > 5) parts[5].toBoolean() else false
                            status = if (parts.size > 6) {
                                try {
                                    StockStatus.valueOf(parts[6])
                                } catch (e: Exception) {
                                    StockStatus.NOT_TRIGGERED
                                }
                            } else {
                                StockStatus.NOT_TRIGGERED
                            }
                            val orderId = if (parts.size > 7) parts[7] else ""
                            finalStatus = if (parts.size > 8 && parts[8] != "null") {
                                try {
                                    StockStatus.valueOf(parts[8])
                                } catch (e: Exception) {
                                    null
                                }
                            } else {
                                null
                            }
                            finalLTP = if (parts.size > 9 && parts[9] != "null") {
                                parts[9].toDoubleOrNull()
                            } else {
                                null
                            }
                            finalPercentage = if (parts.size > 10 && parts[10] != "null") {
                                parts[10].toDoubleOrNull()
                            } else {
                                null
                            }
                            
                            Stock(
                                instrument = instrument,
                                buyPrice = parts[1].toDouble(),
                                stopLoss = parts[2].toDouble(),
                                target = parts[3].toDouble(),
                                quantity = quantity,
                                onlyWatch = onlyWatch,
                                status = status,
                                orderId = orderId,
                                finalStatus = finalStatus,
                                finalLTP = finalLTP,
                                finalPercentage = finalPercentage
                            )
                        } else {
                            // Old format without orderId: instrument|buyPrice|stopLoss|target|quantity|onlyWatch|status|finalStatus|finalLTP|finalPercentage
                            quantity = if (parts.size > 4) parts[4].toIntOrNull() ?: 1 else 1
                            onlyWatch = if (parts.size > 5) parts[5].toBoolean() else false
                            status = if (parts.size > 6) {
                                try {
                                    StockStatus.valueOf(parts[6])
                                } catch (e: Exception) {
                                    StockStatus.NOT_TRIGGERED
                                }
                            } else {
                                StockStatus.NOT_TRIGGERED
                            }
                            finalStatus = if (parts.size > 7 && parts[7] != "null") {
                                try {
                                    StockStatus.valueOf(parts[7])
                                } catch (e: Exception) {
                                    null
                                }
                            } else {
                                null
                            }
                            finalLTP = if (parts.size > 8 && parts[8] != "null") {
                                parts[8].toDoubleOrNull()
                            } else {
                                null
                            }
                            finalPercentage = if (parts.size > 9 && parts[9] != "null") {
                                parts[9].toDoubleOrNull()
                            } else {
                                null
                            }
                            
                            Stock(
                                instrument = instrument,
                                buyPrice = parts[1].toDouble(),
                                stopLoss = parts[2].toDouble(),
                                target = parts[3].toDouble(),
                                quantity = quantity,
                                onlyWatch = onlyWatch,
                                status = status,
                                orderId = "",
                                finalStatus = finalStatus,
                                finalLTP = finalLTP,
                                finalPercentage = finalPercentage
                            )
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
