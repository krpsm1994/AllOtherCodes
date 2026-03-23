package com.example.angelonestrategyexecutor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.angelonestrategyexecutor.ui.viewmodel.AddStockViewModel
import com.example.angelonestrategyexecutor.ui.viewmodel.InstrumentItem
import com.example.angelonestrategyexecutor.ui.viewmodel.InstrumentsUiState

data class StockEntry(
    val symbol: String,
    val symbolToken: String,
    val option: String,
    val optionToken: String,
    val optionLotSize: Int,
    val candleHigh: Double,
    val candleLow: Double,
    val target: Double,
    val watchType: String? = "Buy",     // Buy => CE options, Short => PE options
    val orderStatus: String? = "Watching",
    val symbolExchangeType: Int? = null,  // null → assume 1 (NSE Cash)
    val optionExchangeType: Int? = null,  // null → assume 2 (NSE F&O)
    val optionExchSeg: String? = null,    // raw exchange segment e.g. "NFO"
    // Order details — populated after order is placed
    val orderId: String? = null,
    val uniqueOrderId: String? = null,
    val orderPrice: Double? = null,       // limit price used when placing
    val orderQuantity: Int? = null,       // quantity ordered
    // Filled after WebSocket order update
    val buyPrice: Double? = null,         // average fill price from order WS
    val filledShares: String? = null,     // shares filled so far
    val orderText: String? = null,        // rejection/status message from exchange
    // Sell order details — populated when exit order is placed
    val sellOrderId: String? = null,
    val sellUniqueOrderId: String? = null,
    val sellPrice: Double? = null,        // sell limit price or avg fill price
    val sellOrderStatus: String? = null,  // Triggered / Open / complete / rejected / cancelled
    val sellFilledShares: String? = null,
    val sellOrderText: String? = null,
    val exitReason: String? = null,       // "Target" or "Stoploss"
    val closedDate: String? = null,        // date when order was closed "yyyy-MM-dd"
)

private fun normalizeWatchType(watchType: String?): String {
    return if (watchType.equals("Short", ignoreCase = true)) "Short" else "Buy"
}

private fun resolveInitialWatchType(existingStock: StockEntry?): String {
    val explicitWatchType = existingStock?.watchType
    if (!explicitWatchType.isNullOrBlank()) {
        return normalizeWatchType(explicitWatchType)
    }

    val optionSymbol = existingStock?.option.orEmpty().uppercase()
    return when {
        optionSymbol.endsWith("PE") -> "Short"
        optionSymbol.endsWith("CE") -> "Buy"
        else -> "Buy"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStockScreen(
    onBack: () -> Unit,
    onStockAdded: (StockEntry) -> Unit,
    existingStock: StockEntry? = null,
    viewModel: AddStockViewModel = viewModel(),
) {
    // Handle system back gesture/button
    BackHandler(onBack = onBack)

    val isEditMode = existingStock != null
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stockSymbols by viewModel.stockSymbols.collectAsStateWithLifecycle()
    val filteredOptions by viewModel.filteredOptions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val isRefreshing = uiState is InstrumentsUiState.Loading

    var selectedSymbolItem by remember {
        mutableStateOf(
            existingStock?.let {
                InstrumentItem(
                    token = it.symbolToken, symbol = it.symbol,
                    lotSize = 0, exchangeType = it.symbolExchangeType ?: 1,
                )
            }
        )
    }
    var selectedOptionItem by remember {
        mutableStateOf(
            existingStock?.let {
                if (it.option.isNotEmpty()) InstrumentItem(
                    token = it.optionToken, symbol = it.option,
                    lotSize = it.optionLotSize, exchangeType = it.optionExchangeType ?: 2,
                    exchSeg = it.optionExchSeg ?: "",
                ) else null
            }
        )
    }
    var symbolQuery by rememberSaveable { mutableStateOf(existingStock?.symbol ?: "") }
    var optionQuery by rememberSaveable { mutableStateOf(existingStock?.option ?: "") }
    var candleHighText by rememberSaveable { mutableStateOf(existingStock?.let { "%.2f".format(it.candleHigh) } ?: "") }
    var candleLowText by rememberSaveable { mutableStateOf(existingStock?.let { "%.2f".format(it.candleLow) } ?: "") }
    var targetText by rememberSaveable { mutableStateOf(existingStock?.let { "%.2f".format(it.target) } ?: "") }
    var watchType by rememberSaveable { mutableStateOf(resolveInitialWatchType(existingStock)) }
    var isTargetManual by rememberSaveable { mutableStateOf(existingStock != null) }
    var orderStatus by rememberSaveable { mutableStateOf(existingStock?.orderStatus ?: "Watching") }
    var optionBuyPriceText by rememberSaveable { mutableStateOf(existingStock?.buyPrice?.let { "%.2f".format(it) } ?: "") }
    var optionSellPriceText by rememberSaveable { mutableStateOf(existingStock?.sellPrice?.let { "%.2f".format(it) } ?: "") }
    var watchTypeExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var symbolExpanded by remember { mutableStateOf(false) }
    var optionExpanded by remember { mutableStateOf(false) }
    var symbolError by remember { mutableStateOf(false) }
    var candleHighError by remember { mutableStateOf(false) }
    var candleLowError by remember { mutableStateOf(false) }
    var targetError by remember { mutableStateOf(false) }

    val displayedSymbols = remember(stockSymbols, symbolQuery) {
        if (symbolQuery.isBlank()) stockSymbols
        else stockSymbols.filter {
            it.symbol.contains(symbolQuery, ignoreCase = true)
        }
    }

    val displayedOptions = remember(filteredOptions, optionQuery) {
        if (optionQuery.isBlank()) filteredOptions
        else filteredOptions.filter {
            it.symbol.contains(optionQuery, ignoreCase = true)
        }
    }

    val candleHigh = candleHighText.toDoubleOrNull()
    val candleLow = candleLowText.toDoubleOrNull()
    val target = targetText.toDoubleOrNull()

    // Auto-calculate target when candle values/watch type change (unless user manually edited)
    LaunchedEffect(candleHigh, candleLow, watchType) {
        if (!isTargetManual && candleHigh != null && candleLow != null) {
            val calculated = if (watchType == "Short") {
                (2.0 * candleLow) - candleHigh
            } else {
                (2.0 * candleHigh) - candleLow
            }
            targetText = "%.2f".format(calculated)
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState) {
        if (uiState is InstrumentsUiState.Error) {
            snackbarHostState.showSnackbar((uiState as InstrumentsUiState.Error).message)
            viewModel.resetError()
        }
    }

    // Keep option list synced to selected stock + watch type.
    // On user changes, clear selected option to avoid stale CE/PE mismatch.
    var filterInitialized by remember { mutableStateOf(existingStock == null) }
    LaunchedEffect(selectedSymbolItem?.symbol, watchType) {
        val selectedSymbol = selectedSymbolItem?.symbol ?: ""
        if (!filterInitialized) {
            filterInitialized = true
            viewModel.filterOptionsForStock(selectedSymbol, watchType)
            return@LaunchedEffect
        }

        selectedOptionItem = null
        optionQuery = ""
        optionExpanded = false
        viewModel.filterOptionsForStock(selectedSymbol, watchType)
    }

    fun clearForm() {
        selectedSymbolItem = null; selectedOptionItem = null
        symbolQuery = ""; optionQuery = ""
        candleHighText = ""; candleLowText = ""; targetText = ""
        watchType = "Buy"
        watchTypeExpanded = false
        isTargetManual = false
        orderStatus = "Watching"
        optionBuyPriceText = ""
        optionSellPriceText = ""
        symbolError = false; candleHighError = false; candleLowError = false; targetError = false
        viewModel.filterOptionsForStock("", watchType)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Stock" else "Add Stock", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshInstruments() },
                        enabled = !isRefreshing,
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Instruments",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Stock Symbol autocomplete ──────────────────────────────
            ExposedDropdownMenuBox(
                expanded = symbolExpanded && displayedSymbols.isNotEmpty(),
                onExpandedChange = { },
            ) {
                OutlinedTextField(
                    value = symbolQuery,
                    onValueChange = {
                        symbolQuery = it
                        selectedSymbolItem = null
                        symbolExpanded = it.isNotEmpty()
                        symbolError = false
                    },
                    label = { Text("Stock Symbol") },
                    placeholder = { Text("Type to search…") },
                    trailingIcon = {
                        if (symbolQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                symbolQuery = ""; selectedSymbolItem = null; symbolExpanded = false
                            }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                        }
                    },
                    isError = symbolError,
                    supportingText = {
                        when {
                            symbolError -> Text("Symbol is required", color = MaterialTheme.colorScheme.error)
                            isRefreshing -> Text("Loading instruments…")
                            stockSymbols.isEmpty() -> Text("Tap ↻ to fetch latest instruments")
                            selectedSymbolItem != null -> Text("Token: ${selectedSymbolItem!!.token}")
                            symbolQuery.isNotEmpty() -> Text("${displayedSymbols.size} of ${stockSymbols.size} matches")
                            else -> Text("${stockSymbols.size} instruments loaded")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = symbolExpanded && displayedSymbols.isNotEmpty(),
                    onDismissRequest = { symbolExpanded = false },
                ) {
                    displayedSymbols.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.symbol, fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                            onClick = {
                                selectedSymbolItem = item
                                symbolQuery = item.symbol
                                symbolExpanded = false
                            },
                        )
                    }
                }
            }

            // ── Watch Type dropdown ──────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = watchTypeExpanded,
                onExpandedChange = { watchTypeExpanded = it },
            ) {
                OutlinedTextField(
                    value = watchType,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Watch Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = watchTypeExpanded) },
                    supportingText = {
                        Text(
                            if (watchType == "Short") "Short watches PE options"
                            else "Buy watches CE options"
                        )
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = watchTypeExpanded,
                    onDismissRequest = { watchTypeExpanded = false },
                ) {
                    listOf("Short", "Buy").forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                watchType = type
                                watchTypeExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            // ── Stock Option autocomplete ──────────────────────────────
            ExposedDropdownMenuBox(
                expanded = optionExpanded && displayedOptions.isNotEmpty(),
                onExpandedChange = { },
            ) {
                OutlinedTextField(
                    value = optionQuery,
                    onValueChange = {
                        optionQuery = it
                        selectedOptionItem = null
                        optionExpanded = it.isNotEmpty()
                    },
                    label = { Text("Stock Option") },
                    placeholder = { Text("Type to search…") },
                    trailingIcon = {
                        if (optionQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                optionQuery = ""; selectedOptionItem = null; optionExpanded = false
                            }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                        }
                    },
                    supportingText = {
                        when {
                            selectedSymbolItem == null -> Text("Select a stock symbol first")
                            filteredOptions.isEmpty() -> Text("No ${if (watchType == "Short") "PE" else "CE"} OPTSTK contracts found")
                            selectedOptionItem != null -> Text("Lot size: ${selectedOptionItem!!.lotSize}")
                            optionQuery.isNotEmpty() -> Text("${displayedOptions.size} of ${filteredOptions.size} matches")
                            else -> Text("${filteredOptions.size} ${if (watchType == "Short") "PE" else "CE"} contracts available")
                        }
                    },
                    enabled = selectedSymbolItem != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = optionExpanded && displayedOptions.isNotEmpty(),
                    onDismissRequest = { optionExpanded = false },
                ) {
                    displayedOptions.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(item.symbol, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(
                                        "Lot: ${item.lotSize}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            },
                            onClick = {
                                selectedOptionItem = item
                                optionQuery = item.symbol
                                optionExpanded = false
                                android.util.Log.d("AddStockScreen", "Selected option: ${item.symbol}, lotSize=${item.lotSize}")
                            },
                        )
                    }
                }
            }
            HorizontalDivider()

            // ── 15-min candle inputs ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = candleHighText,
                    onValueChange = { candleHighText = it; candleHighError = false },
                    label = { Text("15-min Candle High") },
                    placeholder = { Text("Buy price") },
                    isError = candleHighError,
                    supportingText = {
                        if (candleHighError) Text("Required", color = MaterialTheme.colorScheme.error)
                        else Text(if (watchType == "Short") "Acts as stop loss" else "Acts as buy price")
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = candleLowText,
                    onValueChange = { candleLowText = it; candleLowError = false },
                    label = { Text("15-min Candle Low") },
                    placeholder = { Text("Stop loss") },
                    isError = candleLowError,
                    supportingText = {
                        if (candleLowError) Text("Required", color = MaterialTheme.colorScheme.error)
                        else Text(if (watchType == "Short") "Acts as buy price" else "Acts as stop loss")
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            // ── Target (Auto-calculated, but editable) ────────────────────────
            OutlinedTextField(
                value = targetText,
                onValueChange = { 
                    targetText = it
                    isTargetManual = it.isNotBlank()
                    targetError = false
                },
                label = { Text("Target") },
                placeholder = { Text("Auto-calculated") },
                isError = targetError,
                supportingText = {
                    if (targetError) Text("Required", color = MaterialTheme.colorScheme.error)
                    else Text(
                        if (watchType == "Short") {
                            "= (2 × Candle Low) − Candle High ${if (isTargetManual) "(manual override)" else ""}"
                        } else {
                            "= (2 × Candle High) − Candle Low ${if (isTargetManual) "(manual override)" else ""}"
                        }
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
            )

            Spacer(Modifier.height(12.dp))

            // ── Order Status dropdown ──────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = it },
            ) {
                OutlinedTextField(
                    value = orderStatus,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Order Status") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                    supportingText = { Text("Current status of this stock entry") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = statusExpanded,
                    onDismissRequest = { statusExpanded = false },
                ) {
                    listOf("Watching", "Triggered", "Open", "Closed").forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status) },
                            onClick = {
                                orderStatus = status
                                statusExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            // ── Option Buy Price (shown when status is Open or Closed) ────────────
            if (orderStatus == "Open" || orderStatus == "Closed") {
                OutlinedTextField(
                    value = optionBuyPriceText,
                    onValueChange = { optionBuyPriceText = it },
                    label = { Text("Option Buy Price") },
                    placeholder = { Text("e.g. 120.50") },
                    supportingText = { Text("Average fill price of the buy order") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
            }

            // ── Option Sell Price (shown only when status is Closed) ────────────
            if (orderStatus == "Closed") {
                OutlinedTextField(
                    value = optionSellPriceText,
                    onValueChange = { optionSellPriceText = it },
                    label = { Text("Option Sell Price") },
                    placeholder = { Text("e.g. 145.00") },
                    supportingText = { Text("Average fill price of the sell order") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Add Stock button ──────────────────────────────────────────────
            Button(
                onClick = {
                    symbolError = selectedSymbolItem == null
                    candleHighError = candleHighText.isBlank()
                    candleLowError = candleLowText.isBlank()
                    targetError = targetText.isBlank()
                    if (!symbolError && !candleHighError && !candleLowError && !targetError
                        && candleHigh != null && candleLow != null && target != null) {
                        
                        val lotSize = selectedOptionItem?.lotSize ?: 0
                        android.util.Log.d("AddStockScreen", "=== Adding Stock ===")
                        android.util.Log.d("AddStockScreen", "Symbol: ${selectedSymbolItem!!.symbol}")
                        android.util.Log.d("AddStockScreen", "Option: ${selectedOptionItem?.symbol ?: "None"}")
                        android.util.Log.d("AddStockScreen", "Option lotSize: $lotSize")
                        
                        onStockAdded(StockEntry(
                            symbol = selectedSymbolItem!!.symbol,
                            symbolToken = selectedSymbolItem!!.token,
                            option = selectedOptionItem?.symbol ?: "",
                            optionToken = selectedOptionItem?.token ?: "",
                            optionLotSize = lotSize,
                            candleHigh = candleHigh,
                            candleLow = candleLow,
                            target = target,
                            watchType = watchType,
                            orderStatus = orderStatus,
                            symbolExchangeType = selectedSymbolItem!!.exchangeType,
                            optionExchangeType = selectedOptionItem?.exchangeType,
                            optionExchSeg = selectedOptionItem?.exchSeg,
                            buyPrice = if (orderStatus == "Open" || orderStatus == "Closed") optionBuyPriceText.toDoubleOrNull() else existingStock?.buyPrice,
                            // Preserve existing order/sell details when editing
                            orderId = existingStock?.orderId,
                            uniqueOrderId = existingStock?.uniqueOrderId,
                            orderPrice = existingStock?.orderPrice,
                            orderQuantity = existingStock?.orderQuantity,
                            filledShares = existingStock?.filledShares,
                            orderText = existingStock?.orderText,
                            sellOrderId = existingStock?.sellOrderId,
                            sellUniqueOrderId = existingStock?.sellUniqueOrderId,
                            sellPrice = if (orderStatus == "Closed") optionSellPriceText.toDoubleOrNull() else existingStock?.sellPrice,
                            sellOrderStatus = if (orderStatus == "Closed") "complete" else existingStock?.sellOrderStatus,
                            sellFilledShares = existingStock?.sellFilledShares,
                            sellOrderText = existingStock?.sellOrderText,
                            exitReason = existingStock?.exitReason,
                            closedDate = if (orderStatus == "Closed") (existingStock?.closedDate ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())) else existingStock?.closedDate,
                        ))
                        clearForm()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
            ) {
                Text(if (isEditMode) "Update Stock" else "Add Stock", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            // ── Clear Form + Refresh Instruments ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { clearForm() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Clear Form", fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = { viewModel.refreshInstruments() },
                    enabled = !isRefreshing,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondary,
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(" Refresh Instruments", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
