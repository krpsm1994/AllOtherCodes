package com.example.stocksmonitor

import android.content.Context
import java.io.File

class StockManager(private val context: Context) {
    private val stocksFileName = "stocks.txt"
    private val instrumentsFileName = "instruments.txt"
    private val allExchangesStocksFileName = "All_exchanges_stocks.txt"
    private val stocksLock = Any()
    
    fun saveStock(stock: Stock) {
        synchronized(stocksLock) {
            val file = File(context.filesDir, stocksFileName)
            file.appendText("${stock.toFileString()}\n")
        }
    }
    
    fun getAllStocks(): List<Stock> {
        synchronized(stocksLock) {
            val file = File(context.filesDir, stocksFileName)
            if (!file.exists()) {
                return emptyList()
            }
            
            return file.readLines()
                .mapNotNull { Stock.fromFileString(it) }
        }
    }
    
    fun deleteStock(stock: Stock) {
        synchronized(stocksLock) {
            val stocks = getAllStocks().filter { it != stock }
            saveAllStocks(stocks)
        }
    }
    
    fun updateStock(oldStock: Stock, newStock: Stock) {
        synchronized(stocksLock) {
            val stocks = getAllStocks().map { 
                if (it.instrument.instrumentToken == oldStock.instrument.instrumentToken && 
                    it.status == oldStock.status && 
                    it.orderId == oldStock.orderId) {
                    newStock
                } else {
                    it
                }
            }
            saveAllStocks(stocks)
        }
    }
    
    private fun saveAllStocks(stocks: List<Stock>) {
        val file = File(context.filesDir, stocksFileName)
        file.writeText(stocks.joinToString("\n") { it.toFileString() } + if (stocks.isNotEmpty()) "\n" else "")
    }
    
    fun saveInstruments(instruments: List<Instrument>) {
        val file = File(context.filesDir, instrumentsFileName)
        file.writeText("")
        instruments.forEach { instrument ->
            file.appendText("${instrument.toFileString()}\n")
        }
    }
    
    fun getAllInstruments(): List<Instrument> {
        val file = File(context.filesDir, instrumentsFileName)
        if (!file.exists()) {
            return emptyList()
        }
        
        return file.readLines()
            .mapNotNull { Instrument.fromCsvLine(it) }
    }
    
    fun getEquityInstruments(): List<Instrument> {
        return getAllInstruments().filter { 
            (it.exchange == "NSE" && it.instrumentType == "EQ") || it.exchange == "BSE"
        }
    }
    
    fun saveAllExchangesStocks(instruments: List<Instrument>) {
        val file = File(context.filesDir, allExchangesStocksFileName)
        file.writeText("")
        instruments.forEach { instrument ->
            file.appendText("${instrument.toFileString()}\n")
        }
    }
    
    fun getAllExchangesStocks(): List<Instrument> {
        val file = File(context.filesDir, allExchangesStocksFileName)
        if (!file.exists()) {
            return emptyList()
        }
        
        return file.readLines()
            .mapNotNull { Instrument.fromCsvLine(it) }
    }
}
