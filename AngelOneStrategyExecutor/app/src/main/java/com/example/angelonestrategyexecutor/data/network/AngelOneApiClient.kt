package com.example.angelonestrategyexecutor.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object AngelOneApiClient {

    private const val BASE_URL = "https://apiconnect.angelone.in/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val commonHeadersInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(commonHeadersInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Historical data can be large; use a no-logging client to avoid response payload logs.
    private val historicalOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(commonHeadersInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: AngelOneApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AngelOneApiService::class.java)
    }

    val historicalService: AngelOneApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(historicalOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AngelOneApiService::class.java)
    }
}
