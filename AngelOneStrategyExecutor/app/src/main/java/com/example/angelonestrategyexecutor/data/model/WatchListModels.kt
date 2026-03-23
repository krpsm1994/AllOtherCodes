package com.example.angelonestrategyexecutor.data.model

data class OhlcvCandle(
    val timestamp: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

data class WatchListItem(
    val symbol: String,
    val symbolToken: String,
    val exchange: String,
    val lastFifteenMinuteCandle: OhlcvCandle,
    val lastOneHourCandle: OhlcvCandle,
    val lastDailyCandle: OhlcvCandle,
)

data class WatchListScanAnalysisEntry(
    val symbol: String,
    val failedConditions: List<String> = emptyList(),
    val lastEvaluated15mCandle: OhlcvCandle? = null,
    val lastEvaluated1hCandle: OhlcvCandle? = null,
    val lastEvaluatedDailyCandle: OhlcvCandle? = null,
    val hourSma10: Double? = null,
    val dailySma10: Double? = null,
    val dailySma20: Double? = null,
    val previousHourCandle: OhlcvCandle? = null,
    val previousDayCandle: OhlcvCandle? = null,
    val selectedOptionSymbol: String? = null,
    val selectedOptionToken: String? = null,
    val selectedOptionLotSize: Int? = null,
)

data class BacktestResultEntry(
    val candleDateTime: String,
    val symbol: String,
)

data class BacktestProgress(
    val isRunning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val results: List<BacktestResultEntry> = emptyList(),
    val message: String = "",
)

data class WatchListSnapshot(
    val updatedAtMillis: Long = 0L,
    val scanMode: String = "ONLY_0930",
    val totalScanned: Int = 0,
    val totalMatched: Int = 0,
    val message: String = "No scan yet",
    val items: List<WatchListItem> = emptyList(),
    val analysis: List<WatchListScanAnalysisEntry>? = null,
)
