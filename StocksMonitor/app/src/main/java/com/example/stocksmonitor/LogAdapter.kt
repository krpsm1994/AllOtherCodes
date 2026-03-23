package com.example.stocksmonitor

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class LogAdapter(context: Context, private val logs: List<LogEntry>) :
    ArrayAdapter<LogEntry>(context, 0, logs) {

    private val stockManager = StockManager(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_log, parent, false)

        val logEntry = logs[position]

        // Get instrument to display name:exchange format
        val displayName = getDisplayName(logEntry.symbol)
        view.findViewById<TextView>(R.id.log_symbol).text = displayName
        view.findViewById<TextView>(R.id.log_type).text = logEntry.type.name.replace("_", " ")
        view.findViewById<TextView>(R.id.log_message).text = logEntry.message
        view.findViewById<TextView>(R.id.log_time).text = logEntry.getFormattedTime()

        // Color code by type
        val typeView = view.findViewById<TextView>(R.id.log_type)
        typeView.setBackgroundColor(getTypeColor(logEntry.type))

        return view
    }

    private fun getDisplayName(symbol: String): String {
        // Try to find instrument from both filtered and unfiltered lists
        val allInstruments = stockManager.getAllInstruments()
        val instrument = allInstruments.find { it.tradingSymbol == symbol }
        
        return if (instrument != null) {
            if (instrument.exchange == "NSE") {
                "${instrument.name} : ${instrument.exchange}"
            } else if (instrument.exchange == "BSE") {
                "${instrument.tradingSymbol} : ${instrument.exchange}"
            } else {
                "${instrument.name.takeIf { it.isNotEmpty() } ?: instrument.tradingSymbol} : ${instrument.exchange}"
            }
        } else {
            // Fallback to symbol if not found
            symbol
        }
    }

    private fun getTypeColor(type: LogType): Int {
        return when (type) {
            LogType.STATUS_CHANGE -> Color.parseColor("#2196F3")      // Blue
            LogType.STOCK_ADDED -> Color.parseColor("#4CAF50")        // Green
            LogType.STOCK_UPDATED -> Color.parseColor("#FF9800")      // Orange
            LogType.STOCK_DELETED -> Color.parseColor("#F44336")      // Red
            LogType.STOCK_MOVED_TO_HISTORY -> Color.parseColor("#9C27B0")  // Purple
            LogType.STOCK_RESTORED -> Color.parseColor("#4CAF50")     // Green
            LogType.SL_HIT -> Color.parseColor("#F44336")             // Red
            LogType.TARGET_HIT -> Color.parseColor("#4CAF50")         // Green
        }
    }
}
