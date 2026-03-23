package com.example.stocksmonitor

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class OrderAdapter(context: Context, private val orders: List<Order>) :
    ArrayAdapter<Order>(context, R.layout.order_item, orders) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.order_item, parent, false)
        val order = orders[position]

        val symbolView = view.findViewById<TextView>(R.id.order_symbol)
        val statusView = view.findViewById<TextView>(R.id.order_status)
        val typeView = view.findViewById<TextView>(R.id.order_type)
        val quantityView = view.findViewById<TextView>(R.id.order_quantity)
        val priceView = view.findViewById<TextView>(R.id.order_price)
        val timestampView = view.findViewById<TextView>(R.id.order_timestamp)

        symbolView.text = order.tradingSymbol
        statusView.text = order.status

        // Color code status
        when (order.status.uppercase()) {
            "COMPLETE" -> statusView.setTextColor(Color.parseColor("#4CAF50")) // Green
            "REJECTED", "CANCELLED" -> statusView.setTextColor(Color.parseColor("#F44336")) // Red
            "PENDING", "OPEN", "TRIGGER PENDING" -> statusView.setTextColor(Color.parseColor("#FF9800")) // Orange
            else -> statusView.setTextColor(Color.parseColor("#666666")) // Gray
        }

        typeView.text = "${order.transactionType} ${order.orderType}"
        quantityView.text = "Qty: ${order.quantity}"
        
        val displayPrice = if (order.averagePrice > 0) order.averagePrice else order.price
        priceView.text = "₹%.2f".format(displayPrice)
        
        timestampView.text = order.orderTimestamp

        return view
    }
}
