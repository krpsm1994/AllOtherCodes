package com.example.angelonestrategyexecutor.data.repository

import android.content.Context
import android.util.Log
import com.example.angelonestrategyexecutor.data.model.Instrument
import com.example.angelonestrategyexecutor.data.network.InstrumentsApiClient
import com.google.gson.Gson
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WeeklyRefreshSummary(
    val equitiesCount: Int,
    val derivativesCount: Int,
    val underlyingsCount: Int,
)

private data class CachedInstrumentItem(
    val token: String,
    val symbol: String,
    val lotSize: Int = 0,
    val exchangeType: Int = 0,
    val exchSeg: String = "",
)

private data class CachedInstrumentsPayload(
    val equities: List<CachedInstrumentItem>,
    val derivatives: List<CachedInstrumentItem>,
)

private data class ParsedNfoOptionSymbol(
    val stockName: String,
)

object InstrumentsRefreshRepository {

    private const val TAG = "InstrumentsRefresh"
    private const val CACHE_FILE_NAME = "instruments_cache.json"
    private val gson = Gson()

    // <stock><DDMMMYY><strike><CE|PE>
    private val nfoOptionSymbolPattern =
        Regex("^([A-Z0-9]+)(\\d{2}[A-Z]{3}\\d{2})(\\d+)(CE|PE)$")

    suspend fun refreshAndCache(context: Context): WeeklyRefreshSummary = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        val raw: List<Instrument> = InstrumentsApiClient.service.fetchScripMaster()
            .filter { !it.exchSeg.equals("BFO", ignoreCase = true) }

        val derivatives = raw
            .filter {
                it.exchSeg.equals("NFO", ignoreCase = true) &&
                    it.instrumentType.equals("OPTSTK", ignoreCase = true)
            }
            .map {
                val rawLotSize = it.lotSize.trim()
                val parsedLotSize = rawLotSize.toIntOrNull() ?: 0
                CachedInstrumentItem(
                    token = it.token,
                    symbol = it.symbol,
                    lotSize = parsedLotSize,
                    exchangeType = mapExchangeType(it.exchSeg),
                    exchSeg = it.exchSeg,
                )
            }

        val optionUnderlyingBySymbol = mutableMapOf<String, String>()
        derivatives.forEach { option ->
            val parsed = parseNfoOptionSymbol(option.symbol) ?: return@forEach
            optionUnderlyingBySymbol[option.symbol] = parsed.stockName
        }

        val nfoUnderlyingNames = optionUnderlyingBySymbol.values.toSet()
        val equities = raw
            .filter { it.symbol.endsWith("-EQ", ignoreCase = true) }
            .mapNotNull {
                val base = eqUnderlyingFromSymbol(it.symbol)
                if (base !in nfoUnderlyingNames) return@mapNotNull null
                CachedInstrumentItem(
                    token = it.token,
                    symbol = it.symbol,
                    exchangeType = mapExchangeType(it.exchSeg),
                    exchSeg = it.exchSeg,
                )
            }
            .distinctBy { it.symbol }
            .sortedBy { it.symbol }

        cacheFile.writeText(gson.toJson(CachedInstrumentsPayload(equities, derivatives)))

        val summary = WeeklyRefreshSummary(
            equitiesCount = equities.size,
            derivativesCount = derivatives.size,
            underlyingsCount = nfoUnderlyingNames.size,
        )
        Log.d(
            TAG,
            "Cache refreshed: equities=${summary.equitiesCount}, derivatives=${summary.derivativesCount}, underlyings=${summary.underlyingsCount}",
        )
        summary
    }

    private fun mapExchangeType(exchSeg: String): Int = when (exchSeg.uppercase()) {
        "NSE" -> 1
        "NFO" -> 2
        "BSE" -> 3
        "BFO" -> 4
        "MCX" -> 5
        "NCX" -> 7
        "CDS" -> 13
        else -> 0
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
        )
    }
}
