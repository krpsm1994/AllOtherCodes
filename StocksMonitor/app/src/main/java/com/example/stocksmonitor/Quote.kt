package com.example.stocksmonitor

data class Quote(
    val ltp: Double,
    val percentChange: Double,
    val lastPrice: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)
