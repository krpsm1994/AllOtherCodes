package com.example.angelonestrategyexecutor.data.repository

import android.os.SystemClock
import android.util.Log
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.example.angelonestrategyexecutor.data.model.CandleDataPoint
import com.example.angelonestrategyexecutor.data.model.CandleDataRequest
import com.example.angelonestrategyexecutor.data.network.AngelOneApiClient
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object HistoricalCandleRepository {

    private const val TAG = "HistoricalRepo"
    private const val CACHE_TTL_MS = 45_000L
    private const val LOOKBACK_DAYS = 50L
    private const val MAX_PER_SECOND = 3
    private const val MAX_PER_MINUTE = 180
    private const val MAX_PER_HOUR = 5000

    private data class CacheKey(
        val exchange: String,
        val symbolToken: String,
        val interval: String,
    )

    private data class CacheEntry(
        val fetchedAtMillis: Long,
        val candles: List<CandleDataPoint>,
    )

    private val candleCache = mutableMapOf<CacheKey, CacheEntry>()
    private val rateLimiter = HistoricalApiRateLimiter()

    suspend fun getRecentCandles(
        exchange: String,
        symbolToken: String,
        interval: String = "FIFTEEN_MINUTE",
        maxCandles: Int = 40,
    ): List<CandleDataPoint> {
        val key = CacheKey(exchange = exchange, symbolToken = symbolToken, interval = interval)
        val now = System.currentTimeMillis()

        val cached = candleCache[key]
        if (cached != null && now - cached.fetchedAtMillis <= CACHE_TTL_MS) {
            return cached.candles.takeLast(maxCandles)
        }

        val creds = AuthState.credentials.value
        if (creds == null || creds.isExpired) {
            Log.w(TAG, "Skipping historical call for token=$symbolToken: session not available")
            return cached?.candles?.takeLast(maxCandles) ?: emptyList()
        }

        val request = CandleDataRequest(
            exchange = exchange,
            symbolToken = symbolToken,
            interval = interval,
            fromDate = formatApiDate(now - LOOKBACK_DAYS * 24L * 60L * 60L * 1000L),
            toDate = formatApiDate(now),
        )

        return try {
            rateLimiter.acquirePermit()
            logHistoricalRequest(request)
            val response = AngelOneApiClient.historicalService.getCandleData(
                authorization = "Bearer ${creds.jwtToken}",
                apiKey = creds.apiKey,
                userType = "USER",
                sourceId = "WEB",
                clientLocalIp = "192.168.1.1",
                clientPublicIp = "192.168.1.1",
                macAddress = "AA:BB:CC:DD:EE:FF",
                request = request,
            )

            if (!response.status || response.data.isNullOrEmpty()) {
                Log.w(TAG, "Historical API returned empty data for request=$request")
                return cached?.candles?.takeLast(maxCandles) ?: emptyList()
            }

            val candles = response.data
                .mapNotNull { parseCandleRow(it) }
                .sortedBy { it.timestamp }

            candleCache[key] = CacheEntry(fetchedAtMillis = now, candles = candles)
            candles.takeLast(maxCandles)
        } catch (e: Exception) {
            Log.e(TAG, "Historical API call failed for request=$request", e)
            cached?.candles?.takeLast(maxCandles) ?: emptyList()
        }
    }

    suspend fun getCandlesInRange(
        exchange: String,
        symbolToken: String,
        interval: String = "FIFTEEN_MINUTE",
        fromMillis: Long,
        toMillis: Long,
        verbose: Boolean = true,
    ): List<CandleDataPoint> {
        val creds = AuthState.credentials.value
        if (creds == null || creds.isExpired) {
            Log.w(TAG, "Skipping historical range call for token=$symbolToken: session not available")
            return emptyList()
        }

        val request = CandleDataRequest(
            exchange = exchange,
            symbolToken = symbolToken,
            interval = interval,
            fromDate = formatApiDate(fromMillis),
            toDate = formatApiDate(toMillis),
        )

        val maxAttempts = 3
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                rateLimiter.acquirePermit()
                if (verbose) {
                    if (attempt == 1) logHistoricalRequest(request)
                    else Log.d(TAG, "Historical API retry attempt $attempt for request=$request")
                }
                delay(500L) // Coroutine-safe extra delay to stay within server-side rate limits
                val response = AngelOneApiClient.historicalService.getCandleData(
                    authorization = "Bearer ${creds.jwtToken}",
                    apiKey = creds.apiKey,
                    userType = "USER",
                    sourceId = "WEB",
                    clientLocalIp = "192.168.1.1",
                    clientPublicIp = "192.168.1.1",
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    request = request,
                )

                if (!response.status || response.data.isNullOrEmpty()) {
                    val backoffMs = attempt * 2_000L
                    Log.w(TAG, "Historical API returned empty data (attempt $attempt/$maxAttempts) for request=$request — backing off ${backoffMs}ms")
                    if (attempt < maxAttempts) {
                        delay(backoffMs)
                        continue
                    }
                    return emptyList()
                }

                return response.data
                    .mapNotNull { parseCandleRow(it) }
                    .sortedBy { it.timestamp }
            } catch (e: Exception) {
                lastException = e
                val backoffMs = attempt * 2_000L
                Log.e(TAG, "Historical API call failed (attempt $attempt/$maxAttempts) for request=$request — backing off ${backoffMs}ms", e)
                if (attempt < maxAttempts) {
                    delay(backoffMs)
                }
            }
        }
        Log.e(TAG, "Historical API exhausted $maxAttempts attempts for request=$request", lastException)
        return emptyList()
    }

    private fun logHistoricalRequest(request: CandleDataRequest) {
        Log.d(TAG, "Historical API request: $request")
    }

    private fun parseCandleRow(row: List<com.google.gson.JsonElement>): CandleDataPoint? {
        if (row.size < 6) return null
        return try {
            CandleDataPoint(
                timestamp = row[0].asString,
                open = row[1].asDouble,
                high = row[2].asDouble,
                low = row[3].asDouble,
                close = row[4].asDouble,
                volume = row[5].asDouble,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun formatApiDate(timeMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
    }

    private class HistoricalApiRateLimiter {
        private val mutex = Mutex()
        private val perSecondWindow = ArrayDeque<Long>()
        private val perMinuteWindow = ArrayDeque<Long>()
        private val perHourWindow = ArrayDeque<Long>()

        suspend fun acquirePermit() {
            while (true) {
                val waitMs = mutex.withLock {
                    val now = SystemClock.elapsedRealtime()
                    pruneOld(now)

                    val waitForSecond = computeWaitMs(
                        window = perSecondWindow,
                        limit = MAX_PER_SECOND,
                        windowMs = 1_000L,
                        now = now,
                    )
                    val waitForMinute = computeWaitMs(
                        window = perMinuteWindow,
                        limit = MAX_PER_MINUTE,
                        windowMs = 60_000L,
                        now = now,
                    )
                    val waitForHour = computeWaitMs(
                        window = perHourWindow,
                        limit = MAX_PER_HOUR,
                        windowMs = 3_600_000L,
                        now = now,
                    )

                    val requiredWait = maxOf(waitForSecond, waitForMinute, waitForHour)
                    if (requiredWait <= 0L) {
                        perSecondWindow.addLast(now)
                        perMinuteWindow.addLast(now)
                        perHourWindow.addLast(now)
                        0L
                    } else {
                        requiredWait
                    }
                }

                if (waitMs <= 0L) return
                delay(waitMs)
            }
        }

        private fun pruneOld(now: Long) {
            pruneWindow(perSecondWindow, now, 1_000L)
            pruneWindow(perMinuteWindow, now, 60_000L)
            pruneWindow(perHourWindow, now, 3_600_000L)
        }

        private fun pruneWindow(window: ArrayDeque<Long>, now: Long, windowMs: Long) {
            val expiry = now - windowMs
            while (window.isNotEmpty() && window.first() <= expiry) {
                window.removeFirst()
            }
        }

        private fun computeWaitMs(
            window: ArrayDeque<Long>,
            limit: Int,
            windowMs: Long,
            now: Long,
        ): Long {
            if (window.size < limit) return 0L
            val oldest = window.first()
            val waitUntil = oldest + windowMs + 1L
            return (waitUntil - now).coerceAtLeast(1L)
        }
    }
}
