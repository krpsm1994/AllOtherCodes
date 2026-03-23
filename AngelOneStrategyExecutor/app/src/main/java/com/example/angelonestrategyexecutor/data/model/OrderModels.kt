package com.example.angelonestrategyexecutor.data.model

import com.google.gson.annotations.SerializedName

data class PlaceOrderRequest(
    @SerializedName("variety") val variety: String = "NORMAL",
    @SerializedName("tradingsymbol") val tradingSymbol: String,
    @SerializedName("symboltoken") val symbolToken: String,
    @SerializedName("transactiontype") val transactionType: String = "BUY",
    @SerializedName("exchange") val exchange: String = "NFO",
    @SerializedName("ordertype") val orderType: String = "LIMIT",
    @SerializedName("producttype") val productType: String = "CARRYFORWARD",
    @SerializedName("duration") val duration: String = "DAY",
    @SerializedName("price") val price: String,
    @SerializedName("squareoff") val squareOff: String = "0",
    @SerializedName("stoploss") val stoploss: String = "0",
    @SerializedName("quantity") val quantity: String,
    @SerializedName("disclosedquantity") val disclosedQuantity: String = "0",
)

data class PlaceOrderResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("errorcode") val errorCode: String,
    @SerializedName("data") val data: PlaceOrderData?,
)

data class PlaceOrderData(
    @SerializedName("script") val script: String?,
    @SerializedName("orderid") val orderId: String?,
    @SerializedName("uniqueorderid") val uniqueOrderId: String?,
)
