package com.example.stocksmonitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StocksTabAdapter(
    private val activeStocks: List<Stock>,
    private val historyStocks: List<Stock>,
    private val onStockClick: (Stock) -> Unit,
    private val onOrdersTabSelected: () -> Unit,
    private val onOrderClick: ((Order) -> Unit)? = null,
    private val quotesCache: Map<String, Quote>
) : RecyclerView.Adapter<StocksTabAdapter.TabPageViewHolder>() {

    private var ordersTabViewHolder: TabPageViewHolder? = null
    private var cachedOrders: List<Order>? = null
    private var cachedError: String? = null
    
    fun updateOrders(newOrders: List<Order>, error: String?) {
        cachedOrders = newOrders
        cachedError = error
        ordersTabViewHolder?.let { holder ->
            updateOrdersTabView(holder, newOrders, error)
        }
    }

    class TabPageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val listView: ListView = view.findViewById(R.id.list_view)
        val emptyView: TextView = view.findViewById(R.id.empty_view)
    }

    override fun getItemCount(): Int = 3 // 3 tabs: Stocks, Orders, History

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabPageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.tab_page_layout, parent, false)
        return TabPageViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabPageViewHolder, position: Int) {
        when (position) {
            0 -> setupStocksTab(holder)
            1 -> {
                ordersTabViewHolder = holder
                setupOrdersTab(holder)
            }
            2 -> setupHistoryTab(holder)
        }
    }

    private fun setupStocksTab(holder: TabPageViewHolder) {
        if (activeStocks.isEmpty()) {
            holder.emptyView.visibility = View.VISIBLE
            holder.emptyView.text = "No stocks added yet. Click + to add."
            holder.listView.visibility = View.GONE
        } else {
            holder.emptyView.visibility = View.GONE
            holder.listView.visibility = View.VISIBLE
            
            val adapter = StockAdapter(holder.itemView.context, activeStocks, quotesCache)
            holder.listView.adapter = adapter
            
            holder.listView.setOnItemClickListener { _, _, position, _ ->
                onStockClick(activeStocks[position])
            }
        }
    }

    private fun setupOrdersTab(holder: TabPageViewHolder) {
        // Check if we have cached orders to display
        if (cachedOrders != null || cachedError != null) {
            // Show cached data
            updateOrdersTabView(holder, cachedOrders ?: emptyList(), cachedError)
        } else {
            // No data yet, show loading state
            holder.emptyView.visibility = View.VISIBLE
            holder.emptyView.text = "Loading orders..."
            holder.listView.visibility = View.GONE
        }
    }
    
    private fun updateOrdersTabView(holder: TabPageViewHolder, orders: List<Order>, error: String?) {
        if (error != null) {
            holder.emptyView.visibility = View.VISIBLE
            holder.emptyView.text = "Error loading orders: $error"
            holder.listView.visibility = View.GONE
        } else if (orders.isEmpty()) {
            holder.emptyView.visibility = View.VISIBLE
            holder.emptyView.text = "No orders found"
            holder.listView.visibility = View.GONE
        } else {
            holder.emptyView.visibility = View.GONE
            holder.listView.visibility = View.VISIBLE
            
            val adapter = OrderAdapter(holder.itemView.context, orders)
            holder.listView.adapter = adapter
            
            // Add click listener for orders
            holder.listView.setOnItemClickListener { _, _, position, _ ->
                onOrderClick?.invoke(orders[position])
            }
        }
    }

    private fun setupHistoryTab(holder: TabPageViewHolder) {
        if (historyStocks.isEmpty()) {
            holder.emptyView.visibility = View.VISIBLE
            holder.emptyView.text = "No stocks in history"
            holder.listView.visibility = View.GONE
        } else {
            holder.emptyView.visibility = View.GONE
            holder.listView.visibility = View.VISIBLE
            
            val adapter = StockAdapter(holder.itemView.context, historyStocks, quotesCache)
            holder.listView.adapter = adapter
            
            holder.listView.setOnItemClickListener { _, _, position, _ ->
                onStockClick(historyStocks[position])
            }
        }
    }
}
