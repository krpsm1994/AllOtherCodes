package com.example.angelonestrategyexecutor.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.angelonestrategyexecutor.data.model.Instrument
import com.example.angelonestrategyexecutor.data.network.InstrumentsApiClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Equity stock: only token + symbol */
data class StockItem(
    val token: String,
    val symbol: String,
)

/** Option contract: token + symbol + lot size */
data class OptionItem(
    val token: String,
    val symbol: String,
    val lotSize: Int,
)

// Keep InstrumentItem as a unified type used by both dropdowns
data class InstrumentItem(
    val token: String,
    val symbol: String,
    val lotSize: Int = 0,
    val exchangeType: Int = 0, // 0=unknown, 1=NSE_CM, 2=NSE_FO, 3=BSE_CM, 4=BSE_FO, 5=MCX_FO
    val exchSeg: String = "", // raw exchange segment from API e.g. "NFO", "NSE", "BSE"
)

sealed class InstrumentsUiState {
    object Idle : InstrumentsUiState()
    object Loading : InstrumentsUiState()
    object Success : InstrumentsUiState()
    data class Error(val message: String) : InstrumentsUiState()
}

data class CachedInstruments(
    val equities: List<InstrumentItem>,
    val derivatives: List<InstrumentItem>,
)

class AddStockViewModel(application: Application) : AndroidViewModel(application) {

    private data class ParsedNfoOptionSymbol(
        val stockName: String,
        val expiry: String,
        val strike: String,
        val optionType: String,
    )

    private val gson = Gson()
    // Stored pattern for NFO options: <stock><DDMMMYY><strike><CE|PE>
    private val nfoOptionSymbolPattern = Regex("^([A-Z0-9]+)(\\d{2}[A-Z]{3}\\d{2})(\\d+)(CE|PE)$")
    private val cacheFile: File
        get() = File(getApplication<Application>().filesDir, "instruments_cache.json")

    init {
        loadFromCache()
    }

    /** Map exchange segment string to WebSocket exchange type int */
    private fun mapExchangeType(exchSeg: String): Int = when (exchSeg.uppercase()) {
        "NSE" -> 1   // nse_cm
        "NFO" -> 2   // nse_fo
        "BSE" -> 3   // bse_cm
        "BFO" -> 4   // bse_fo
        "MCX" -> 5   // mcx_fo
        "NCX" -> 7   // ncx_fo
        "CDS" -> 13  // cde_fo
        else  -> 0   // unknown
    }

    private val _uiState = MutableStateFlow<InstrumentsUiState>(InstrumentsUiState.Idle)
    val uiState: StateFlow<InstrumentsUiState> = _uiState.asStateFlow()

    /** All equity instruments (NSE/BSE, instrumenttype == "" or "EQ") */
    private val _stockSymbols = MutableStateFlow<List<InstrumentItem>>(emptyList())
    val stockSymbols: StateFlow<List<InstrumentItem>> = _stockSymbols.asStateFlow()

    /** All derivative instruments (NFO etc.) — filtered by selected stock on demand */
    private var allDerivatives: List<InstrumentItem> = emptyList()
    private val optionUnderlyingBySymbol = mutableMapOf<String, String>()

    private val _filteredOptions = MutableStateFlow<List<InstrumentItem>>(emptyList())
    val filteredOptions: StateFlow<List<InstrumentItem>> = _filteredOptions.asStateFlow()

    fun refreshInstruments() {
        if (_uiState.value is InstrumentsUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = InstrumentsUiState.Loading
            try {
                val raw: List<Instrument> = InstrumentsApiClient.service.fetchScripMaster()
                    .filter { it.exchSeg != "BFO" }  // Exclude BSE F&O segment

                // Options universe: NFO OPTSTK contracts only.
                allDerivatives = raw
                    .filter {
                        it.exchSeg.equals("NFO", ignoreCase = true) &&
                            it.instrumentType.equals("OPTSTK", ignoreCase = true)
                    }
                    .map {
                        val rawLotSize = it.lotSize.trim()
                        val parsedLotSize = rawLotSize.toIntOrNull() ?: 0
                        InstrumentItem(
                            token = it.token,
                            symbol = it.symbol,
                            lotSize = parsedLotSize,
                            exchangeType = mapExchangeType(it.exchSeg),
                            exchSeg = it.exchSeg,
                        )
                    }
                rebuildOptionUnderlyingMap(allDerivatives)

                val nfoUnderlyingNames = optionUnderlyingBySymbol.values.toSet()

                // Stocks universe: only EQ symbols whose underlying exists in NFO options universe.
                val equities = raw
                    .filter { it.symbol.endsWith("-EQ", ignoreCase = true) }
                    .mapNotNull {
                        val base = eqUnderlyingFromSymbol(it.symbol)
                        if (base !in nfoUnderlyingNames) return@mapNotNull null
                        InstrumentItem(
                            token = it.token,
                            symbol = it.symbol,
                            exchangeType = mapExchangeType(it.exchSeg),
                            exchSeg = it.exchSeg,
                        )
                    }
                    .distinctBy { it.symbol }
                    .sortedBy { it.symbol }

                // Validation logging
                val totalOptstk = allDerivatives.size
                val withValidLotSize = allDerivatives.count { it.lotSize > 0 }
                val withZeroLotSize = allDerivatives.count { it.lotSize == 0 }
                android.util.Log.d("AddStockVM", "=== NFO OPTSTK Validation ===")
                android.util.Log.d("AddStockVM", "Total NFO OPTSTK instruments: $totalOptstk")
                android.util.Log.d("AddStockVM", "With valid lot size (>0): $withValidLotSize")
                android.util.Log.d("AddStockVM", "With zero lot size: $withZeroLotSize")
                android.util.Log.d("AddStockVM", "NFO underlyings parsed from pattern: ${nfoUnderlyingNames.size}")
                android.util.Log.d("AddStockVM", "Equity universe after NFO filter: ${equities.size}")
                
                // Sample first 5 derivatives with RAW data from API
                raw.filter {
                    it.exchSeg.equals("NFO", ignoreCase = true) &&
                        it.instrumentType.equals("OPTSTK", ignoreCase = true)
                }.take(5).forEach { item ->
                    android.util.Log.d("AddStockVM", "Raw API: symbol='${item.symbol}', lotsize='${item.lotSize}', instrumenttype='${item.instrumentType}'")
                }
                
                // Sample first 5 processed derivatives
                allDerivatives.take(5).forEach { item ->
                    android.util.Log.d("AddStockVM", "Processed: ${item.symbol}, lotSize=${item.lotSize}")
                }

                _stockSymbols.value = equities
                _uiState.value = InstrumentsUiState.Success
                saveToCache(equities, allDerivatives)
            } catch (e: Exception) {
                _uiState.value = InstrumentsUiState.Error(
                    e.message ?: "Failed to fetch instruments"
                )
            }
        }
    }

    /**
     * When a stock is selected from the equities dropdown, filter derivatives
     * whose `name` matches the selected stock's name (case-insensitive).
     */
    fun filterOptionsForStock(stockSymbol: String, watchType: String = "Buy") {
        if (stockSymbol.isBlank()) {
            _filteredOptions.value = emptyList()
            return
        }
        val normalizedWatchType = if (watchType.equals("Short", ignoreCase = true)) "Short" else "Buy"
        val requiredSuffix = if (normalizedWatchType == "Short") "PE" else "CE"

        val base = eqUnderlyingFromSymbol(stockSymbol)
        val filtered = allDerivatives
            .filter {
                val symbol = it.symbol.uppercase()
                val optionUnderlying = optionUnderlyingBySymbol[it.symbol]
                    ?: parseNfoOptionSymbol(it.symbol)?.stockName
                    ?: ""
                optionUnderlying == base && symbol.endsWith(requiredSuffix)
            }
            .sortedBy { it.symbol }
        
        _filteredOptions.value = filtered
        
        // Validation logging
        android.util.Log.d("AddStockVM", "=== Filtered Options for $stockSymbol ===")
        android.util.Log.d("AddStockVM", "Watch type: $normalizedWatchType (suffix=$requiredSuffix)")
        android.util.Log.d("AddStockVM", "Base ticker: $base")
        android.util.Log.d("AddStockVM", "Total filtered options: ${filtered.size}")
        android.util.Log.d("AddStockVM", "Options with valid lot size: ${filtered.count { it.lotSize > 0 }}")
        
        // Show first 3 filtered options
        filtered.take(3).forEach { item ->
            android.util.Log.d("AddStockVM", "  → ${item.symbol}, lotSize=${item.lotSize}")
        }
    }

    fun resetError() {
        if (_uiState.value is InstrumentsUiState.Error) {
            _uiState.value = InstrumentsUiState.Idle
        }
    }

    private fun loadFromCache() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (!cacheFile.exists()) return@withContext
                    val type = object : TypeToken<CachedInstruments>() {}.type
                    val cached: CachedInstruments? = gson.fromJson(cacheFile.readText(), type)
                    if (cached != null) {
                        allDerivatives = cached.derivatives
                            .filter { it.exchSeg.equals("NFO", ignoreCase = true) }
                        rebuildOptionUnderlyingMap(allDerivatives)
                        val nfoUnderlyingNames = optionUnderlyingBySymbol.values.toSet()
                        _stockSymbols.value = cached.equities
                            .filter { eqUnderlyingFromSymbol(it.symbol) in nfoUnderlyingNames }
                            .sortedBy { it.symbol }
                        _uiState.value = InstrumentsUiState.Success
                    }
                }
            } catch (_: Exception) { /* silently ignore corrupt cache */ }
        }
    }

    private fun saveToCache(equities: List<InstrumentItem>, derivatives: List<InstrumentItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cacheFile.writeText(gson.toJson(CachedInstruments(equities, derivatives)))
            } catch (_: Exception) { /* silently ignore write failure */ }
        }
    }

    private fun eqUnderlyingFromSymbol(symbol: String): String {
        val base = symbol.removeSuffix("-EQ").removeSuffix("-eq")
        return normalizeUnderlyingName(base)
    }

    private fun normalizeUnderlyingName(raw: String): String {
        return raw.uppercase(Locale.getDefault()).replace(Regex("[^A-Z0-9]"), "")
    }

    private fun parseNfoOptionSymbol(symbol: String): ParsedNfoOptionSymbol? {
        val normalizedSymbol = symbol.uppercase(Locale.getDefault())
        val match = nfoOptionSymbolPattern.matchEntire(normalizedSymbol) ?: return null
        return ParsedNfoOptionSymbol(
            stockName = normalizeUnderlyingName(match.groupValues[1]),
            expiry = match.groupValues[2],
            strike = match.groupValues[3],
            optionType = match.groupValues[4],
        )
    }

    private fun rebuildOptionUnderlyingMap(options: List<InstrumentItem>) {
        optionUnderlyingBySymbol.clear()
        options.forEach { option ->
            val parsed = parseNfoOptionSymbol(option.symbol) ?: return@forEach
            optionUnderlyingBySymbol[option.symbol] = parsed.stockName
        }
    }
}
