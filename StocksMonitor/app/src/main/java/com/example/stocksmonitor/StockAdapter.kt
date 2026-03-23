package com.example.stocksmonitor

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class StockAdapter(context: Context, private val stocks: List<Stock>, private val quotes: Map<String, Quote> = emptyMap()) :
    ArrayAdapter<Stock>(context, 0, stocks) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_stock, parent, false)

        val stock = stocks[position]
        val instrument = stock.instrument

        val displayName = if (instrument.exchange == "NSE") {
            "${instrument.name} : ${instrument.exchange}"
        } else if (instrument.exchange == "BSE") {
            "${instrument.tradingSymbol} : ${instrument.exchange}"
        } else {
            // Fallback for other exchanges
            if (instrument.name.isNotEmpty()) {
                "${instrument.name} : ${instrument.exchange}"
            } else {
                "${instrument.tradingSymbol} : ${instrument.exchange}"
            }
        }

        view.findViewById<TextView>(R.id.stock_name).text = displayName
        
        // Show "WATCH" badge if onlyWatch is true
        val onlyWatchBadge = view.findViewById<TextView>(R.id.only_watch_badge)
        if (stock.onlyWatch) {
            onlyWatchBadge.visibility = View.VISIBLE
        } else {
            onlyWatchBadge.visibility = View.GONE
        }
        
        view.findViewById<TextView>(R.id.quantity).text = stock.quantity.toString()
        val amountRequired = (stock.buyPrice * stock.quantity) + 20
        view.findViewById<TextView>(R.id.amount_required).text = "₹${String.format("%.2f", amountRequired)}"
        view.findViewById<TextView>(R.id.buy_price).text = "₹${stock.buyPrice}"
        view.findViewById<TextView>(R.id.stop_loss).text = "₹${stock.stopLoss}"
        view.findViewById<TextView>(R.id.target).text = "₹${stock.target}"
        
        // Calculate and display percentages based on buy price
        val slPercent = ((stock.stopLoss - stock.buyPrice) / stock.buyPrice) * 100
        val slPercentText = String.format("%.2f", slPercent)
        view.findViewById<TextView>(R.id.stop_loss_percent).text = "($slPercentText%)"
        
        val targetPercent = ((stock.target - stock.buyPrice) / stock.buyPrice) * 100
        val targetPercentText = String.format("%.2f", targetPercent)
        view.findViewById<TextView>(R.id.target_percent).text = "(+$targetPercentText%)"

        // Display status with percentage difference
        val statusView = view.findViewById<TextView>(R.id.status)
        val ltpView = view.findViewById<TextView>(R.id.ltp)
        val percentChangeView = view.findViewById<TextView>(R.id.percent_change)
        val quote = quotes[instrument.instrumentToken]
        
        // For history stocks, always show the final status and percentage
        if (stock.status == StockStatus.HISTORY && stock.finalStatus != null) {
            val finalStatusText = stock.finalStatus.name.replace("_", " ")
            val percentageText = if (stock.finalPercentage != null) {
                val sign = if (stock.finalPercentage >= 0) "+" else ""
                "($sign${String.format("%.2f", stock.finalPercentage)}%)"
            } else {
                ""
            }
            statusView.text = "$finalStatusText $percentageText"
        } else if (stock.status == StockStatus.HISTORY) {
            // Manually moved to history (no final status)
            statusView.text = "Manually Moved"
        } else if (quote != null) {
            val percentageDiff = calculatePercentageDifference(stock, quote)
            statusView.text = "${stock.status.name.replace("_", " ")} ${percentageDiff}"
        } else {
            statusView.text = stock.status.name.replace("_", " ")
        }
        
        // Set status background and text color based on conditions
        // For history stocks, use finalStatus for coloring if available, otherwise use HISTORY color
        val statusForColoring = if (stock.status == StockStatus.HISTORY && stock.finalStatus != null) {
            stock.finalStatus
        } else {
            stock.status
        }
        val statusColorPair = getStatusColorAndTextColor(statusForColoring, stock.buyPrice, quote)
        statusView.setBackgroundColor(statusColorPair.first)
        statusView.setTextColor(statusColorPair.second)
        
        if (quote != null) {
            ltpView.text = "₹${String.format("%.2f", quote.ltp)}"
            
            val percentText = String.format("%.2f%%", quote.percentChange)
            percentChangeView.text = if (quote.percentChange >= 0) "+$percentText" else percentText
            
            // Color code based on positive/negative change
            val color = if (quote.percentChange >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            ltpView.setTextColor(color)
            percentChangeView.setTextColor(color)
        } else {
            // For history stocks, show the final LTP if available
            if (stock.status == StockStatus.HISTORY && stock.finalLTP != null) {
                ltpView.text = "₹${String.format("%.2f", stock.finalLTP)}"
                ltpView.setTextColor(Color.GRAY)
            } else {
                ltpView.text = "--"
                ltpView.setTextColor(Color.GRAY)
            }
            percentChangeView.text = "--"
            percentChangeView.setTextColor(Color.GRAY)
        }

        return view
    }

    private fun getStatusColor(status: StockStatus): Int {
        return when (status) {
            StockStatus.NOT_TRIGGERED -> Color.parseColor("#9E9E9E")  // Gray
            StockStatus.ORDER_PLACED -> Color.parseColor("#FF9800")    // Orange
            StockStatus.TRIGGERED -> Color.parseColor("#2196F3")      // Blue
            StockStatus.SL_HIT -> Color.parseColor("#F44336")         // Red
            StockStatus.TARGET_HIT -> Color.parseColor("#4CAF50")     // Green
            StockStatus.HISTORY -> Color.parseColor("#9C27B0")        // Purple
        }
    }

    private fun getStatusColorAndTextColor(status: StockStatus, buyPrice: Double, quote: Quote?): Pair<Int, Int> {
        return when (status) {
            StockStatus.NOT_TRIGGERED -> {
                // Blue background with white text
                Pair(Color.parseColor("#2196F3"), Color.WHITE)
            }
            StockStatus.ORDER_PLACED -> {
                // Orange background with white text (to indicate order is being placed)
                Pair(Color.parseColor("#FF9800"), Color.WHITE)
            }
            StockStatus.TRIGGERED -> {
                // Green, black, or red background depending on LTP vs buyPrice
                if (quote != null) {
                    when {
                        quote.ltp > buyPrice -> Pair(Color.parseColor("#4CAF50"), Color.WHITE) // Green
                        quote.ltp == buyPrice -> Pair(Color.BLACK, Color.WHITE)               // Black
                        else -> Pair(Color.parseColor("#F44336"), Color.WHITE)                // Red
                    }
                } else {
                    Pair(Color.parseColor("#2196F3"), Color.WHITE) // Default to blue if no quote
                }
            }
            StockStatus.SL_HIT -> {
                // Red background with white text
                Pair(Color.parseColor("#F44336"), Color.WHITE)
            }
            StockStatus.TARGET_HIT -> {
                // Green background with white text
                Pair(Color.parseColor("#4CAF50"), Color.WHITE)
            }
            StockStatus.HISTORY -> {
                // Gray background with black text
                Pair(Color.parseColor("#9E9E9E"), Color.BLACK)
            }
        }
    }

    private fun calculatePercentageDifference(stock: Stock, quote: Quote): String {
        return when (stock.status) {
            StockStatus.NOT_TRIGGERED -> {
                // Display price difference in percentage for LTP and BuyPrice
                val diffPercent = ((quote.ltp - stock.buyPrice) / stock.buyPrice) * 100
                val sign = if (diffPercent >= 0) "+" else ""
                "($sign${String.format("%.2f", diffPercent)}%)"
            }
            StockStatus.ORDER_PLACED -> {
                // Display current profit/loss percentage while waiting for order to fill
                val diffPercent = ((quote.ltp - stock.buyPrice) / stock.buyPrice) * 100
                val sign = if (diffPercent >= 0) "+" else ""
                "($sign${String.format("%.2f", diffPercent)}%)"
            }
            StockStatus.TRIGGERED -> {
                // Display current profit/loss percentage
                val profitLoss = ((quote.ltp - stock.buyPrice) / stock.buyPrice) * 100
                val sign = if (profitLoss >= 0) "+" else ""
                "($sign${String.format("%.2f", profitLoss)}%)"
            }
            StockStatus.TARGET_HIT -> {
                // Display target percentage (target and buy price)
                val targetPercent = ((stock.target - stock.buyPrice) / stock.buyPrice) * 100
                "(+${String.format("%.2f", targetPercent)}%)"
            }
            StockStatus.SL_HIT -> {
                // Display stop loss percentage (stoploss and buy price)
                val slPercent = ((stock.stopLoss - stock.buyPrice) / stock.buyPrice) * 100
                val sign = if (slPercent >= 0) "+" else ""
                "($sign${String.format("%.2f", slPercent)}%)"
            }
            StockStatus.HISTORY -> {
                // History stocks are archived, show difference from buy price
                val diffPercent = ((quote.ltp - stock.buyPrice) / stock.buyPrice) * 100
                val sign = if (diffPercent >= 0) "+" else ""
                "($sign${String.format("%.2f", diffPercent)}%)"
            }
        }
    }
}

