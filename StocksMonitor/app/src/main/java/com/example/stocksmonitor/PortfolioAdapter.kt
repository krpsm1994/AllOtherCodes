package com.example.stocksmonitor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color
import com.example.stocksmonitor.databinding.ItemHoldingBinding

class PortfolioAdapter(
    private var holdings: List<Holding> = emptyList(),
    private val onHoldingClick: (Holding) -> Unit = {}
) : RecyclerView.Adapter<PortfolioAdapter.HoldingViewHolder>() {
    
    inner class HoldingViewHolder(private val binding: ItemHoldingBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(holding: Holding) {
            binding.apply {
                // Symbol and quantity
                symbolText.text = holding.symbol
                quantityText.text = "Qty: ${holding.qty}"
                
                // Average price and current LTP
                priceText.text = "Avg: ₹${String.format("%.2f", holding.avgPrice)}"
                ltpText.text = "LTP: ₹${String.format("%.2f", holding.ltp)}"
                
                // Current value
                valueText.text = "Value: ₹${String.format("%.2f", holding.currentValue)}"
                
                // P&L in rupees
                pnlText.text = "P&L: ₹${String.format("%.2f", holding.pnl)}"
                
                // P&L percentage with color coding
                pnlPercentText.text = "${String.format("%.2f", holding.pnlPercent)}%"
                pnlPercentText.setTextColor(
                    if (holding.pnl >= 0) Color.GREEN else Color.RED
                )
                pnlText.setTextColor(
                    if (holding.pnl >= 0) Color.GREEN else Color.RED
                )
                
                // Source badge
                sourceText.text = holding.source.uppercase()
                sourceText.setBackgroundColor(
                    if (holding.source == "kite") Color.parseColor("#0066FF") else Color.parseColor("#FF6600")
                )
                sourceText.setTextColor(Color.WHITE)
                
                // Type badge
                typeText.text = holding.holdingType.uppercase()
                typeText.setBackgroundColor(
                    if (holding.holdingType == "long") Color.parseColor("#00AA00") else Color.parseColor("#CC0000")
                )
                typeText.setTextColor(Color.WHITE)
                
                root.setOnClickListener {
                    onHoldingClick(holding)
                }
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HoldingViewHolder {
        val binding = ItemHoldingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HoldingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: HoldingViewHolder, position: Int) {
        holder.bind(holdings[position])
    }
    
    override fun getItemCount(): Int = holdings.size
    
    fun updateHoldings(newHoldings: List<Holding>) {
        this.holdings = newHoldings
        notifyDataSetChanged()
    }
    
    fun updateHolding(holding: Holding) {
        val index = holdings.indexOfFirst { it.symbol == holding.symbol && it.source == holding.source }
        if (index >= 0) {
            val mutableList = holdings.toMutableList()
            mutableList[index] = holding
            this.holdings = mutableList
            notifyItemChanged(index)
        }
    }
}
