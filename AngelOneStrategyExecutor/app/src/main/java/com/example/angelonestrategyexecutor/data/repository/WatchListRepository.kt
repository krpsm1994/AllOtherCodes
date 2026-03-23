package com.example.angelonestrategyexecutor.data.repository

import android.content.Context
import android.util.Log
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.example.angelonestrategyexecutor.data.config.AppConfig
import com.example.angelonestrategyexecutor.data.model.CandleDataPoint
import com.example.angelonestrategyexecutor.data.model.OhlcvCandle
import com.example.angelonestrategyexecutor.data.model.WatchListItem
import com.example.angelonestrategyexecutor.data.model.WatchListScanAnalysisEntry
import com.example.angelonestrategyexecutor.data.model.BacktestProgress
import com.example.angelonestrategyexecutor.data.model.BacktestResultEntry
import com.example.angelonestrategyexecutor.data.model.WatchListSnapshot
import com.google.gson.Gson
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

private data class WatchListCachedInstrumentItem(
    val token: String,
    val symbol: String,
    val exchSeg: String = "",
)

private data class WatchListDerivativeItem(
    val token: String,
    val symbol: String,
    val lotSize: Int = 0,
    val exchSeg: String = "",
)

private data class WatchListInstrumentsPayload(
    val equities: List<WatchListCachedInstrumentItem> = emptyList(),
    val derivatives: List<WatchListDerivativeItem> = emptyList(),
)

private data class TimedCandle(
    val dateTime: ZonedDateTime,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

private data class AggregatedBar(
    val dateTime: ZonedDateTime,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

private data class DailyBar(
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

private data class StockEvaluationResult(
    val matchedItem: WatchListItem?,
    val failedConditions: List<String>,
    val lastEvaluated15mCandle: OhlcvCandle? = null,
    val lastEvaluated1hCandle: OhlcvCandle? = null,
    val lastEvaluatedDailyCandle: OhlcvCandle? = null,
    val hourSma10: Double? = null,
    val dailySma10: Double? = null,
    val dailySma20: Double? = null,
    val previousHourCandle: OhlcvCandle? = null,
    val previousDayCandle: OhlcvCandle? = null,
)

object WatchListRepository {

    private const val TAG = "WatchListRepo"
    private const val INSTRUMENTS_CACHE_FILE = "instruments_cache.json"
    private const val WATCHLIST_CACHE_FILE = "watchlist_results.json"
    private const val BACKTEST_RESULTS_FILE = "backtest_results.json"
    private const val API_THROTTLE_DELAY_MS = 350L
    private const val LOOKBACK_DAYS = 50L

    private val gson = Gson()
    private val istZone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val firstCandleTime = LocalTime.of(9, 15)
    private val scanReadyTime = LocalTime.of(9, 30)
    private val scanEndTime = LocalTime.of(15, 15)
    private val marketOpenTime = LocalTime.of(9, 15)
    private val marketCloseTime = LocalTime.of(15, 15)
    private val hourlyBucketStartTimes = listOf(
        LocalTime.of(9, 15),
        LocalTime.of(10, 15),
        LocalTime.of(11, 15),
        LocalTime.of(12, 15),
        LocalTime.of(13, 15),
        LocalTime.of(14, 15),
        LocalTime.of(15, 15),
    )
    private val fullSession15mSlots = buildList {
        var current = marketOpenTime
        while (!current.isAfter(marketCloseTime)) {
            add(current)
            current = current.plusMinutes(15)
        }
    }
    private val scanMutex = Mutex()
    private val backtestMutex = Mutex()
    @Volatile private var cancelScanRequested = false
    @Volatile private var cancelBacktestRequested = false

    fun stopScan() {
        cancelScanRequested = true
    }

    fun stopBacktest() {
        cancelBacktestRequested = true
    }

    private val _snapshot = MutableStateFlow(WatchListSnapshot())
    val snapshot: StateFlow<WatchListSnapshot> = _snapshot.asStateFlow()

    private val _backtestProgress = MutableStateFlow(BacktestProgress())
    val backtestProgress: StateFlow<BacktestProgress> = _backtestProgress.asStateFlow()

    @Volatile
    private var isInitialized = false
    private lateinit var watchListFile: File
    private lateinit var backtestResultsFile: File

    fun init(context: Context) {
        ensureInitialized(context)
    }

    suspend fun scanAndUpdate(
        context: Context,
        assumeCurrentTimeIs0930: Boolean = false,
        manualScanDateTimeMillis: Long? = null,
    ): WatchListSnapshot = withContext(Dispatchers.IO) {
        if (!scanMutex.tryLock()) {
            val current = _snapshot.value
            return@withContext WatchListSnapshot(
                updatedAtMillis = current.updatedAtMillis,
                scanMode = current.scanMode,
                totalScanned = current.totalScanned,
                totalMatched = current.totalMatched,
                message = "Skipped: scan already in progress",
                items = current.items,
                analysis = current.analysis,
            )
        }

        try {
        cancelScanRequested = false
        ensureInitialized(context)

        val appContext = context.applicationContext
        AppConfig.init(appContext)
        AuthState.init(appContext)

        val scanMode = AppConfig.watchListScanMode.value
        val currentNow = ZonedDateTime.now(istZone)
        val now = when {
            manualScanDateTimeMillis != null -> Instant.ofEpochMilli(manualScanDateTimeMillis).atZone(istZone)
            assumeCurrentTimeIs0930 -> currentNow
                .withHour(9)
                .withMinute(30)
                .withSecond(0)
                .withNano(0)
            else -> currentNow
        }
        val previous = _snapshot.value
        val bypassReadyTimeCheck = assumeCurrentTimeIs0930 || manualScanDateTimeMillis != null
        val isAutomaticRun = !bypassReadyTimeCheck

        if (!bypassReadyTimeCheck && now.toLocalTime().isBefore(scanReadyTime)) {
            val skipped = WatchListSnapshot(
                updatedAtMillis = System.currentTimeMillis(),
                scanMode = scanMode,
                totalScanned = previous.totalScanned,
                totalMatched = previous.totalMatched,
                message = "Skipped: waiting for 09:15 candle close",
                items = previous.items,
                analysis = previous.analysis,
            )
            saveToDisk(skipped)
            return@withContext skipped
        }

        if (isAutomaticRun && now.toLocalTime().isAfter(scanEndTime)) {
            val skipped = WatchListSnapshot(
                updatedAtMillis = System.currentTimeMillis(),
                scanMode = scanMode,
                totalScanned = previous.totalScanned,
                totalMatched = previous.totalMatched,
                message = "Skipped: outside scan window (09:30-15:15)",
                items = previous.items,
                analysis = previous.analysis,
            )
            saveToDisk(skipped)
            return@withContext skipped
        }

        val creds = AuthState.credentials.value
        if (creds == null || creds.isExpired) {
            val skipped = WatchListSnapshot(
                updatedAtMillis = System.currentTimeMillis(),
                scanMode = scanMode,
                totalScanned = previous.totalScanned,
                totalMatched = previous.totalMatched,
                message = "Skipped: login session not available or expired",
                items = previous.items,
                analysis = previous.analysis,
            )
            saveToDisk(skipped)
            return@withContext skipped
        }

        val equities = loadEquities(appContext)
        val derivatives = loadDerivatives(appContext)
        if (equities.isEmpty()) {
            val empty = WatchListSnapshot(
                updatedAtMillis = System.currentTimeMillis(),
                scanMode = scanMode,
                totalScanned = previous.totalScanned,
                totalMatched = previous.totalMatched,
                message = "No equity universe found in instruments cache",
                items = previous.items,
                analysis = previous.analysis,
            )
            saveToDisk(empty)
            return@withContext empty
        }

        // Use 09:29 as API cutoff to avoid pulling the 09:30 candle (which may be incomplete).
        val cutoff = now
            .withHour(9)
            .withMinute(29)
            .withSecond(0)
            .withNano(0)
        val from = cutoff.minusDays(LOOKBACK_DAYS)

        var scannedCount = 0
        val matched = mutableListOf<WatchListItem>()
        val analysisRows = mutableListOf<WatchListScanAnalysisEntry>()
        val totalEquities = equities.size

        equities.forEachIndexed { index, equity ->
            if (cancelScanRequested) return@forEachIndexed
            if (index > 0) delay(API_THROTTLE_DELAY_MS)

            val exchange = resolveExchange(equity.exchSeg)
            val candles = HistoricalCandleRepository.getCandlesInRange(
                exchange = exchange,
                symbolToken = equity.token,
                interval = "FIFTEEN_MINUTE",
                fromMillis = from.toInstant().toEpochMilli(),
                toMillis = cutoff.toInstant().toEpochMilli(),
            )

            scannedCount += 1
            if (candles.isEmpty()) {
                analysisRows.add(
                    WatchListScanAnalysisEntry(
                        symbol = equity.symbol,
                        failedConditions = listOf("Historical candles not available"),
                        lastEvaluated15mCandle = null,
                        lastEvaluated1hCandle = null,
                        lastEvaluatedDailyCandle = null,
                    )
                )
            } else {
                val evaluation = evaluateStock(
                    symbol = equity.symbol,
                    symbolToken = equity.token,
                    exchange = exchange,
                    candles = candles,
                    scanDate = cutoff.toLocalDate(),
                )
                val isMatched = evaluation.failedConditions.isEmpty()
                val selectedOption = if (isMatched) selectCeOption(
                    stockSymbol = equity.symbol,
                    strikeAbove = evaluation.lastEvaluated15mCandle?.high ?: 0.0,
                    scanDate = cutoff.toLocalDate(),
                    derivatives = derivatives,
                ) else null
                analysisRows.add(
                    WatchListScanAnalysisEntry(
                        symbol = equity.symbol,
                        failedConditions = if (isMatched) {
                            listOf("All conditions met")
                        } else {
                            evaluation.failedConditions
                        },
                        lastEvaluated15mCandle = evaluation.lastEvaluated15mCandle,
                        lastEvaluated1hCandle = evaluation.lastEvaluated1hCandle,
                        lastEvaluatedDailyCandle = evaluation.lastEvaluatedDailyCandle,
                        hourSma10 = evaluation.hourSma10,
                        dailySma10 = evaluation.dailySma10,
                        dailySma20 = evaluation.dailySma20,
                        previousHourCandle = evaluation.previousHourCandle,
                        previousDayCandle = evaluation.previousDayCandle,
                        selectedOptionSymbol = selectedOption?.symbol,
                        selectedOptionToken = selectedOption?.token,
                        selectedOptionLotSize = selectedOption?.lotSize,
                    )
                )
                evaluation.matchedItem?.let { matched.add(it) }
            }

            // Emit incremental progress so the UI updates after each stock.
            _snapshot.value = WatchListSnapshot(
                updatedAtMillis = System.currentTimeMillis(),
                scanMode = scanMode,
                totalScanned = scannedCount,
                totalMatched = matched.size,
                message = "Scanning $scannedCount / $totalEquities…",
                items = matched.sortedBy { it.symbol },
                analysis = analysisRows.sortedBy { it.symbol },
            )
        }

        val finalSnapshot = WatchListSnapshot(
            updatedAtMillis = System.currentTimeMillis(),
            scanMode = scanMode,
            totalScanned = scannedCount,
            totalMatched = matched.size,
            message = if (cancelScanRequested) "Scan stopped at $scannedCount / $totalEquities" else "Scan completed",
            items = matched.sortedBy { it.symbol },
            analysis = analysisRows.sortedBy { it.symbol },
        )
        saveToDisk(finalSnapshot)
        finalSnapshot
        } finally {
            scanMutex.unlock()
        }
    }

    fun reloadFromDisk(context: Context) {
        ensureInitialized(context)
        loadFromDisk()
    }

    private fun evaluateStock(
        symbol: String,
        symbolToken: String,
        exchange: String,
        candles: List<CandleDataPoint>,
        scanDate: LocalDate,
    ): StockEvaluationResult {
        val timedCandles = candles
            .mapNotNull { toTimedCandle(it) }
            .sortedBy { it.dateTime.toInstant().toEpochMilli() }

        if (timedCandles.isEmpty()) {
            return StockEvaluationResult(
                matchedItem = null,
                failedConditions = listOf("No valid candles after parsing"),
                lastEvaluated15mCandle = null,
                lastEvaluated1hCandle = null,
                lastEvaluatedDailyCandle = null,
            )
        }

        val firstCandle = timedCandles
            .filter {
                it.dateTime.toLocalDate() == scanDate &&
                    it.dateTime.toLocalTime().hour == firstCandleTime.hour &&
                    it.dateTime.toLocalTime().minute == firstCandleTime.minute
            }
            .minByOrNull { it.dateTime.toInstant().toEpochMilli() }
            ?: return StockEvaluationResult(
                matchedItem = null,
                failedConditions = listOf("9:15 candle not found"),
                lastEvaluated15mCandle = null,
                lastEvaluated1hCandle = null,
                lastEvaluatedDailyCandle = null,
            )

        val evaluated15mCandle = toOhlcvCandle(firstCandle)

        // ── Stage 1: Volume ────────────────────────────────────────────────────
        // Sum all available scan-day 15-min candle volumes. Evaluated first because
        // it is cheap (no bar-building) and eliminates illiquid stocks immediately.
        val scanDayVolume = timedCandles
            .filter {
                it.dateTime.toLocalDate() == scanDate &&
                    !it.dateTime.toLocalTime().isBefore(marketOpenTime) &&
                    !it.dateTime.toLocalTime().isAfter(marketCloseTime)
            }
            .sumOf { it.volume }
        if (scanDayVolume <= 100_000.0) {
            return StockEvaluationResult(
                matchedItem = null,
                failedConditions = listOf("6) Daily volume <= 100000"),
                lastEvaluated15mCandle = evaluated15mCandle,
                lastEvaluated1hCandle = null,
                lastEvaluatedDailyCandle = null,
            )
        }

        // ── Stage 2: 15-minute candle conditions ──────────────────────────────
        // Uses only the 9:15 candle — no expensive bar building required.
        // Range check reference: close of the last 15-min candle before scan date
        // (≈ previous trading day's closing price).
        val prevDayLastClose = timedCandles
            .lastOrNull { it.dateTime.toLocalDate() < scanDate }?.close ?: firstCandle.close
        if (firstCandle.close <= 50.0) return StockEvaluationResult(
            matchedItem = null,
            failedConditions = listOf("7) 9:15 candle close <= 50"),
            lastEvaluated15mCandle = evaluated15mCandle,
            lastEvaluated1hCandle = null,
            lastEvaluatedDailyCandle = null,
        )
        if (firstCandle.close <= firstCandle.open) return StockEvaluationResult(
            matchedItem = null,
            failedConditions = listOf("9) 9:15 close <= 9:15 open"),
            lastEvaluated15mCandle = evaluated15mCandle,
            lastEvaluated1hCandle = null,
            lastEvaluatedDailyCandle = null,
        )
        if ((firstCandle.high - firstCandle.low) >= prevDayLastClose * 0.03) return StockEvaluationResult(
            matchedItem = null,
            failedConditions = listOf("8) 9:15 range >= 3% of previous close"),
            lastEvaluated15mCandle = evaluated15mCandle,
            lastEvaluated1hCandle = null,
            lastEvaluatedDailyCandle = null,
        )

        // ── Stage 3: Hourly conditions ─────────────────────────────────────────
        // buildHourlyBars is only invoked after stages 1+2 pass.
        val hourlyBars = buildHourlyBars(timedCandles)
        if (hourlyBars.size < 10) {
            return StockEvaluationResult(
                matchedItem = null,
                failedConditions = listOf("Insufficient 1-hour candles (< 10 complete bars)"),
                lastEvaluated15mCandle = evaluated15mCandle,
                lastEvaluated1hCandle = null,
                lastEvaluatedDailyCandle = null,
            )
        }
        val previousHourBar = hourlyBars.last()
        val hourSma10 = hourlyBars.takeLast(10).map { it.close }.average()

        // Scan-day 1h bar (9:15 bucket, falls back to 9:15 candle when slice is at 9:29)
        val scanDaySlotMap = timedCandles
            .filter { it.dateTime.toLocalDate() == scanDate }
            .associateBy { it.dateTime.toLocalTime() }
        val hour915BucketSlots = listOf(
            LocalTime.of(9, 15),
            LocalTime.of(9, 30),
            LocalTime.of(9, 45),
            LocalTime.of(10, 0),
        )
        val scanDayHourBar = if (hour915BucketSlots.all { scanDaySlotMap.containsKey(it) }) {
            val slots = hour915BucketSlots.mapNotNull { scanDaySlotMap[it] }
            AggregatedBar(
                dateTime = ZonedDateTime.of(scanDate, firstCandleTime, istZone),
                open = slots.first().open,
                high = slots.maxOf { it.high },
                low = slots.minOf { it.low },
                close = slots.last().close,
                volume = slots.sumOf { it.volume },
            )
        } else {
            AggregatedBar(
                dateTime = ZonedDateTime.of(scanDate, firstCandleTime, istZone),
                open = firstCandle.open,
                high = firstCandle.high,
                low = firstCandle.low,
                close = firstCandle.close,
                volume = firstCandle.volume,
            )
        }
        val evaluated1hCandle = toOhlcvCandle(scanDayHourBar)

        if (scanDayHourBar.close <= previousHourBar.high) return StockEvaluationResult(
            matchedItem = null,
            failedConditions = listOf("2) 1-hour close <= previous 1-hour high"),
            lastEvaluated15mCandle = evaluated15mCandle,
            lastEvaluated1hCandle = evaluated1hCandle,
            lastEvaluatedDailyCandle = null,
            hourSma10 = hourSma10,
            previousHourCandle = toOhlcvCandle(previousHourBar),
        )
        if (scanDayHourBar.close <= hourSma10) return StockEvaluationResult(
            matchedItem = null,
            failedConditions = listOf("1) 1-hour close <= 1-hour SMA(10)"),
            lastEvaluated15mCandle = evaluated15mCandle,
            lastEvaluated1hCandle = evaluated1hCandle,
            lastEvaluatedDailyCandle = null,
            hourSma10 = hourSma10,
            previousHourCandle = toOhlcvCandle(previousHourBar),
        )

        // ── Stage 4: Daily conditions ──────────────────────────────────────────
        // buildDailyBars is only invoked after stages 1+2+3 pass.
        val completedDailyBars = buildDailyBars(timedCandles).filter { it.date < scanDate }
        if (completedDailyBars.size < 20) {
            return StockEvaluationResult(
                matchedItem = null,
                failedConditions = listOf("Insufficient daily candles (< 20 completed days)"),
                lastEvaluated15mCandle = evaluated15mCandle,
                lastEvaluated1hCandle = evaluated1hCandle,
                lastEvaluatedDailyCandle = null,
                hourSma10 = hourSma10,
                previousHourCandle = toOhlcvCandle(previousHourBar),
            )
        }
        val previousDailyBar = completedDailyBars.last()
        val dailySma10 = completedDailyBars.takeLast(10).map { it.close }.average()
        val dailySma20 = completedDailyBars.takeLast(20).map { it.close }.average()

        // Scan-day daily bar (falls back to 9:15 candle when full session not yet available)
        val scanDaySessionSlots = timedCandles
            .filter { it.dateTime.toLocalDate() == scanDate }
            .filter {
                val t = it.dateTime.toLocalTime()
                !t.isBefore(marketOpenTime) && !t.isAfter(marketCloseTime)
            }
            .associateBy { it.dateTime.toLocalTime() }
        val scanDayDailyBar = if (fullSession15mSlots.all { scanDaySessionSlots.containsKey(it) }) {
            val sorted = fullSession15mSlots.mapNotNull { scanDaySessionSlots[it] }
            DailyBar(
                date = scanDate,
                open = sorted.first().open,
                high = sorted.maxOf { it.high },
                low = sorted.minOf { it.low },
                close = sorted.last().close,
                volume = sorted.sumOf { it.volume },
            )
        } else {
            DailyBar(
                date = scanDate,
                open = firstCandle.open,
                high = firstCandle.high,
                low = firstCandle.low,
                close = firstCandle.close,
                volume = firstCandle.volume,
            )
        }
        val evaluatedDailyCandle = toOhlcvCandle(scanDayDailyBar)

        if (scanDayDailyBar.close <= previousDailyBar.high) return StockEvaluationResult(
            matchedItem = null,
            failedConditions = listOf("3) Daily close <= previous day high"),
            lastEvaluated15mCandle = evaluated15mCandle,
            lastEvaluated1hCandle = evaluated1hCandle,
            lastEvaluatedDailyCandle = evaluatedDailyCandle,
            hourSma10 = hourSma10,
            dailySma10 = dailySma10,
            dailySma20 = dailySma20,
            previousHourCandle = toOhlcvCandle(previousHourBar),
            previousDayCandle = toOhlcvCandle(previousDailyBar),
        )
        if (scanDayDailyBar.close <= dailySma10) return StockEvaluationResult(
            matchedItem = null,
            failedConditions = listOf("4) Daily close <= Daily SMA(10)"),
            lastEvaluated15mCandle = evaluated15mCandle,
            lastEvaluated1hCandle = evaluated1hCandle,
            lastEvaluatedDailyCandle = evaluatedDailyCandle,
            hourSma10 = hourSma10,
            dailySma10 = dailySma10,
            dailySma20 = dailySma20,
            previousHourCandle = toOhlcvCandle(previousHourBar),
            previousDayCandle = toOhlcvCandle(previousDailyBar),
        )
        if (scanDayDailyBar.close <= dailySma20) return StockEvaluationResult(
            matchedItem = null,
            failedConditions = listOf("5) Daily close <= Daily SMA(20)"),
            lastEvaluated15mCandle = evaluated15mCandle,
            lastEvaluated1hCandle = evaluated1hCandle,
            lastEvaluatedDailyCandle = evaluatedDailyCandle,
            hourSma10 = hourSma10,
            dailySma10 = dailySma10,
            dailySma20 = dailySma20,
            previousHourCandle = toOhlcvCandle(previousHourBar),
            previousDayCandle = toOhlcvCandle(previousDailyBar),
        )

        // ── All conditions passed ──────────────────────────────────────────────
        return StockEvaluationResult(
            matchedItem = WatchListItem(
                symbol = symbol,
                symbolToken = symbolToken,
                exchange = exchange,
                lastFifteenMinuteCandle = evaluated15mCandle,
                lastOneHourCandle = evaluated1hCandle,
                lastDailyCandle = evaluatedDailyCandle,
            ),
            failedConditions = emptyList(),
            lastEvaluated15mCandle = evaluated15mCandle,
            lastEvaluated1hCandle = evaluated1hCandle,
            lastEvaluatedDailyCandle = evaluatedDailyCandle,
            hourSma10 = hourSma10,
            dailySma10 = dailySma10,
            dailySma20 = dailySma20,
            previousHourCandle = toOhlcvCandle(previousHourBar),
            previousDayCandle = toOhlcvCandle(previousDailyBar),
        )
    }

    private fun buildDailyBars(candles: List<TimedCandle>): List<DailyBar> {
        return candles
            .groupBy { it.dateTime.toLocalDate() }
            .toSortedMap()
            .mapNotNull { (date, rows) ->
                val slotMap = rows
                    .filter {
                        val time = it.dateTime.toLocalTime()
                        !time.isBefore(marketOpenTime) && !time.isAfter(marketCloseTime)
                    }
                    .associateBy { it.dateTime.toLocalTime() }

                // Daily candle must be constructed from the exact 09:15..15:15 session.
                if (!fullSession15mSlots.all { slotMap.containsKey(it) }) return@mapNotNull null

                val sorted = fullSession15mSlots.mapNotNull { slotMap[it] }
                val first = sorted.firstOrNull() ?: return@mapNotNull null
                val last = sorted.lastOrNull() ?: return@mapNotNull null
                DailyBar(
                    date = date,
                    open = first.open,
                    high = sorted.maxOf { it.high },
                    low = sorted.minOf { it.low },
                    close = last.close,
                    volume = sorted.sumOf { it.volume },
                )
            }
    }

    private fun buildHourlyBars(candles: List<TimedCandle>): List<AggregatedBar> {
        val output = mutableListOf<AggregatedBar>()

        val dayGrouped = candles
            .filter {
                val time = it.dateTime.toLocalTime()
                !time.isBefore(marketOpenTime) && !time.isAfter(marketCloseTime)
            }
            .groupBy { it.dateTime.toLocalDate() }
            .toSortedMap()

        dayGrouped.forEach { (date, rows) ->
            val slotMap = rows.associateBy { it.dateTime.toLocalTime() }

            hourlyBucketStartTimes.forEach { bucketStart ->
                val expectedSlots = if (bucketStart == marketCloseTime) {
                    listOf(bucketStart)
                } else {
                    listOf(
                        bucketStart,
                        bucketStart.plusMinutes(15),
                        bucketStart.plusMinutes(30),
                        bucketStart.plusMinutes(45),
                    )
                }

                if (!expectedSlots.all { slotMap.containsKey(it) }) return@forEach

                val sorted = expectedSlots.mapNotNull { slotMap[it] }
                val first = sorted.firstOrNull() ?: return@forEach
                val last = sorted.lastOrNull() ?: return@forEach
                output.add(
                    AggregatedBar(
                        dateTime = ZonedDateTime.of(date, bucketStart, istZone),
                        open = first.open,
                        high = sorted.maxOf { it.high },
                        low = sorted.minOf { it.low },
                        close = last.close,
                        volume = sorted.sumOf { it.volume },
                    )
                )
            }
        }

        return output.sortedBy { it.dateTime.toInstant().toEpochMilli() }
    }

    private fun toTimedCandle(candle: CandleDataPoint): TimedCandle? {
        val parsed = parseTimestamp(candle.timestamp) ?: return null
        return TimedCandle(
            dateTime = parsed,
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
        )
    }

    private fun toOhlcvCandle(bar: AggregatedBar): OhlcvCandle {
        return OhlcvCandle(
            timestamp = bar.dateTime.toString(),
            open = bar.open,
            high = bar.high,
            low = bar.low,
            close = bar.close,
            volume = bar.volume,
        )
    }

    private fun toOhlcvCandle(candle: TimedCandle): OhlcvCandle {
        return OhlcvCandle(
            timestamp = candle.dateTime.toString(),
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
        )
    }

    private fun toOhlcvCandle(dailyBar: DailyBar): OhlcvCandle {
        return OhlcvCandle(
            timestamp = dailyBar.date.toString(),
            open = dailyBar.open,
            high = dailyBar.high,
            low = dailyBar.low,
            close = dailyBar.close,
            volume = dailyBar.volume,
        )
    }

    private fun parseTimestamp(raw: String): ZonedDateTime? {
        val value = raw.trim()

        return runCatching { OffsetDateTime.parse(value).atZoneSameInstant(istZone) }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(value).withZoneSameInstant(istZone) }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    .atZone(istZone)
            }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(istZone)
            }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                    .atZone(istZone)
            }.getOrNull()
    }

    private fun loadEquities(context: Context): List<WatchListCachedInstrumentItem> {
        val file = File(context.filesDir, INSTRUMENTS_CACHE_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val payload = gson.fromJson(file.readText(), WatchListInstrumentsPayload::class.java)
            payload?.equities.orEmpty().filter { it.token.isNotBlank() && it.symbol.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read instruments cache: ${e.message}", e)
            emptyList()
        }
    }

    private fun loadDerivatives(context: Context): List<WatchListDerivativeItem> {
        val file = File(context.filesDir, INSTRUMENTS_CACHE_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val payload = gson.fromJson(file.readText(), WatchListInstrumentsPayload::class.java)
            payload?.derivatives.orEmpty().filter { it.token.isNotBlank() && it.symbol.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read derivatives from instruments cache: ${e.message}", e)
            emptyList()
        }
    }

    // Regex: <stockName><DDMMMYY><strike><CE|PE>
    private val nfoOptionPattern = Regex("^([A-Z0-9]+)(\\d{2}[A-Z]{3}\\d{2})(\\d+)(CE|PE)$")
    private val expiryDateFormatter = java.time.format.DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("ddMMMyy")
        .toFormatter(Locale.ENGLISH)

    private fun normalizeUnderlyingName(raw: String): String =
        raw.uppercase(Locale.getDefault()).replace(Regex("[^A-Z0-9]"), "")

    /**
     * Selects the CE option for [stockSymbol] whose strike is the closest one strictly above
     * [strikeAbove] (the 9:15 candle high), using the monthly expiry of the target month.
     *
     * Target month: if [scanDate].dayOfMonth > 20 → next month, else current month.
     * From all CE options in the target month, picks the latest expiry date (= monthly expiry),
     * then the smallest strike > [strikeAbove].
     */
    private fun selectCeOption(
        stockSymbol: String,
        strikeAbove: Double,
        scanDate: LocalDate,
        derivatives: List<WatchListDerivativeItem>,
    ): WatchListDerivativeItem? {
        val underlying = normalizeUnderlyingName(
            stockSymbol.removeSuffix("-EQ").removeSuffix("-eq")
        )
        val targetMonth = if (scanDate.dayOfMonth > 20) {
            YearMonth.from(scanDate).plusMonths(1)
        } else {
            YearMonth.from(scanDate)
        }

        Log.d(TAG, "selectCeOption: stock=$stockSymbol underlying=$underlying scanDate=$scanDate targetMonth=$targetMonth strikeAbove=$strikeAbove totalDerivatives=${derivatives.size}")

        // Log a handful of raw derivative symbols so we can see the actual format in Logcat
        derivatives.take(3).forEach { Log.d(TAG, "  sample derivative: ${it.symbol}") }

        data class Parsed(val derivative: WatchListDerivativeItem, val expiry: LocalDate, val strike: Double)

        var noRegexMatch = 0; var wrongUnderlying = 0; var notCE = 0
        var badExpiry = 0; var wrongMonth = 0; var badStrike = 0

        val parsed = derivatives.mapNotNull { d ->
            val upper = d.symbol.uppercase(Locale.ENGLISH)
            val match = nfoOptionPattern.matchEntire(upper) ?: run { noRegexMatch++; return@mapNotNull null }
            val parsedUnderlying = normalizeUnderlyingName(match.groupValues[1])
            if (parsedUnderlying != underlying) { wrongUnderlying++; return@mapNotNull null }
            if (match.groupValues[4] != "CE") { notCE++; return@mapNotNull null }
            val expiry = runCatching {
                LocalDate.parse(match.groupValues[2], expiryDateFormatter)
            }.getOrNull() ?: run { badExpiry++; Log.w(TAG, "  expiry parse failed: ${match.groupValues[2]}"); return@mapNotNull null }
            if (YearMonth.from(expiry) != targetMonth) { wrongMonth++; return@mapNotNull null }
            val strike = match.groupValues[3].toDoubleOrNull() ?: run { badStrike++; return@mapNotNull null }
            Parsed(d, expiry, strike)
        }

        Log.d(TAG, "  parse stats: noRegex=$noRegexMatch wrongUnderlying=$wrongUnderlying notCE=$notCE badExpiry=$badExpiry wrongMonth=$wrongMonth badStrike=$badStrike -> parsed=${parsed.size}")

        if (parsed.isEmpty()) {
            // Log a few symbols that matched underlying but failed on month, for diagnosis
            derivatives.filter {
                val m = nfoOptionPattern.matchEntire(it.symbol.uppercase(Locale.ENGLISH))
                m != null && normalizeUnderlyingName(m.groupValues[1]) == underlying && m.groupValues[4] == "CE"
            }.take(5).forEach { d ->
                val m = nfoOptionPattern.matchEntire(d.symbol.uppercase(Locale.ENGLISH))!!
                val expStr = m.groupValues[2]
                val exp = runCatching { LocalDate.parse(expStr, expiryDateFormatter) }.getOrNull()
                Log.d(TAG, "  CE candidate (wrong month?): ${d.symbol} expiryStr=$expStr parsedExpiry=$exp targetMonth=$targetMonth")
            }
            return null
        }

        // Use the latest expiry in the target month (= monthly expiry)
        val latestExpiry = parsed.maxOf { it.expiry }
        Log.d(TAG, "  latestExpiry=$latestExpiry candidatesAboveStrike=${parsed.count { it.expiry == latestExpiry && it.strike > strikeAbove }}")

        return parsed
            .filter { it.expiry == latestExpiry && it.strike > strikeAbove }
            .minByOrNull { it.strike }
            ?.derivative
            .also { result ->
                if (result != null) Log.d(TAG, "  selected: ${result.symbol}")
                else Log.w(TAG, "  no strike above $strikeAbove found in $latestExpiry expiry")
            }
    }

    /**
     * Fetches 15-minute candles for an arbitrarily long date range by splitting it into
     * 99-day chunks (the API hard-caps each request at 100 calendar days).
     * Results are merged, deduplicated by timestamp, and sorted ascending.
     */
    private suspend fun fetchCandlesChunked(
        exchange: String,
        symbolToken: String,
        fromZdt: ZonedDateTime,
        toZdt: ZonedDateTime,
    ): List<CandleDataPoint> {
        val chunkDays = 99L
        val merged = mutableListOf<CandleDataPoint>()
        var chunkStart = fromZdt
        while (!chunkStart.isAfter(toZdt)) {
            val chunkEnd = minOf(chunkStart.plusDays(chunkDays), toZdt)
            val chunk = HistoricalCandleRepository.getCandlesInRange(
                exchange = exchange,
                symbolToken = symbolToken,
                interval = "FIFTEEN_MINUTE",
                fromMillis = chunkStart.toInstant().toEpochMilli(),
                toMillis = chunkEnd.toInstant().toEpochMilli(),
                verbose = true,
            )
            merged.addAll(chunk)
            if (chunkEnd == toZdt) break
            chunkStart = chunkEnd.plusMinutes(1)
        }
        // Deduplicate and sort
        return merged
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
    }

    private fun resolveExchange(exchSeg: String): String {
        return when (exchSeg.uppercase()) {
            "BSE" -> "BSE"
            else -> "NSE"
        }
    }

    suspend fun runBacktest(
        context: Context,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): Unit = withContext(Dispatchers.IO) {
        if (!backtestMutex.tryLock()) return@withContext
        cancelBacktestRequested = false
        _backtestProgress.value = BacktestProgress(isRunning = true, message = "Starting\u2026")
        try {
            ensureInitialized(context)
            val appContext = context.applicationContext
            AppConfig.init(appContext)
            AuthState.init(appContext)

            val equities = loadEquities(appContext)
            if (equities.isEmpty()) return@withContext

            val scanDays = buildList {
                var d = fromDate
                while (!d.isAfter(toDate)) {
                    if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) add(d)
                    d = d.plusDays(1)
                }
            }
            if (scanDays.isEmpty()) return@withContext

            val total = equities.size
            val batchedResults = mutableListOf<BacktestResultEntry>()
            val fetchFrom = fromDate.minusDays(LOOKBACK_DAYS).atStartOfDay(istZone)
            val fetchTo = toDate.atTime(LocalTime.of(9, 29)).atZone(istZone)

            equities.forEachIndexed { index, equity ->
                if (cancelBacktestRequested) return@forEachIndexed
                if (index > 0) delay(API_THROTTLE_DELAY_MS)

                Log.d(TAG, "Backtest: Analysing stock ${equity.symbol}")
                val exchange = resolveExchange(equity.exchSeg)
                val allCandles = fetchCandlesChunked(
                    exchange = exchange,
                    symbolToken = equity.token,
                    fromZdt = fetchFrom,
                    toZdt = fetchTo,
                )

                val symbolClean = equity.symbol.removeSuffix("-EQ")
                if (allCandles.isNotEmpty()) {
                    for (scanDate in scanDays) {
                        if (cancelBacktestRequested) break
                        val sliceCutoffMillis = scanDate.atTime(LocalTime.of(9, 29)).atZone(istZone).toInstant().toEpochMilli()
                        val slicedCandles = allCandles.filter { c ->
                            val ts = parseTimestamp(c.timestamp)
                            ts != null && ts.toInstant().toEpochMilli() <= sliceCutoffMillis
                        }
                        if (slicedCandles.isEmpty()) continue
                        val evaluation = evaluateStock(
                            symbol = equity.symbol,
                            symbolToken = equity.token,
                            exchange = exchange,
                            candles = slicedCandles,
                            scanDate = scanDate,
                        )
                        if (evaluation.failedConditions.isEmpty()) {
                            batchedResults.add(
                                BacktestResultEntry(
                                    candleDateTime = "$scanDate 09:15",
                                    symbol = symbolClean,
                                )
                            )
                        }
                    }
                }

                _backtestProgress.value = BacktestProgress(
                    isRunning = true,
                    scanned = index + 1,
                    total = total,
                    results = batchedResults.toList(),
                    message = "Scanning ${index + 1}/$total\u2026",
                )
            }

            val finalResults = batchedResults.sortedWith(compareBy({ it.candleDateTime }, { it.symbol }))
            val finalMessage = when {
                cancelBacktestRequested -> "Stopped — ${finalResults.size} match(es) found so far"
                finalResults.isEmpty() -> "No matches found"
                else -> {
                    val dayCount = finalResults.map { it.candleDateTime.take(10) }.distinct().size
                    "${finalResults.size} match(es) across $dayCount day(s)"
                }
            }
            _backtestProgress.value = BacktestProgress(
                isRunning = false,
                scanned = total,
                total = total,
                results = finalResults,
                message = finalMessage,
            )
        } finally {
            _backtestProgress.value = _backtestProgress.value.copy(isRunning = false)
            saveBacktestResultsToDisk()
            backtestMutex.unlock()
        }
    }

    private fun ensureInitialized(context: Context) {
        if (isInitialized) return
        watchListFile = File(context.applicationContext.filesDir, WATCHLIST_CACHE_FILE)
        backtestResultsFile = File(context.applicationContext.filesDir, BACKTEST_RESULTS_FILE)
        loadBacktestResultsFromDisk()
        isInitialized = true
    }

    private fun loadBacktestResultsFromDisk() {
        if (!backtestResultsFile.exists()) return
        try {
            val saved = gson.fromJson(backtestResultsFile.readText(), BacktestProgress::class.java)
            if (saved != null && saved.results.isNotEmpty()) {
                _backtestProgress.value = saved.copy(isRunning = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read backtest results: ${e.message}", e)
        }
    }

    private fun saveBacktestResultsToDisk() {
        try {
            backtestResultsFile.writeText(gson.toJson(_backtestProgress.value.copy(isRunning = false)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save backtest results: ${e.message}", e)
        }
    }

    private fun loadFromDisk() {
        if (!watchListFile.exists()) {
            _snapshot.value = WatchListSnapshot()
            return
        }

        _snapshot.value = try {
            gson.fromJson(watchListFile.readText(), WatchListSnapshot::class.java) ?: WatchListSnapshot()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read watchlist cache: ${e.message}", e)
            WatchListSnapshot(message = "Failed to read cached watchlist")
        }
    }

    private fun saveToDisk(snapshot: WatchListSnapshot) {
        try {
            watchListFile.writeText(gson.toJson(snapshot))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save watchlist cache: ${e.message}", e)
        }
        _snapshot.value = snapshot
    }
}
