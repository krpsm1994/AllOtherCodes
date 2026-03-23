package com.example.stocksmonitor

import android.content.Context
import android.util.Log
import java.io.File

class LogManager(private val context: Context) {
    private val logsFile = File(context.filesDir, "stock_logs.txt")
    private val logs = mutableListOf<LogEntry>()

    init {
        loadLogs()
    }

    private fun loadLogs() {
        if (logsFile.exists()) {
            logsFile.readLines().forEach { line ->
                val logEntry = LogEntry.fromFileString(line)
                if (logEntry != null) {
                    logs.add(logEntry)
                }
            }
            // Sort by timestamp in descending order (newest first)
            logs.sortByDescending { it.timestamp }
        }
    }

    fun addLog(type: LogType, symbol: String, message: String) {
        val logEntry = LogEntry(
            timestamp = System.currentTimeMillis(),
            type = type,
            symbol = symbol,
            message = message
        )
        logs.add(0, logEntry) // Add to beginning for newest first
        saveLogs()
        Log.d("LogManager", "[$type] $symbol: $message")
    }

    fun getLogs(): List<LogEntry> {
        return logs.toList()
    }

    fun getLogsBySymbol(symbol: String): List<LogEntry> {
        return logs.filter { it.symbol == symbol }
    }

    fun getLogsByType(type: LogType): List<LogEntry> {
        return logs.filter { it.type == type }
    }

    fun clearLogs() {
        logs.clear()
        saveLogs()
    }

    private fun saveLogs() {
        try {
            logsFile.writeText("")
            // Keep only recent logs (e.g., last 500)
            val logsToSave = logs.takeLast(500).reversed()
            logsFile.writeText(logsToSave.joinToString("\n") { it.toFileString() })
        } catch (e: Exception) {
            Log.e("LogManager", "Error saving logs", e)
        }
    }
}
