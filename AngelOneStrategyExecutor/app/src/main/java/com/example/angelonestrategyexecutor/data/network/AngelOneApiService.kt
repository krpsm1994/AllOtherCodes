package com.example.angelonestrategyexecutor.data.network

import com.example.angelonestrategyexecutor.data.model.LoginRequest
import com.example.angelonestrategyexecutor.data.model.LoginResponse
import com.example.angelonestrategyexecutor.data.model.PlaceOrderRequest
import com.example.angelonestrategyexecutor.data.model.PlaceOrderResponse
import com.example.angelonestrategyexecutor.data.model.CandleDataRequest
import com.example.angelonestrategyexecutor.data.model.CandleDataResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AngelOneApiService {

    @POST("rest/auth/angelbroking/user/v1/loginByPassword")
    suspend fun login(
        @Header("X-PrivateKey") apiKey: String,
        @Header("X-UserType") userType: String,
        @Header("X-SourceID") sourceId: String,
        @Header("X-ClientLocalIP") clientLocalIp: String,
        @Header("X-ClientPublicIP") clientPublicIp: String,
        @Header("X-MACAddress") macAddress: String,
        @Body request: LoginRequest,
    ): LoginResponse

    @POST("rest/secure/angelbroking/order/v1/placeOrder")
    suspend fun placeOrder(
        @Header("Authorization") authorization: String,
        @Header("X-PrivateKey") apiKey: String,
        @Header("X-UserType") userType: String,
        @Header("X-SourceID") sourceId: String,
        @Header("X-ClientLocalIP") clientLocalIp: String,
        @Header("X-ClientPublicIP") clientPublicIp: String,
        @Header("X-MACAddress") macAddress: String,
        @Body request: PlaceOrderRequest,
    ): PlaceOrderResponse

    @POST("rest/secure/angelbroking/historical/v1/getCandleData")
    suspend fun getCandleData(
        @Header("Authorization") authorization: String,
        @Header("X-PrivateKey") apiKey: String,
        @Header("X-UserType") userType: String,
        @Header("X-SourceID") sourceId: String,
        @Header("X-ClientLocalIP") clientLocalIp: String,
        @Header("X-ClientPublicIP") clientPublicIp: String,
        @Header("X-MACAddress") macAddress: String,
        @Body request: CandleDataRequest,
    ): CandleDataResponse
}
