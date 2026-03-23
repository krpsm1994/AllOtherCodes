package com.example.angelonestrategyexecutor.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("clientcode") val clientCode: String,
    @SerializedName("password") val password: String,
    @SerializedName("totp") val totp: String,
)
