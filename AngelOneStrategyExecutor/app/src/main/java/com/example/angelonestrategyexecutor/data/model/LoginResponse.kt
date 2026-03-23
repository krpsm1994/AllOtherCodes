package com.example.angelonestrategyexecutor.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("errorcode") val errorCode: String,
    @SerializedName("data") val data: LoginData?,
)

data class LoginData(
    @SerializedName("jwtToken") val jwtToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("feedToken") val feedToken: String,
    @SerializedName("state") val state: String?,
)
