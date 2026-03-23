package com.example.angelonestrategyexecutor.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class CandleDataRequest(
    @SerializedName("exchange") val exchange: String,
    @SerializedName("symboltoken") val symbolToken: String,
    @SerializedName("interval") val interval: String,
    @SerializedName("fromdate") val fromDate: String,
    @SerializedName("todate") val toDate: String,
)

data class CandleDataResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("errorcode") val errorCode: String,
    @SerializedName("data") val data: List<List<JsonElement>>? = null,
)

data class CandleDataPoint(
    val timestamp: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)
