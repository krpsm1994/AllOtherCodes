package com.example.angelonestrategyexecutor.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.example.angelonestrategyexecutor.data.config.AppConfig
import com.example.angelonestrategyexecutor.data.repository.OrderRepository
import com.example.angelonestrategyexecutor.data.repository.StockHistoryRepository
import com.example.angelonestrategyexecutor.data.websocket.LtpRepository
import com.example.angelonestrategyexecutor.data.websocket.OrderStatusRepository
import com.example.angelonestrategyexecutor.data.websocket.OrderWebSocketService
import com.example.angelonestrategyexecutor.service.LtpForegroundService
import com.example.angelonestrategyexecutor.utils.OrderNotificationHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocksScreen(contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val gson = remember { Gson() }
    val stocksFile = remember { File(context.filesDir, "stocks_list.json") }
    
    var showAddStock by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var stockList by remember { mutableStateOf(listOf<StockEntry>()) }
    var editingStockIndex by rememberSaveable { mutableIntStateOf(-1) } // -1 = not editing
    var deleteStockIndex by rememberSaveable { mutableIntStateOf(-1) } // -1 = no delete pending
    var expandedClosedDates by remember { mutableStateOf(setOf<String>()) }
    var previousStatusByItemKey by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    // Observe live LTP data from the WebSocket repository
    val ltpMap by LtpRepository.ltpMap.collectAsStateWithLifecycle()
    val wsState by LtpRepository.connectionState.collectAsStateWithLifecycle()

    // Observe auth state reactively (plain property AuthState.isLoggedIn is not Compose-observable)
    val authCredentials by AuthState.credentials.collectAsStateWithLifecycle()

    // Observe order status updates from Order WebSocket
    val orderUpdatesMap by OrderStatusRepository.orderUpdates.collectAsStateWithLifecycle()

    // Observe config toggles
    val placeOrdersEnabled by AppConfig.placeOrders.collectAsStateWithLifecycle()
    val configNumLots by AppConfig.numLots.collectAsStateWithLifecycle()
    val configProductType by AppConfig.productType.collectAsStateWithLifecycle()

    // Load stock list on first composition
    LaunchedEffect(Unit) {
        try {
            if (stocksFile.exists()) {
                val json = stocksFile.readText()
                val type = object : TypeToken<List<StockEntry>>() {}.type
                val loaded: List<StockEntry> = gson.fromJson(json, type) ?: emptyList()
                // Migrate: grouped statuses without a date get today's date
                val today = currentTradeDate()
                stockList = loaded.map { stock ->
                    if (isGroupedStatus(stock.orderStatus) && stock.closedDate == null)
                        stock.copy(closedDate = today)
                    else stock
                }
                android.util.Log.d("StocksScreen", "Loaded ${stockList.size} stocks from cache")
            }
        } catch (e: Exception) {
            android.util.Log.e("StocksScreen", "Failed to load stocks: ${e.message}")
        }
    }

    // Save stock list whenever it changes
    LaunchedEffect(stockList) {
        if (stockList.isNotEmpty()) {
            try {
                stocksFile.writeText(gson.toJson(stockList))
                android.util.Log.d("StocksScreen", "Saved ${stockList.size} stocks to cache")
            } catch (e: Exception) {
                android.util.Log.e("StocksScreen", "Failed to save stocks: ${e.message}")
            }
        }
    }

    // Keep foreground WebSocket service aligned with streamable stocks.
    LaunchedEffect(stockList, authCredentials) {
        val creds = authCredentials
        if (creds == null || creds.isExpired) {
            LtpForegroundService.stop(context)
            return@LaunchedEffect
        }

        val hasStreamableStocks = stockList.any(::shouldStreamStock)
        if (hasStreamableStocks) {
            LtpForegroundService.start(context)
        } else {
            LtpForegroundService.stop(context)
        }
    }

    // ── Auto-expire: mark Watching stocks as Not Triggered after 12:00 PM ──
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val minute = now.get(Calendar.MINUTE)
            val isPastNoon = hour > 12 || (hour == 12 && minute >= 0)

            if (isPastNoon) {
                val expiredStocks = stockList.filter { it.orderStatus == "Watching" || it.orderStatus == null }
                if (expiredStocks.isNotEmpty()) {
                    val today = currentTradeDate()
                    stockList = stockList.toMutableList().also { list ->
                        list.forEachIndexed { index, stock ->
                            if (stock.orderStatus == "Watching" || stock.orderStatus == null) {
                                list[index] = stock.copy(
                                    orderStatus = "Not Triggered",
                                    closedDate = stock.closedDate ?: today,
                                )
                                // Unsubscribe tokens from WebSocket
                                val symExch = stock.symbolExchangeType ?: 1
                                val optExch = stock.optionExchangeType ?: 2
                                if (stock.symbolToken.isNotBlank()) {
                                    LtpRepository.unsubscribeByExchange(symExch, listOf(stock.symbolToken))
                                }
                                if (stock.optionToken.isNotBlank()) {
                                    LtpRepository.unsubscribeByExchange(optExch, listOf(stock.optionToken))
                                }
                                LtpRepository.removeTokens(
                                    listOfNotNull(
                                        stock.symbolToken.takeIf { it.isNotBlank() },
                                        stock.optionToken.takeIf { it.isNotBlank() },
                                    )
                                )
                                StockHistoryRepository.log(
                                    stock.symbol, "Not Triggered",
                                    "Order not triggered before 12:00 PM — unsubscribed"
                                )
                                OrderNotificationHelper.notify(
                                    context, stock.symbol,
                                    "Not Triggered",
                                    "Order not triggered before 12:00 PM"
                                )
                                android.util.Log.d("StocksScreen", "Auto-expired ${stock.symbol}: Not Triggered after 12:00 PM")
                            }
                        }
                    }
                }
            }

            delay(60_000L) // check every minute
        }
    }

    // ── Entry trigger by watch type (Buy / Short) ─────────────────────────
    // Track which tokens already have an in-flight order to prevent duplicates
    val orderingTokens = remember { mutableSetOf<String>() }

    LaunchedEffect(ltpMap, stockList) {
        for ((index, stock) in stockList.withIndex()) {
            val status = stock.orderStatus ?: "Watching"
            val watchType = normalizedWatchType(stock.watchType)

            // Only trigger for stocks in "Watching" state that have an option configured
            if (status != "Watching") continue
            if (stock.optionToken.isBlank()) continue

            val stockLtp = ltpMap[stock.symbolToken] ?: continue // no LTP yet
            val shouldEnter = if (watchType == "Short") {
                shouldExecuteShortWatchEntry(stockLtp = stockLtp, stock = stock)
            } else {
                shouldExecuteBuyWatchEntry(stockLtp = stockLtp, stock = stock)
            }
            if (!shouldEnter) continue

            // Prevent duplicate in-flight orders
            if (stock.symbolToken in orderingTokens) continue
            orderingTokens.add(stock.symbolToken)

            val optionLtp = ltpMap[stock.optionToken] ?: 0.0
            val entryTriggerText = if (watchType == "Short") {
                "crossed below candleLow=${stock.candleLow}"
            } else {
                "crossed above candleHigh=${stock.candleHigh}"
            }

            if (placeOrdersEnabled) {
                // ── LIVE: Place real order ───────────────────────────────
                val isMarketOrder = optionLtp <= 0.0

                android.util.Log.d(
                    "StocksScreen",
                    "$watchType TRIGGER: ${stock.symbol} LTP=$stockLtp $entryTriggerText. " +
                        "Placing ${if (isMarketOrder) "MARKET" else "LIMIT"} BUY for ${stock.option}" +
                        if (isMarketOrder) "" else " @ $optionLtp"
                )

                launch {
                    val response = OrderRepository.placeBuyOrder(
                        tradingSymbol = stock.option,
                        symbolToken = stock.optionToken,
                        quantity = stock.optionLotSize * configNumLots,
                        limitPrice = optionLtp,
                        isMarketOrder = isMarketOrder,
                        exchange = stock.optionExchSeg ?: "NFO",
                        productType = configProductType,
                    )

                    orderingTokens.remove(stock.symbolToken)

                    if (response != null && response.status) {
                        stockList = stockList.toMutableList().also { list ->
                            list[index] = stock.copy(
                                orderStatus = "Triggered",
                                orderId = response.data?.orderId,
                                uniqueOrderId = response.data?.uniqueOrderId,
                                orderPrice = optionLtp,
                                buyPrice = optionLtp,
                                orderQuantity = stock.optionLotSize * configNumLots,
                            )
                        }
                        StockHistoryRepository.log(
                            stock.symbol, "Buy Triggered",
                            "WatchType: $watchType, Option: ${stock.option}, Price: $optionLtp, Qty: ${stock.optionLotSize * configNumLots}, OrderId: ${response.data?.orderId}"
                        )
                        OrderNotificationHelper.notify(
                            context, stock.symbol,
                            "Buy Triggered ($watchType)",
                            "Option: ${stock.option} | Price: ₹$optionLtp | Qty: ${stock.optionLotSize * configNumLots}"
                        )
                        android.util.Log.d(
                            "StocksScreen",
                            "$watchType entry order placed: orderId=${response.data?.orderId} for ${stock.option}"
                        )
                        OrderStatusRepository.connect()
                    } else {
                        android.util.Log.e(
                            "StocksScreen",
                            "$watchType entry order FAILED for ${stock.option}: ${response?.message ?: "no response"}"
                        )
                    }
                }
            } else {
                // ── PAPER TRADE: Simulate buy order ─────────────────────
                val simBuyPrice = if (optionLtp > 0) optionLtp else 0.0
                if (simBuyPrice <= 0.0) {
                    // No option LTP yet — skip and retry on next tick
                    android.util.Log.d(
                        "PaperTrade",
                        "SKIPPED BUY ($watchType): ${stock.symbol} LTP=$stockLtp $entryTriggerText, " +
                            "but option ${stock.option} LTP not available yet"
                    )
                    orderingTokens.remove(stock.symbolToken)
                    continue
                }
                android.util.Log.d(
                    "PaperTrade",
                    "SIMULATED BUY ($watchType): ${stock.symbol} LTP=$stockLtp $entryTriggerText. " +
                        "Option ${stock.option} buyPrice=$simBuyPrice"
                )
                orderingTokens.remove(stock.symbolToken)
                stockList = stockList.toMutableList().also { list ->
                    list[index] = stock.copy(
                        orderStatus = "Open",
                        orderId = "PAPER-${System.currentTimeMillis()}",
                        orderPrice = simBuyPrice,
                        buyPrice = simBuyPrice,
                        orderQuantity = stock.optionLotSize * configNumLots,
                        filledShares = (stock.optionLotSize * configNumLots).toString(),
                    )
                }
                StockHistoryRepository.log(
                    stock.symbol, "Paper Buy",
                    "WatchType: $watchType, Option: ${stock.option}, BuyPrice: $simBuyPrice, StockLTP: $stockLtp"
                )
                OrderNotificationHelper.notify(
                    context, stock.symbol,
                    "Paper Buy — Open ($watchType)",
                    "Option: ${stock.option} | BuyPrice: ₹$simBuyPrice"
                )
            }
        }
    }

    // ── Exit trigger by watch type (Buy / Short) ──────────────────────────
    val sellingTokens = remember { mutableSetOf<String>() }

    LaunchedEffect(ltpMap, stockList) {
        for ((index, stock) in stockList.withIndex()) {
            val status = stock.orderStatus ?: "Watching"
            val watchType = normalizedWatchType(stock.watchType)
            if (stock.optionToken.isBlank()) continue
            if (stock.sellOrderId != null) continue   // sell already placed

            val stockLtp = ltpMap[stock.symbolToken] ?: continue
            val effectiveTarget = stock.target
            val effectiveStopLoss = if (watchType == "Short") stock.candleHigh else stock.candleLow

            android.util.Log.d(
                "SellTrigger",
                "${stock.symbol}: watchType=$watchType, status=$status, stockLtp=$stockLtp, " +
                    "target=$effectiveTarget, stopLoss=$effectiveStopLoss, " +
                    "sellOrderId=${stock.sellOrderId}"
            )

            if (status != "Open") continue

            // Determine exit reason
            val exitReason = if (watchType == "Short") {
                resolveShortWatchExitReason(stockLtp = stockLtp, stock = stock)
            } else {
                resolveBuyWatchExitReason(stockLtp = stockLtp, stock = stock)
            }
            if (exitReason == null) continue

            // Prevent duplicate in-flight sell orders
            if (stock.symbolToken in sellingTokens) continue
            sellingTokens.add(stock.symbolToken)

            val optionLtp = ltpMap[stock.optionToken] ?: 0.0

            if (placeOrdersEnabled) {
                // ── LIVE: Place real sell order ──────────────────────────
                val isMarketOrder = optionLtp <= 0.0
                val sellQty = stock.filledShares?.toIntOrNull() ?: stock.optionLotSize

                android.util.Log.d(
                    "StocksScreen",
                    "$watchType SELL TRIGGER ($exitReason): ${stock.symbol} LTP=$stockLtp. " +
                        "Placing ${if (isMarketOrder) "MARKET" else "LIMIT"} SELL for ${stock.option}" +
                        if (isMarketOrder) "" else " @ $optionLtp"
                )

                launch {
                    val response = OrderRepository.placeSellOrder(
                        tradingSymbol = stock.option,
                        symbolToken = stock.optionToken,
                        quantity = sellQty,
                        limitPrice = optionLtp,
                        isMarketOrder = isMarketOrder,
                        exchange = stock.optionExchSeg ?: "NFO",
                        productType = configProductType,
                    )

                    sellingTokens.remove(stock.symbolToken)

                    if (response != null && response.status) {
                        stockList = stockList.toMutableList().also { list ->
                            list[index] = stock.copy(
                                sellOrderId = response.data?.orderId,
                                sellUniqueOrderId = response.data?.uniqueOrderId,
                                sellPrice = optionLtp,
                                sellOrderStatus = "Triggered",
                                exitReason = exitReason,
                            )
                        }
                        StockHistoryRepository.log(
                            stock.symbol, "Sell Triggered",
                            "WatchType: $watchType, Reason: $exitReason, Option: ${stock.option}, Price: $optionLtp, OrderId: ${response.data?.orderId}"
                        )
                        OrderNotificationHelper.notify(
                            context, stock.symbol,
                            "Sell Triggered ($exitReason, $watchType)",
                            "Option: ${stock.option} | Price: ₹$optionLtp"
                        )
                        android.util.Log.d(
                            "StocksScreen",
                            "$watchType sell order placed ($exitReason): orderId=${response.data?.orderId} for ${stock.option}"
                        )
                    } else {
                        android.util.Log.e(
                            "StocksScreen",
                            "$watchType sell order FAILED for ${stock.option}: ${response?.message ?: "no response"}"
                        )
                    }
                }
            } else {
                // ── PAPER TRADE: Simulate sell order ────────────────────
                val simSellPrice = if (optionLtp > 0) optionLtp else 0.0
                if (simSellPrice <= 0.0) {
                    // No option LTP yet — skip and retry on next tick
                    android.util.Log.d(
                        "PaperTrade",
                        "SKIPPED SELL ($exitReason, $watchType): ${stock.symbol} LTP=$stockLtp, " +
                            "but option ${stock.option} LTP not available yet"
                    )
                    sellingTokens.remove(stock.symbolToken)
                    continue
                }
                android.util.Log.d(
                    "PaperTrade",
                    "SIMULATED SELL ($exitReason, $watchType): ${stock.symbol} LTP=$stockLtp. " +
                        "Option ${stock.option} sellPrice=$simSellPrice"
                )
                sellingTokens.remove(stock.symbolToken)
                stockList = stockList.toMutableList().also { list ->
                    list[index] = stock.copy(
                        orderStatus = "Closed",
                        sellOrderId = "PAPER-SELL-${System.currentTimeMillis()}",
                        sellPrice = simSellPrice,
                        sellOrderStatus = "complete",
                        sellFilledShares = (stock.filledShares?.toIntOrNull() ?: stock.optionLotSize).toString(),
                        exitReason = exitReason,
                        closedDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
                    )
                }
                StockHistoryRepository.log(
                    stock.symbol, "Paper Sell",
                    "WatchType: $watchType, Reason: $exitReason, Option: ${stock.option}, SellPrice: $simSellPrice, StockLTP: $stockLtp"
                )
                OrderNotificationHelper.notify(
                    context, stock.symbol,
                    "Paper Sell — Closed ($exitReason, $watchType)",
                    "Option: ${stock.option} | SellPrice: ₹$simSellPrice"
                )
                // Unsubscribe stock and option tokens from WebSocket when order is Closed
                val symExchPaper = stock.symbolExchangeType ?: 1
                val optExchPaper = stock.optionExchangeType ?: 2
                if (stock.symbolToken.isNotBlank()) {
                    LtpRepository.unsubscribeByExchange(symExchPaper, listOf(stock.symbolToken))
                }
                if (stock.optionToken.isNotBlank()) {
                    LtpRepository.unsubscribeByExchange(optExchPaper, listOf(stock.optionToken))
                }
                LtpRepository.removeTokens(
                    listOfNotNull(
                        stock.symbolToken.takeIf { it.isNotBlank() },
                        stock.optionToken.takeIf { it.isNotBlank() },
                    )
                )
                android.util.Log.d("StocksScreen", "Unsubscribed tokens for ${stock.symbol} after Closed (Paper Trade)")
            }
        }
    }

    // ── Order WebSocket: apply status updates to stock list ────────────────
    // Connect/disconnect order WS based on whether there are in-flight orders to track.
    LaunchedEffect(stockList, orderUpdatesMap) {
        val hasOrdersToTrack = stockList.any { stock ->
            shouldTrackOrderOnSocket(stock = stock, orderUpdatesMap = orderUpdatesMap)
        }
        if (hasOrdersToTrack && !OrderStatusRepository.isConnected) {
            OrderStatusRepository.connect()
        } else if (!hasOrdersToTrack && OrderStatusRepository.isConnected) {
            OrderStatusRepository.disconnect()
        }
    }

    // Also keep token/state cleanup in sync when stock list changes.
    LaunchedEffect(stockList) {
        val activeTokensByExchange = collectTokensByExchange(stockList) { stock ->
            !isGroupedStatus(stock.orderStatus)
        }
        val groupedTokensByExchange = collectTokensByExchange(stockList) { stock ->
            isGroupedStatus(stock.orderStatus)
        }

        val tokensToUnsubscribeByExchange = groupedTokensByExchange
            .mapValues { (exchange, groupedTokens) ->
                groupedTokens - (activeTokensByExchange[exchange] ?: emptySet())
            }
            .filterValues { it.isNotEmpty() }

        if (tokensToUnsubscribeByExchange.isNotEmpty()) {
            tokensToUnsubscribeByExchange.forEach { (exchange, tokens) ->
                LtpRepository.unsubscribeByExchange(exchange, tokens.toList())
            }
            LtpRepository.removeTokens(tokensToUnsubscribeByExchange.values.flatten().distinct())
            android.util.Log.d(
                "StocksScreen",
                "Token cleanup for grouped orders: ${tokensToUnsubscribeByExchange.values.sumOf { it.size }} unsubscribed"
            )
        }

        val currentStatusByItemKey = stockList.associate { stockItemKey(it) to it.orderStatus }

        if (previousStatusByItemKey.isNotEmpty()) {
            val datesToExpand = stockList.mapNotNull { stock ->
                val itemKey = stockItemKey(stock)
                val previousStatus = previousStatusByItemKey[itemKey]
                val isNowGrouped = isGroupedStatus(stock.orderStatus)
                val wasGrouped = isGroupedStatus(previousStatus)
                if (isNowGrouped && !wasGrouped) {
                    stock.closedDate ?: currentTradeDate()
                } else {
                    null
                }
            }.toSet()

            if (datesToExpand.isNotEmpty()) {
                expandedClosedDates = expandedClosedDates + datesToExpand
            }
        }

        previousStatusByItemKey = currentStatusByItemKey
    }

    LaunchedEffect(orderUpdatesMap) {
        if (orderUpdatesMap.isEmpty()) return@LaunchedEffect

        android.util.Log.d("OrderWS-Apply", "orderUpdatesMap changed, size=${orderUpdatesMap.size}, keys=${orderUpdatesMap.keys}")

        var changed = false
        val mutableList = stockList.toMutableList()

        for ((index, stock) in mutableList.withIndex()) {
            // ── Handle BUY order updates ─────────────
            val buyOid = stock.orderId
            if (buyOid != null) {
                val update = orderUpdatesMap[buyOid]
                android.util.Log.d("OrderWS-Apply", "${stock.symbol} BUY orderId=$buyOid, matchFound=${update != null}")
                if (update != null) {
                    val newStatus = when (update.orderStatusCode) {
                        "AB01", "AB05", "AB09", "AB10" -> "Open"
                        "AB02", "AB07"         -> "cancelled"
                        "AB03"                 -> "rejected"
                        "AB04", "AB08", "AB11" -> stock.orderStatus ?: "Triggered"
                        else                   -> stock.orderStatus ?: "Triggered"
                    }
                    val avgPrice = if (update.averagePrice > 0) update.averagePrice else stock.buyPrice

                    if (newStatus != stock.orderStatus ||
                        avgPrice != stock.buyPrice ||
                        update.filledShares != stock.filledShares ||
                        update.text != stock.orderText
                    ) {
                        mutableList[index] = mutableList[index].copy(
                            orderStatus = newStatus,
                            buyPrice = avgPrice,
                            filledShares = update.filledShares,
                            orderText = update.text,
                        )
                        changed = true
                        if (newStatus != stock.orderStatus) {
                            StockHistoryRepository.log(
                                stock.symbol, if (newStatus == "Open") "Buy Filled" else "Status Changed",
                                "Status: ${stock.orderStatus} → $newStatus, AvgPrice: $avgPrice, Filled: ${update.filledShares}"
                            )
                            val buyNotifTitle = when (newStatus) {
                                "Open"      -> "Buy Filled"
                                "cancelled" -> "Buy Cancelled"
                                "rejected"  -> "Buy Rejected"
                                else        -> "Buy Status: $newStatus"
                            }
                            val buyNotifMsg = when (newStatus) {
                                "Open"      -> "Option: ${stock.option} | AvgPrice: ₹$avgPrice | Filled: ${update.filledShares}"
                                else        -> "Option: ${stock.option} | Status: $newStatus"
                            }
                            OrderNotificationHelper.notify(context, stock.symbol, buyNotifTitle, buyNotifMsg)
                        }
                        android.util.Log.d(
                            "StocksScreen",
                            "BUY order WS update: ${stock.symbol} → status=$newStatus, " +
                                "avgPrice=$avgPrice, filled=${update.filledShares}"
                        )
                    }
                }
            }

            // ── Handle SELL order updates ─────────────
            val sellOid = mutableList[index].sellOrderId
            android.util.Log.d("OrderWS-Apply", "${mutableList[index].symbol} SELL orderId=$sellOid")
            if (sellOid != null) {
                val update = orderUpdatesMap[sellOid]
                android.util.Log.d("OrderWS-Apply", "${mutableList[index].symbol} SELL matchFound=${update != null}, statusCode=${update?.orderStatusCode}")
                if (update != null) {
                    val sellStatus = when (update.orderStatusCode) {
                        "AB01", "AB09", "AB10" -> "Open"
                        "AB05"                 -> "complete"
                        "AB02", "AB07"         -> "cancelled"
                        "AB03"                 -> "rejected"
                        else                   -> mutableList[index].sellOrderStatus ?: "Triggered"
                    }
                    val sellAvgPrice = if (update.averagePrice > 0) update.averagePrice else mutableList[index].sellPrice
                    val previousSellStatus = mutableList[index].sellOrderStatus

                    if (sellStatus != mutableList[index].sellOrderStatus ||
                        sellAvgPrice != mutableList[index].sellPrice ||
                        update.filledShares != mutableList[index].sellFilledShares ||
                        update.text != mutableList[index].sellOrderText
                    ) {
                        // If sell is complete, mark the overall order status as Closed
                        val overallStatus = if (sellStatus == "complete") "Closed" else mutableList[index].orderStatus
                        mutableList[index] = mutableList[index].copy(
                            orderStatus = overallStatus,
                            sellOrderStatus = sellStatus,
                            sellPrice = sellAvgPrice,
                            sellFilledShares = update.filledShares,
                            sellOrderText = update.text,
                            closedDate = if (overallStatus == "Closed" && mutableList[index].closedDate == null)
                                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            else mutableList[index].closedDate,
                        )
                        changed = true
                        if (sellStatus != previousSellStatus) {
                            StockHistoryRepository.log(
                                stock.symbol, if (sellStatus == "complete") "Sell Filled" else "Status Changed",
                                "SellStatus: $sellStatus, SellPrice: $sellAvgPrice, Filled: ${update.filledShares}, Reason: ${stock.exitReason}"
                            )
                            val sellNotifTitle = when (sellStatus) {
                                "complete"  -> "Order Closed (${stock.exitReason ?: "Sell Filled"})"
                                "cancelled" -> "Sell Cancelled"
                                "rejected"  -> "Sell Rejected"
                                else        -> "Sell Status: $sellStatus"
                            }
                            val sellNotifMsg = when (sellStatus) {
                                "complete"  -> "Option: ${stock.option} | SellPrice: ₹$sellAvgPrice | Filled: ${update.filledShares}"
                                else        -> "Option: ${stock.option} | Status: $sellStatus"
                            }
                            OrderNotificationHelper.notify(context, stock.symbol, sellNotifTitle, sellNotifMsg)
                        }
                        android.util.Log.d(
                            "StocksScreen",
                            "SELL order WS update: ${stock.symbol} → sellStatus=$sellStatus, " +
                                "sellAvgPrice=$sellAvgPrice, sellFilled=${update.filledShares}"
                        )
                        // Unsubscribe stock and option tokens from WebSocket when order is Closed
                        if (overallStatus == "Closed") {
                            val symExchLive = stock.symbolExchangeType ?: 1
                            val optExchLive = stock.optionExchangeType ?: 2
                            if (stock.symbolToken.isNotBlank()) {
                                LtpRepository.unsubscribeByExchange(symExchLive, listOf(stock.symbolToken))
                            }
                            if (stock.optionToken.isNotBlank()) {
                                LtpRepository.unsubscribeByExchange(optExchLive, listOf(stock.optionToken))
                            }
                            LtpRepository.removeTokens(
                                listOfNotNull(
                                    stock.symbolToken.takeIf { it.isNotBlank() },
                                    stock.optionToken.takeIf { it.isNotBlank() },
                                )
                            )
                            android.util.Log.d("StocksScreen", "Unsubscribed tokens for ${stock.symbol} after Closed (Live Order WS)")
                        }
                    }
                }
            }
        }

        if (changed) {
            stockList = mutableList.toList()
        }
    }

    // Delete confirmation dialog
    if (deleteStockIndex >= 0 && deleteStockIndex < stockList.size) {
        val stockToDelete = stockList[deleteStockIndex]
        AlertDialog(
            onDismissRequest = { deleteStockIndex = -1 },
            title = { Text("Delete Stock") },
            text = { Text("Are you sure you want to delete ${stockToDelete.symbol}?") },
            confirmButton = {
                TextButton(onClick = {
                    // Unsubscribe from WebSocket
                    val symExch = stockToDelete.symbolExchangeType ?: 1
                    val optExch = stockToDelete.optionExchangeType ?: 2
                    if (stockToDelete.symbolToken.isNotBlank()) {
                        LtpRepository.unsubscribeByExchange(symExch, listOf(stockToDelete.symbolToken))
                    }
                    if (stockToDelete.optionToken.isNotBlank()) {
                        LtpRepository.unsubscribeByExchange(optExch, listOf(stockToDelete.optionToken))
                    }
                    // Remove deleted tokens from LTP map
                    LtpRepository.removeTokens(
                        listOfNotNull(
                            stockToDelete.symbolToken.takeIf { it.isNotBlank() },
                            stockToDelete.optionToken.takeIf { it.isNotBlank() },
                        )
                    )
                    // Remove from list
                    stockList = stockList.toMutableList().also { it.removeAt(deleteStockIndex) }
                    StockHistoryRepository.log(
                        stockToDelete.symbol, "Deleted",
                        "Option: ${stockToDelete.option}, Status: ${stockToDelete.orderStatus}"
                    )
                    // Clear stocks file if list is now empty
                    if (stockList.isEmpty()) {
                        try { stocksFile.delete() } catch (_: Exception) { }
                    }
                    deleteStockIndex = -1
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteStockIndex = -1 }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showAddStock || editingStockIndex >= 0) {
        val existingStock = if (editingStockIndex >= 0 && editingStockIndex < stockList.size)
            stockList[editingStockIndex] else null
        AddStockScreen(
            onBack = {
                showAddStock = false
                editingStockIndex = -1
            },
            onStockAdded = { stock ->
                if (editingStockIndex >= 0 && editingStockIndex < stockList.size) {
                    // Update existing item
                    val oldStock = stockList[editingStockIndex]
                    stockList = stockList.toMutableList().also { it[editingStockIndex] = stock }
                    StockHistoryRepository.log(
                        stock.symbol, "Edited",
                        buildString {
                            if (oldStock.candleHigh != stock.candleHigh) append("CandleHigh: ${oldStock.candleHigh}→${stock.candleHigh} ")
                            if (oldStock.candleLow != stock.candleLow) append("CandleLow: ${oldStock.candleLow}→${stock.candleLow} ")
                            if (oldStock.target != stock.target) append("Target: ${oldStock.target}→${stock.target} ")
                            if (oldStock.option != stock.option) append("Option: ${oldStock.option}→${stock.option} ")
                            if (oldStock.orderStatus != stock.orderStatus) append("Status: ${oldStock.orderStatus}→${stock.orderStatus} ")
                            if (isEmpty()) append("No field changes")
                        }.trim()
                    )
                } else {
                    // Add new item
                    stockList = stockList + stock
                    StockHistoryRepository.log(
                        stock.symbol, "Added",
                        "Option: ${stock.option}, CandleHigh: ${stock.candleHigh}, CandleLow: ${stock.candleLow}, Target: ${stock.target}"
                    )
                }
                showAddStock = false
                editingStockIndex = -1
            },
            existingStock = existingStock,
        )
        return
    }

    if (showHistory) {
        HistoryScreen(onBack = { showHistory = false })
        return
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Stocks", fontWeight = FontWeight.SemiBold)
                        WsStatusIndicator(wsState)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "History",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddStock = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Stock",
                )
            }
        },
    ) { innerPadding ->
        if (stockList.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Stocks",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Stocks",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tap + to add a stock to watch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            // Stock list with WebSocket status
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val statusOrder = mapOf(
                    "Open" to 0,
                    "Triggered" to 1,
                    "Watching" to 2,
                )

                // Active items (everything except grouped statuses)
                val activeWithIndex = stockList
                    .mapIndexed { index, stock -> index to stock }
                    .filter { (_, stock) -> !isGroupedStatus(stock.orderStatus) }
                    .sortedBy { (_, stock) -> statusOrder[stock.orderStatus ?: "Watching"] ?: 3 }

                // Grouped items (Closed + Not Triggered) by date, newest first
                val groupedWithIndex = stockList
                    .mapIndexed { index, stock -> index to stock }
                    .filter { (_, stock) -> isGroupedStatus(stock.orderStatus) }

                val groupedByDate = groupedWithIndex
                    .groupBy { (_, stock) -> stock.closedDate ?: "Unknown" }
                    .toSortedMap(compareByDescending { it })

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Active items
                    items(
                        count = activeWithIndex.size,
                        key = { activeWithIndex[it].second.symbolToken + activeWithIndex[it].second.optionToken },
                    ) { sortedIndex ->
                        val (originalIndex, stock) = activeWithIndex[sortedIndex]
                        StockListItem(
                            stock = stock,
                            ltpMap = ltpMap,
                            onEdit = { editingStockIndex = originalIndex },
                            onDelete = { deleteStockIndex = originalIndex },
                        )
                    }

                    // Grouped section — grouped by date with accordion
                    if (groupedWithIndex.isNotEmpty()) {
                        item(key = "closed_section_header") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (activeWithIndex.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(4.dp))
                                }
                                Text(
                                    text = "Closed / Not Triggered Orders",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }

                        groupedByDate.forEach { (date, dateItems) ->
                            val isExpanded = date in expandedClosedDates

                            item(key = "date_header_$date") {
                                ClosedDateHeader(
                                    date = date,
                                    count = dateItems.size,
                                    isExpanded = isExpanded,
                                    onToggle = {
                                        expandedClosedDates = if (isExpanded)
                                            expandedClosedDates - date
                                        else
                                            expandedClosedDates + date
                                    },
                                )
                            }

                            if (isExpanded) {
                                items(
                                    count = dateItems.size,
                                    key = { "closed_${dateItems[it].second.symbolToken}_${dateItems[it].second.optionToken}_$date" },
                                ) { idx ->
                                    val (originalIndex, stock) = dateItems[idx]
                                    StockListItem(
                                        stock = stock,
                                        ltpMap = ltpMap,
                                        onEdit = { editingStockIndex = originalIndex },
                                        onDelete = { deleteStockIndex = originalIndex },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isGroupedStatus(status: String?): Boolean {
    val normalized = status?.trim()?.lowercase()
    return normalized == "closed" || normalized == "not triggered" || normalized == "complete"
}

private fun shouldStreamStock(stock: StockEntry): Boolean {
    if (isGroupedStatus(stock.orderStatus)) return false
    return stock.symbolToken.isNotBlank() || stock.optionToken.isNotBlank()
}

private fun currentTradeDate(): String {
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
}

private fun stockItemKey(stock: StockEntry): String {
    return "${stock.symbolToken}|${stock.optionToken}|${stock.symbol}|${stock.option}"
}

private fun collectTokensByExchange(
    stockList: List<StockEntry>,
    includeStock: (StockEntry) -> Boolean,
): Map<Int, Set<String>> {
    val tokensByExchange = mutableMapOf<Int, MutableSet<String>>()

    stockList.forEach { stock ->
        if (!includeStock(stock)) return@forEach

        if (stock.symbolToken.isNotBlank()) {
            val symbolExchange = stock.symbolExchangeType?.takeIf { it > 0 } ?: 1
            tokensByExchange.getOrPut(symbolExchange) { mutableSetOf() }.add(stock.symbolToken)
        }
        if (stock.optionToken.isNotBlank()) {
            val optionExchange = stock.optionExchangeType?.takeIf { it > 0 } ?: 2
            tokensByExchange.getOrPut(optionExchange) { mutableSetOf() }.add(stock.optionToken)
        }
    }

    return tokensByExchange
}

private fun normalizedWatchType(watchType: String?): String {
    return if (watchType.equals("Short", ignoreCase = true)) "Short" else "Buy"
}

private fun shouldExecuteBuyWatchEntry(stockLtp: Double, stock: StockEntry): Boolean {
    return stockLtp > stock.candleHigh
}

private fun shouldExecuteShortWatchEntry(stockLtp: Double, stock: StockEntry): Boolean {
    return stockLtp < stock.candleLow
}

private fun resolveBuyWatchExitReason(stockLtp: Double, stock: StockEntry): String? {
    return when {
        stockLtp >= stock.target    -> "Target"
        stockLtp <= stock.candleLow -> "Stoploss"
        else -> null
    }
}

private fun resolveShortWatchExitReason(stockLtp: Double, stock: StockEntry): String? {
    return when {
        stockLtp <= stock.target     -> "Target"
        stockLtp >= stock.candleHigh -> "Stoploss"
        else -> null
    }
}

private val terminalOrderStatusCodes = setOf("AB02", "AB03", "AB05", "AB07")

private fun shouldTrackOrderOnSocket(
    stock: StockEntry,
    orderUpdatesMap: Map<String, OrderWebSocketService.OrderUpdate>,
): Boolean {
    val buyOrderId = stock.orderId?.takeIf { !it.startsWith("PAPER-") }
    val sellOrderId = stock.sellOrderId?.takeIf { !it.startsWith("PAPER-") }

    val buyUpdate = buyOrderId?.let { orderUpdatesMap[it] }
    val sellUpdate = sellOrderId?.let { orderUpdatesMap[it] }

    // If sell order exists, only sell-side tracking matters for this stock.
    val isBuyInFlight = buyOrderId != null && sellOrderId == null &&
        (buyUpdate == null || buyUpdate.orderStatusCode !in terminalOrderStatusCodes)
    val isSellInFlight = sellOrderId != null &&
        (sellUpdate == null || sellUpdate.orderStatusCode !in terminalOrderStatusCodes)

    return isBuyInFlight || isSellInFlight
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StockListItem(
    stock: StockEntry,
    ltpMap: Map<String, Double>,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var showPopup by remember { mutableStateOf(false) }

    val orderStatus = stock.orderStatus ?: "Watching" // Fallback for old cached entries
    val watchType = normalizedWatchType(stock.watchType)
    val stockLtp = ltpMap[stock.symbolToken] ?: 0.0
    val optionLtp = if (stock.optionToken.isNotBlank()) ltpMap[stock.optionToken] ?: 0.0 else 0.0
    val optionBuyPrice = stock.buyPrice ?: stock.orderPrice ?: 0.0  // Prefer avg fill price from WS
    val optionSellPrice = stock.sellPrice ?: 0.0
    val displayTarget = stock.target
    val totalQuantity = stock.orderQuantity ?: stock.optionLotSize // actual traded qty
    // P/L: if sell done → (sellPrice - buyPrice) * qty, else → (optionLTP - buyPrice) * qty
    val profitLoss = if (optionBuyPrice > 0) {
        if (optionSellPrice > 0) {
            (optionSellPrice - optionBuyPrice) * totalQuantity
        } else if (optionLtp > 0) {
            (optionLtp - optionBuyPrice) * totalQuantity
        } else 0.0
    } else 0.0
    
    // Status chip colors
    val statusColor = when (orderStatus) {
        "Watching" -> MaterialTheme.colorScheme.secondary
        "Triggered" -> MaterialTheme.colorScheme.tertiary
        "Open" -> MaterialTheme.colorScheme.primary
        "Not Triggered" -> Color(0xFFF57C00)
        "complete" -> Color(0xFF4CAF50)    // green
        "Closed" -> Color(0xFF2196F3)      // blue — fully exited
        "rejected" -> Color(0xFFE53935)    // red
        "cancelled" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.secondary
    }
    val watchTypeColor = if (watchType == "Short") Color(0xFFE53935) else Color(0xFF2E7D32)

    Box {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = { showPopup = true },
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Stock header: symbol + status | Stock LTP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stock.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Token: ${stock.symbolToken}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Watch Type: $watchType",
                            style = MaterialTheme.typography.bodySmall,
                            color = watchTypeColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    AssistChip(
                        onClick = { },
                        label = { Text(orderStatus, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = statusColor.copy(alpha = 0.2f),
                            labelColor = statusColor,
                        ),
                        modifier = Modifier.height(28.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Stock LTP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (stockLtp > 0) "%.2f".format(stockLtp) else "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Candle & Target details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DetailColumn(label = "Candle High", value = "%.2f".format(stock.candleHigh))
                DetailColumn(label = "Candle Low", value = "%.2f".format(stock.candleLow))
                DetailColumn(label = "Target", value = "%.2f".format(displayTarget))
            }

            // Option details (if present)
            if (stock.option.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Option Details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Option symbol and lot size row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stock.option,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Token: ${stock.optionToken}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (stock.optionLotSize > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Lot Size",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stock.optionLotSize.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option price metrics row: Buy Price, LTP, P/L
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    DetailColumn(
                        label = "Buy Price",
                        value = if (optionBuyPrice > 0) "%.2f".format(optionBuyPrice) else "—"
                    )
                    Column {
                        Text(
                            text = "LTP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (optionLtp > 0) "%.2f".format(optionLtp) else "—",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "P/L",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (profitLoss != 0.0) {
                                "${if (profitLoss > 0) "+" else ""}%.2f".format(profitLoss)
                            } else "—",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                profitLoss > 0 -> Color(0xFF4CAF50) // Green
                                profitLoss < 0 -> Color(0xFFE53935) // Red
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }

                // Order details (if order has been placed)
                if (stock.orderId != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Buy Order ID",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stock.orderId,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (stock.orderQuantity != null) {
                            DetailColumn(label = "Qty", value = stock.orderQuantity.toString())
                        }
                    }
                }

                // Sell order details (if sell order has been placed)
                if (stock.sellOrderId != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Sell / Exit (${stock.exitReason ?: "—"})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Sell Order ID",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stock.sellOrderId,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        val sellStatusText = stock.sellOrderStatus ?: "—"
                        val sellStatusColor = when (sellStatusText) {
                            "complete" -> Color(0xFF4CAF50)
                            "rejected" -> Color(0xFFE53935)
                            "cancelled" -> MaterialTheme.colorScheme.outline
                            "Open" -> MaterialTheme.colorScheme.primary
                            "Triggered" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        AssistChip(
                            onClick = { },
                            label = { Text(sellStatusText, fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = sellStatusColor.copy(alpha = 0.2f),
                                labelColor = sellStatusColor,
                            ),
                            modifier = Modifier.height(28.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DetailColumn(
                            label = "Sell Price",
                            value = if ((stock.sellPrice ?: 0.0) > 0) "%.2f".format(stock.sellPrice) else "—"
                        )
                        if (stock.sellFilledShares != null) {
                            DetailColumn(label = "Filled", value = stock.sellFilledShares)
                        }
                        // Net P/L if both buy and sell completed
                        if (optionBuyPrice > 0 && (stock.sellPrice ?: 0.0) > 0) {
                            val netPl = (stock.sellPrice!! - optionBuyPrice) * totalQuantity
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Net P/L",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${if (netPl > 0) "+" else ""}%.2f".format(netPl),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        netPl > 0 -> Color(0xFF4CAF50)
                                        netPl < 0 -> Color(0xFFE53935)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        // Long-press popup menu
        DropdownMenu(
            expanded = showPopup,
            onDismissRequest = { showPopup = false },
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    showPopup = false
                    onEdit()
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showPopup = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    } // end Box
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClosedDateHeader(
    date: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val displayDate = remember(date) {
        if (date == "Earlier") return@remember "Earlier (no date)"
        try {
            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(date)
            if (parsed != null)
                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(parsed)
            else date
        } catch (_: Exception) { date }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$count order${if (count != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DetailColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun WsStatusIndicator(state: LtpRepository.ConnectionState) {
    val (text, color) = when (state) {
        LtpRepository.ConnectionState.CONNECTED -> "Live" to Color(0xFF4CAF50)
        LtpRepository.ConnectionState.CONNECTING -> "Connecting…" to Color(0xFFFFA726)
        LtpRepository.ConnectionState.DISCONNECTED -> "Offline" to Color(0xFFBDBDBD)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
