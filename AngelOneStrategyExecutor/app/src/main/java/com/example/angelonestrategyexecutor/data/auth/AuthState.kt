package com.example.angelonestrategyexecutor.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton that holds authentication tokens shared across the app.
 * Tokens are persisted to SharedPreferences and valid for 24 hours.
 * Populated after successful login; consumed by WebSocket service and background tasks.
 */
object AuthState {

    private const val TAG = "AuthState"
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_JWT_TOKEN = "jwt_token"
    private const val KEY_FEED_TOKEN = "feed_token"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_CLIENT_CODE = "client_code"
    private const val KEY_LOGIN_TIME = "login_time_millis"
    private const val KEY_LOGIN_TIME_DISPLAY = "login_time_display"
    private const val VALIDITY_MILLIS = 24 * 60 * 60 * 1000L  // 24 hours

    data class Credentials(
        val jwtToken: String,
        val feedToken: String,
        val apiKey: String,
        val clientCode: String,
        val loginTimeMillis: Long = System.currentTimeMillis(),
        val loginTimeDisplay: String = "",
    ) {
        /** How many milliseconds remain before this session expires. */
        val remainingMillis: Long get() = (loginTimeMillis + VALIDITY_MILLIS) - System.currentTimeMillis()

        /** Whether the session has expired (> 24 hours old). */
        val isExpired: Boolean get() = remainingMillis <= 0
    }

    private val _credentials = MutableStateFlow<Credentials?>(null)
    val credentials: StateFlow<Credentials?> = _credentials.asStateFlow()

    val isLoggedIn: Boolean get() = _credentials.value?.let { !it.isExpired } ?: false

    private var prefs: SharedPreferences? = null

    /**
     * Initialize with application context. Call once from Application or MainActivity.
     * Loads any previously saved credentials (if still valid).
     */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    fun update(
        jwtToken: String,
        feedToken: String,
        apiKey: String,
        clientCode: String,
        loginTimeDisplay: String = "",
    ) {
        val now = System.currentTimeMillis()
        val creds = Credentials(
            jwtToken = jwtToken,
            feedToken = feedToken,
            apiKey = apiKey,
            clientCode = clientCode,
            loginTimeMillis = now,
            loginTimeDisplay = loginTimeDisplay,
        )
        _credentials.value = creds
        saveToPrefs(creds)
        Log.d(TAG, "Credentials saved (valid for 24h)")
    }

    fun clear() {
        _credentials.value = null
        prefs?.edit()?.clear()?.apply()
        Log.d(TAG, "Credentials cleared")
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun saveToPrefs(creds: Credentials) {
        prefs?.edit()?.apply {
            putString(KEY_JWT_TOKEN, creds.jwtToken)
            putString(KEY_FEED_TOKEN, creds.feedToken)
            putString(KEY_API_KEY, creds.apiKey)
            putString(KEY_CLIENT_CODE, creds.clientCode)
            putLong(KEY_LOGIN_TIME, creds.loginTimeMillis)
            putString(KEY_LOGIN_TIME_DISPLAY, creds.loginTimeDisplay)
            apply()
        }
    }

    private fun loadFromPrefs() {
        val p = prefs ?: return
        val jwt = p.getString(KEY_JWT_TOKEN, null) ?: return
        val feed = p.getString(KEY_FEED_TOKEN, null) ?: return
        val apiKey = p.getString(KEY_API_KEY, null) ?: return
        val clientCode = p.getString(KEY_CLIENT_CODE, null) ?: return
        val loginTime = p.getLong(KEY_LOGIN_TIME, 0L)
        val loginDisplay = p.getString(KEY_LOGIN_TIME_DISPLAY, "") ?: ""

        val creds = Credentials(
            jwtToken = jwt,
            feedToken = feed,
            apiKey = apiKey,
            clientCode = clientCode,
            loginTimeMillis = loginTime,
            loginTimeDisplay = loginDisplay,
        )

        if (creds.isExpired) {
            Log.d(TAG, "Saved session expired, clearing")
            clear()
        } else {
            _credentials.value = creds
            Log.d(TAG, "Restored session (${creds.remainingMillis / 60_000}min remaining)")
        }
    }
}
