package com.example.stocksmonitor

data class LogEntry(
    val timestamp: Long,
    val type: LogType,
    val symbol: String,
    val message: String
) {
    fun getFormattedTime(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(timestamp))
    }

    fun toFileString(): String {
        return "$timestamp|${type.name}|$symbol|$message"
    }

    companion object {
        fun fromFileString(line: String): LogEntry? {
            return try {
                val parts = line.split("|", limit = 4)
                if (parts.size == 4) {
                    LogEntry(
                        timestamp = parts[0].toLong(),
                        type = LogType.valueOf(parts[1]),
                        symbol = parts[2],
                        message = parts[3]
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

enum class LogType {
    STATUS_CHANGE,
    STOCK_ADDED,
    STOCK_UPDATED,
    STOCK_DELETED,
    STOCK_MOVED_TO_HISTORY,
    STOCK_RESTORED,
    SL_HIT,
    TARGET_HIT
}
