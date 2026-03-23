package com.example.angelonestrategyexecutor.data.network

import com.example.angelonestrategyexecutor.data.model.Instrument
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

interface InstrumentsApiService {
    @GET("OpenAPI_File/files/OpenAPIScripMaster.json")
    suspend fun fetchScripMaster(): List<Instrument>
}

object InstrumentsApiClient {
    private const val BASE_URL = "https://margincalculator.angelbroking.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // large file ~30MB
        .build()

    val service: InstrumentsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InstrumentsApiService::class.java)
    }
}
