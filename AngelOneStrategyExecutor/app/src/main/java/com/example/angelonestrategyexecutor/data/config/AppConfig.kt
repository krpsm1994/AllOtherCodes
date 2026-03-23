package com.example.angelonestrategyexecutor.data.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton application configuration backed by SharedPreferences.
 * Must call [init] once (e.g. in Application.onCreate or MainActivity.onCreate).
 */
object AppConfig {

    const val WATCHLIST_SCAN_MODE_ONLY_0930 = "ONLY_0930"
    const val WATCHLIST_SCAN_MODE_EVERY_15_MIN = "EVERY_15_MIN"
    private const val LEGACY_WATCHLIST_SCAN_MODE_ONLY_0931 = "ONLY_0931"

    private const val PREFS_NAME = "app_config"

    // Keys
    private const val KEY_API_KEY = "api_key"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_PIN = "pin"
    private const val KEY_PLACE_ORDERS = "place_orders"
    private const val KEY_ALERTS = "alerts"
    private const val KEY_NUM_LOTS = "num_lots"
    private const val KEY_PRODUCT_TYPE = "product_type"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MINUTE = "reminder_minute"
    private const val KEY_REMINDER_DAYS = "reminder_days"  // comma-separated Calendar day ints
    private const val KEY_WATCHLIST_SCAN_MODE = "watchlist_scan_mode"

    // Defaults
    private const val DEFAULT_API_KEY = "fYy1t3Zh"
    private const val DEFAULT_USER_ID = "S812559"
    private const val DEFAULT_PIN = "8151"
    private const val DEFAULT_PLACE_ORDERS = true
    private const val DEFAULT_ALERTS = true
    private const val DEFAULT_NUM_LOTS = 1
    private const val DEFAULT_PRODUCT_TYPE = "CARRYFORWARD"
    private const val DEFAULT_REMINDER_HOUR = 8
    private const val DEFAULT_REMINDER_MINUTE = 50
    // Mon(2), Tue(3), Wed(4), Thu(5), Fri(6) — java.util.Calendar constants
    private const val DEFAULT_REMINDER_DAYS = "2,3,4,5,6"
    private const val DEFAULT_WATCHLIST_SCAN_MODE = WATCHLIST_SCAN_MODE_ONLY_0930

    private lateinit var prefs: SharedPreferences

    // ── Observable state (so Compose reacts to changes) ─────────────────

    private val _apiKey = MutableStateFlow(DEFAULT_API_KEY)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _userId = MutableStateFlow(DEFAULT_USER_ID)
    val userId: StateFlow<String> = _userId.asStateFlow()

    private val _pin = MutableStateFlow(DEFAULT_PIN)
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _placeOrders = MutableStateFlow(DEFAULT_PLACE_ORDERS)
    val placeOrders: StateFlow<Boolean> = _placeOrders.asStateFlow()

    private val _alerts = MutableStateFlow(DEFAULT_ALERTS)
    val alerts: StateFlow<Boolean> = _alerts.asStateFlow()

    private val _numLots = MutableStateFlow(DEFAULT_NUM_LOTS)
    val numLots: StateFlow<Int> = _numLots.asStateFlow()

    private val _productType = MutableStateFlow(DEFAULT_PRODUCT_TYPE)
    val productType: StateFlow<String> = _productType.asStateFlow()

    private val _reminderHour = MutableStateFlow(DEFAULT_REMINDER_HOUR)
    val reminderHour: StateFlow<Int> = _reminderHour.asStateFlow()

    private val _reminderMinute = MutableStateFlow(DEFAULT_REMINDER_MINUTE)
    val reminderMinute: StateFlow<Int> = _reminderMinute.asStateFlow()

    // Set of Calendar day-of-week ints (Sun=1, Mon=2, ..., Sat=7)
    private val _reminderDays = MutableStateFlow(setOf(2, 3, 4, 5, 6))
    val reminderDays: StateFlow<Set<Int>> = _reminderDays.asStateFlow()

    private val _watchListScanMode = MutableStateFlow(DEFAULT_WATCHLIST_SCAN_MODE)
    val watchListScanMode: StateFlow<String> = _watchListScanMode.asStateFlow()

    // ── Init from SharedPrefs ───────────────────────────────────────────

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _apiKey.value = prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
        _userId.value = prefs.getString(KEY_USER_ID, DEFAULT_USER_ID) ?: DEFAULT_USER_ID
        _pin.value = prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        _placeOrders.value = prefs.getBoolean(KEY_PLACE_ORDERS, DEFAULT_PLACE_ORDERS)
        _alerts.value = prefs.getBoolean(KEY_ALERTS, DEFAULT_ALERTS)
        _numLots.value = prefs.getInt(KEY_NUM_LOTS, DEFAULT_NUM_LOTS)
        _productType.value = prefs.getString(KEY_PRODUCT_TYPE, DEFAULT_PRODUCT_TYPE) ?: DEFAULT_PRODUCT_TYPE
        _reminderHour.value = prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR)
        _reminderMinute.value = prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
        _reminderDays.value = (prefs.getString(KEY_REMINDER_DAYS, DEFAULT_REMINDER_DAYS) ?: DEFAULT_REMINDER_DAYS)
            .split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        _watchListScanMode.value = normalizeWatchListScanMode(
            prefs.getString(KEY_WATCHLIST_SCAN_MODE, DEFAULT_WATCHLIST_SCAN_MODE)
                ?: DEFAULT_WATCHLIST_SCAN_MODE,
        )
    }

    // ── Setters (persist immediately) ───────────────────────────────────

    fun setApiKey(value: String) {
        _apiKey.value = value
        prefs.edit().putString(KEY_API_KEY, value).apply()
    }

    fun setUserId(value: String) {
        _userId.value = value
        prefs.edit().putString(KEY_USER_ID, value).apply()
    }

    fun setPin(value: String) {
        _pin.value = value
        prefs.edit().putString(KEY_PIN, value).apply()
    }

    fun setPlaceOrders(value: Boolean) {
        _placeOrders.value = value
        prefs.edit().putBoolean(KEY_PLACE_ORDERS, value).apply()
    }

    fun setAlerts(value: Boolean) {
        _alerts.value = value
        prefs.edit().putBoolean(KEY_ALERTS, value).apply()
    }

    fun setNumLots(value: Int) {
        _numLots.value = value.coerceAtLeast(1)
        prefs.edit().putInt(KEY_NUM_LOTS, _numLots.value).apply()
    }

    fun setProductType(value: String) {
        _productType.value = value
        prefs.edit().putString(KEY_PRODUCT_TYPE, value).apply()
    }

    fun setReminderHour(value: Int) {
        _reminderHour.value = value.coerceIn(0, 23)
        prefs.edit().putInt(KEY_REMINDER_HOUR, _reminderHour.value).apply()
    }

    fun setReminderMinute(value: Int) {
        _reminderMinute.value = value.coerceIn(0, 59)
        prefs.edit().putInt(KEY_REMINDER_MINUTE, _reminderMinute.value).apply()
    }

    fun setReminderDays(days: Set<Int>) {
        _reminderDays.value = days
        prefs.edit().putString(KEY_REMINDER_DAYS, days.joinToString(",")).apply()
    }

    fun setWatchListScanMode(mode: String) {
        val normalized = normalizeWatchListScanMode(mode)
        _watchListScanMode.value = normalized
        prefs.edit().putString(KEY_WATCHLIST_SCAN_MODE, normalized).apply()
    }

    private fun normalizeWatchListScanMode(mode: String): String {
        return when (mode) {
            WATCHLIST_SCAN_MODE_EVERY_15_MIN -> WATCHLIST_SCAN_MODE_EVERY_15_MIN
            WATCHLIST_SCAN_MODE_ONLY_0930, LEGACY_WATCHLIST_SCAN_MODE_ONLY_0931 -> WATCHLIST_SCAN_MODE_ONLY_0930
            else -> WATCHLIST_SCAN_MODE_ONLY_0930
        }
    }
}
