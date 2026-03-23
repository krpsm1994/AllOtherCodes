package com.example.angelonestrategyexecutor.data.model

import com.google.gson.annotations.SerializedName

data class Instrument(
    @SerializedName("token") val token: String,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("name") val name: String = "",
    @SerializedName("instrumenttype") val instrumentType: String,
    @SerializedName("lotsize") val lotSize: String,
    @SerializedName("exch_seg") val exchSeg: String = "",
)
