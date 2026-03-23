package com.example.stocksmonitor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class AllStocksAdapter(
    private val context: Context,
    private val instruments: List<Instrument>
) : BaseAdapter() {

    private var filteredInstruments = instruments.toMutableList()

    fun updateList(newList: List<Instrument>) {
        filteredInstruments = newList.toMutableList()
        notifyDataSetChanged()
    }

    override fun getCount(): Int = filteredInstruments.size

    override fun getItem(position: Int): Any = filteredInstruments[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val instrument = filteredInstruments[position]
        
        val view = if (convertView == null) {
            LayoutInflater.from(context).inflate(R.layout.item_all_stock, parent, false)
        } else {
            convertView
        }

        // Find views for table row
        val tokenView = view.findViewById<TextView>(R.id.stock_token)
        val symbolView = view.findViewById<TextView>(R.id.stock_symbol)
        val nameView = view.findViewById<TextView>(R.id.stock_name)
        val exchangeView = view.findViewById<TextView>(R.id.stock_exchange)
        val segmentView = view.findViewById<TextView>(R.id.stock_segment)
        val instrumentTypeView = view.findViewById<TextView>(R.id.stock_instrument_type)
        val lotSizeView = view.findViewById<TextView>(R.id.stock_lot_size)

        // Set text values
        tokenView?.text = instrument.instrumentToken
        symbolView?.text = instrument.tradingSymbol
        nameView?.text = instrument.name
        exchangeView?.text = instrument.exchange
        segmentView?.text = instrument.segment
        instrumentTypeView?.text = instrument.instrumentType
        lotSizeView?.text = instrument.lotSize

        return view
    }
}
