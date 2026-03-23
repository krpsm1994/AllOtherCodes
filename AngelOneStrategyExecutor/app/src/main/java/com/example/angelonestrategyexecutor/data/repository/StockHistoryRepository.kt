package com.example.angelonestrategyexecutor.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class representing a single history entry for a stock change.
 */
data class StockHistoryEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val symbol: String,
    val action: String,       // e.g. "Added", "Edited", "Deleted", "Buy Triggered", "Buy Filled", "Sell Triggered", "Sell Filled", "Status Changed"
    val details: String = "", // Human-readable change description
) {
    val formattedTime: String
        get() = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(timestamp))

    val formattedDate: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))

    val formattedTimeOnly: String
        get() = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Singleton repository that persists stock change history to a JSON file.
 */
object StockHistoryRepository {

    private const val TAG = "StockHistory"
    private const val FILE_NAME = "stock_history.json"
    private const val MAX_ENTRIES = 500 // Keep last 500 entries to avoid unbounded growth

    private val gson = Gson()
    private lateinit var historyFile: File
    private val _history = mutableListOf<StockHistoryEntry>()

    val history: List<StockHistoryEntry> get() = _history.toList()

    fun init(context: Context) {
        historyFile = File(context.filesDir, FILE_NAME)
        loadFromDisk()
    }

    fun log(symbol: String, action: String, details: String = "") {
        val entry = StockHistoryEntry(
            symbol = symbol,
            action = action,
            details = details,
        )
        _history.add(0, entry) // newest first
        // Trim to max entries
        while (_history.size > MAX_ENTRIES) {
            _history.removeAt(_history.lastIndex)
        }
        saveToDisk()
        Log.d(TAG, "[$action] $symbol: $details")
    }

    fun clear() {
        _history.clear()
        saveToDisk()
    }

    private fun loadFromDisk() {
        try {
            if (historyFile.exists()) {
                val json = historyFile.readText()
                val type = object : TypeToken<List<StockHistoryEntry>>() {}.type
                val loaded: List<StockHistoryEntry>? = gson.fromJson(json, type)
                _history.clear()
                if (loaded != null) _history.addAll(loaded)
                Log.d(TAG, "Loaded ${_history.size} history entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load history: ${e.message}")
        }
    }

    private fun saveToDisk() {
        try {
            historyFile.writeText(gson.toJson(_history))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history: ${e.message}")
        }
    }
}
