package com.example.angelonestrategyexecutor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.example.angelonestrategyexecutor.data.model.LoginRequest
import com.example.angelonestrategyexecutor.data.network.AngelOneApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(
        val jwtToken: String,
        val feedToken: String,
        val lastLoginTime: String,
    ) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // Restore UI state from saved session if still valid
        val saved = AuthState.credentials.value
        if (saved != null && !saved.isExpired) {
            _uiState.value = LoginUiState.Success(
                jwtToken = saved.jwtToken,
                feedToken = saved.feedToken,
                lastLoginTime = saved.loginTimeDisplay,
            )
        }
    }

    fun login(
        clientCode: String,
        pin: String,
        totp: String,
        apiKey: String,
    ) {
        if (clientCode.isBlank() || pin.isBlank() || totp.isBlank() || apiKey.isBlank()) {
            _uiState.value = LoginUiState.Error("All fields including API Key are required.")
            return
        }
        if (totp.length != 6) {
            _uiState.value = LoginUiState.Error("TOTP must be exactly 6 digits.")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val localIp = getLocalIpAddress()
                val response = AngelOneApiClient.service.login(
                    apiKey = apiKey,
                    userType = "USER",
                    sourceId = "WEB",
                    clientLocalIp = localIp,
                    clientPublicIp = localIp,
                    macAddress = "00:00:00:00:00:00",
                    request = LoginRequest(
                        clientCode = clientCode,
                        password = pin,
                        totp = totp,
                    ),
                )

                if (response.status && response.data != null) {
                    val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                    val loginTimeStr = sdf.format(Date())

                    // Persist tokens for WebSocket and other services (saved to SharedPrefs, valid 24h)
                    AuthState.update(
                        jwtToken = response.data.jwtToken,
                        feedToken = response.data.feedToken,
                        apiKey = apiKey,
                        clientCode = clientCode,
                        loginTimeDisplay = loginTimeStr,
                    )

                    _uiState.value = LoginUiState.Success(
                        jwtToken = response.data.jwtToken,
                        feedToken = response.data.feedToken,
                        lastLoginTime = loginTimeStr,
                    )
                } else {
                    val msg = response.message.ifBlank { "Login failed (${response.errorCode})" }
                    _uiState.value = LoginUiState.Error(msg)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Network error. Check your connection.")
            }
        }
    }

    fun resetError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }
}
