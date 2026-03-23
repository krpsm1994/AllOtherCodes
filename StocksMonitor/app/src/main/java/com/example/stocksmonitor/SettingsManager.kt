package com.example.stocksmonitor

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("StocksMonitorSettings", Context.MODE_PRIVATE)
    
    companion object {
        // High Priority Settings
        const val KEY_ENABLE_AUTO_ORDERS = "enable_auto_orders"
        const val KEY_ORDER_POLLING_INTERVAL = "order_polling_interval"
        const val KEY_DEBUG_LOGS_ENABLED = "debug_logs_enabled"
        const val KEY_BROKERAGE_CHARGES = "brokerage_charges"
        const val KEY_DEFAULT_QUANTITY = "default_quantity"
        
        // Medium Priority Settings - Credentials
        const val KEY_KITE_API_KEY = "kite_api_key_custom"
        const val KEY_KITE_SECRET_KEY = "kite_secret_key_custom"
        const val KEY_ANGEL_API_KEY = "angel_api_key_custom"
        const val KEY_ANGEL_CLIENT_IP = "angel_client_ip"
        const val KEY_ANGEL_PUBLIC_IP = "angel_public_ip"
        const val KEY_ANGEL_MAC_ADDRESS = "angel_mac_address"
        
        // Medium Priority Settings - Notifications
        const val KEY_NOTIFY_ORDER_PLACED = "notify_order_placed"
        const val KEY_NOTIFY_ORDER_FILLED = "notify_order_filled"
        const val KEY_NOTIFY_SL_HIT = "notify_sl_hit"
        const val KEY_NOTIFY_TARGET_HIT = "notify_target_hit"
        const val KEY_ENABLE_SOUND_ALERTS = "enable_sound_alerts"
        const val KEY_ENABLE_VIBRATION = "enable_vibration"
        
        // Medium Priority Settings - UI
        const val KEY_THEME = "theme_mode"  // "light", "dark", "system"
        const val KEY_STOCK_SORT_ORDER = "stock_sort_order"  // "symbol", "price", "percentage", "status", "date"
        const val KEY_DEFAULT_TAB = "default_tab"  // 0=stocks, 1=history, 2=orders
        const val KEY_SHOW_PERCENTAGE = "show_percentage"
        const val KEY_COMPACT_VIEW = "compact_view"
        
        // Medium Priority Settings - Login Reminder
        const val KEY_ENABLE_LOGIN_REMINDER = "enable_login_reminder"
        const val KEY_LOGIN_REMINDER_HOUR = "login_reminder_hour"
        const val KEY_LOGIN_REMINDER_MINUTE = "login_reminder_minute"
        const val KEY_LOGIN_REMINDER_DAYS = "login_reminder_days"  // JSON array
        
        // Medium Priority Settings - Data
        const val KEY_AUTO_REFRESH_INSTRUMENTS = "auto_refresh_instruments"
        const val KEY_INSTRUMENTS_REFRESH_INTERVAL = "instruments_refresh_interval"  // "daily", "weekly", "manual"
        const val KEY_MAX_LOG_ENTRIES = "max_log_entries"
        
        // Medium Priority Settings - Background Service
        const val KEY_ENABLE_BACKGROUND_SERVICE = "enable_background_service"
        const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        
        // Medium Priority Settings - Risk Management
        const val KEY_MAX_DAILY_ORDERS = "max_daily_orders"
        const val KEY_MAX_INVESTMENT_PER_STOCK = "max_investment_per_stock"
        const val KEY_MAX_TOTAL_INVESTMENT = "max_total_investment"
        const val KEY_REQUIRE_ORDER_CONFIRMATION = "require_order_confirmation"
        const val KEY_AUTO_LOGOUT_HOURS = "auto_logout_hours"
        
        // Default values
        private const val DEFAULT_ENABLE_AUTO_ORDERS = true
        private const val DEFAULT_ORDER_POLLING_INTERVAL = 20  // seconds
        private const val DEFAULT_DEBUG_LOGS_ENABLED = true
        private const val DEFAULT_BROKERAGE_CHARGES = 20.0
        private const val DEFAULT_QUANTITY = 1
        
        private const val DEFAULT_NOTIFY_ORDER_PLACED = true
        private const val DEFAULT_NOTIFY_ORDER_FILLED = true
        private const val DEFAULT_NOTIFY_SL_HIT = true
        private const val DEFAULT_NOTIFY_TARGET_HIT = true
        private const val DEFAULT_SOUND_ALERTS = true
        private const val DEFAULT_VIBRATION = true
        
        private const val DEFAULT_THEME = "system"
        private const val DEFAULT_SORT_ORDER = "symbol"
        private const val DEFAULT_TAB = 0
        private const val DEFAULT_SHOW_PERCENTAGE = true
        private const val DEFAULT_COMPACT_VIEW = false
        
        private const val DEFAULT_LOGIN_REMINDER_ENABLED = true
        private const val DEFAULT_LOGIN_REMINDER_HOUR = 8
        private const val DEFAULT_LOGIN_REMINDER_MINUTE = 30
        
        private const val DEFAULT_AUTO_REFRESH_INSTRUMENTS = true
        private const val DEFAULT_REFRESH_INTERVAL = "daily"
        private const val DEFAULT_MAX_LOG_ENTRIES = 1000
        
        private const val DEFAULT_BACKGROUND_SERVICE = true
        private const val DEFAULT_KEEP_SCREEN_AWAKE = false
        
        private const val DEFAULT_MAX_DAILY_ORDERS = 100
        private const val DEFAULT_MAX_INVESTMENT_PER_STOCK = 100000.0
        private const val DEFAULT_MAX_TOTAL_INVESTMENT = 500000.0
        private const val DEFAULT_REQUIRE_CONFIRMATION = true
        private const val DEFAULT_AUTO_LOGOUT_HOURS = 0  // 0 means disabled
    }
    
    // High Priority - Order Management
    fun isAutoOrdersEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_AUTO_ORDERS, DEFAULT_ENABLE_AUTO_ORDERS)
    fun setAutoOrdersEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLE_AUTO_ORDERS, enabled).apply()
    
    fun getOrderPollingInterval(): Int = prefs.getInt(KEY_ORDER_POLLING_INTERVAL, DEFAULT_ORDER_POLLING_INTERVAL)
    fun setOrderPollingInterval(intervalSeconds: Int) = prefs.edit().putInt(KEY_ORDER_POLLING_INTERVAL, intervalSeconds).apply()
    
    fun isDebugLogsEnabled(): Boolean = prefs.getBoolean(KEY_DEBUG_LOGS_ENABLED, DEFAULT_DEBUG_LOGS_ENABLED)
    fun setDebugLogsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_DEBUG_LOGS_ENABLED, enabled).apply()
    
    fun getBrokerageCharges(): Double = prefs.getFloat(KEY_BROKERAGE_CHARGES, DEFAULT_BROKERAGE_CHARGES.toFloat()).toDouble()
    fun setBrokerageCharges(charges: Double) = prefs.edit().putFloat(KEY_BROKERAGE_CHARGES, charges.toFloat()).apply()
    
    fun getDefaultQuantity(): Int = prefs.getInt(KEY_DEFAULT_QUANTITY, DEFAULT_QUANTITY)
    fun setDefaultQuantity(quantity: Int) = prefs.edit().putInt(KEY_DEFAULT_QUANTITY, quantity).apply()
    
    // Credential Management
    fun getKiteApiKey(): String? = prefs.getString(KEY_KITE_API_KEY, null)
    fun setKiteApiKey(apiKey: String) = prefs.edit().putString(KEY_KITE_API_KEY, apiKey).apply()
    
    fun getKiteSecretKey(): String? = prefs.getString(KEY_KITE_SECRET_KEY, null)
    fun setKiteSecretKey(secretKey: String) = prefs.edit().putString(KEY_KITE_SECRET_KEY, secretKey).apply()
    
    fun getAngelApiKey(): String? = prefs.getString(KEY_ANGEL_API_KEY, null)
    fun setAngelApiKey(apiKey: String) = prefs.edit().putString(KEY_ANGEL_API_KEY, apiKey).apply()
    
    fun getAngelClientIp(): String = prefs.getString(KEY_ANGEL_CLIENT_IP, "192.168.1.1") ?: "192.168.1.1"
    fun setAngelClientIp(ip: String) = prefs.edit().putString(KEY_ANGEL_CLIENT_IP, ip).apply()
    
    fun getAngelPublicIp(): String = prefs.getString(KEY_ANGEL_PUBLIC_IP, "") ?: ""
    fun setAngelPublicIp(ip: String) = prefs.edit().putString(KEY_ANGEL_PUBLIC_IP, ip).apply()
    
    fun getAngelMacAddress(): String = prefs.getString(KEY_ANGEL_MAC_ADDRESS, "00:00:00:00:00:00") ?: "00:00:00:00:00:00"
    fun setAngelMacAddress(mac: String) = prefs.edit().putString(KEY_ANGEL_MAC_ADDRESS, mac).apply()
    
    // Notification Preferences
    fun isNotifyOrderPlaced(): Boolean = prefs.getBoolean(KEY_NOTIFY_ORDER_PLACED, DEFAULT_NOTIFY_ORDER_PLACED)
    fun setNotifyOrderPlaced(notify: Boolean) = prefs.edit().putBoolean(KEY_NOTIFY_ORDER_PLACED, notify).apply()
    
    fun isNotifyOrderFilled(): Boolean = prefs.getBoolean(KEY_NOTIFY_ORDER_FILLED, DEFAULT_NOTIFY_ORDER_FILLED)
    fun setNotifyOrderFilled(notify: Boolean) = prefs.edit().putBoolean(KEY_NOTIFY_ORDER_FILLED, notify).apply()
    
    fun isNotifySlHit(): Boolean = prefs.getBoolean(KEY_NOTIFY_SL_HIT, DEFAULT_NOTIFY_SL_HIT)
    fun setNotifySlHit(notify: Boolean) = prefs.edit().putBoolean(KEY_NOTIFY_SL_HIT, notify).apply()
    
    fun isNotifyTargetHit(): Boolean = prefs.getBoolean(KEY_NOTIFY_TARGET_HIT, DEFAULT_NOTIFY_TARGET_HIT)
    fun setNotifyTargetHit(notify: Boolean) = prefs.edit().putBoolean(KEY_NOTIFY_TARGET_HIT, notify).apply()
    
    fun isSoundAlertsEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_SOUND_ALERTS, DEFAULT_SOUND_ALERTS)
    fun setSoundAlertsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLE_SOUND_ALERTS, enabled).apply()
    
    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_VIBRATION, DEFAULT_VIBRATION)
    fun setVibrationEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLE_VIBRATION, enabled).apply()
    
    // UI Settings
    fun getThemeMode(): String = prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    fun setThemeMode(theme: String) = prefs.edit().putString(KEY_THEME, theme).apply()
    
    fun getStockSortOrder(): String = prefs.getString(KEY_STOCK_SORT_ORDER, DEFAULT_SORT_ORDER) ?: DEFAULT_SORT_ORDER
    fun setStockSortOrder(order: String) = prefs.edit().putString(KEY_STOCK_SORT_ORDER, order).apply()
    
    fun getDefaultTab(): Int = prefs.getInt(KEY_DEFAULT_TAB, DEFAULT_TAB)
    fun setDefaultTab(tab: Int) = prefs.edit().putInt(KEY_DEFAULT_TAB, tab).apply()
    
    fun shouldShowPercentage(): Boolean = prefs.getBoolean(KEY_SHOW_PERCENTAGE, DEFAULT_SHOW_PERCENTAGE)
    fun setShowPercentage(show: Boolean) = prefs.edit().putBoolean(KEY_SHOW_PERCENTAGE, show).apply()
    
    fun isCompactViewEnabled(): Boolean = prefs.getBoolean(KEY_COMPACT_VIEW, DEFAULT_COMPACT_VIEW)
    fun setCompactViewEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_COMPACT_VIEW, enabled).apply()
    
    // Login Reminder Settings
    fun isLoginReminderEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_LOGIN_REMINDER, DEFAULT_LOGIN_REMINDER_ENABLED)
    fun setLoginReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLE_LOGIN_REMINDER, enabled).apply()
    
    fun getLoginReminderHour(): Int = prefs.getInt(KEY_LOGIN_REMINDER_HOUR, DEFAULT_LOGIN_REMINDER_HOUR)
    fun setLoginReminderHour(hour: Int) = prefs.edit().putInt(KEY_LOGIN_REMINDER_HOUR, hour).apply()
    
    fun getLoginReminderMinute(): Int = prefs.getInt(KEY_LOGIN_REMINDER_MINUTE, DEFAULT_LOGIN_REMINDER_MINUTE)
    fun setLoginReminderMinute(minute: Int) = prefs.edit().putInt(KEY_LOGIN_REMINDER_MINUTE, minute).apply()
    
    // Daily Order Time Settings
    fun getDailyOrderHour(): Int = prefs.getInt("daily_order_hour", 9)
    fun setDailyOrderHour(hour: Int) = prefs.edit().putInt("daily_order_hour", hour).apply()
    
    fun getDailyOrderMinute(): Int = prefs.getInt("daily_order_minute", 0)
    fun setDailyOrderMinute(minute: Int) = prefs.edit().putInt("daily_order_minute", minute).apply()
    
    // Data & Storage Settings
    fun isAutoRefreshInstruments(): Boolean = prefs.getBoolean(KEY_AUTO_REFRESH_INSTRUMENTS, DEFAULT_AUTO_REFRESH_INSTRUMENTS)
    fun setAutoRefreshInstruments(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_REFRESH_INSTRUMENTS, enabled).apply()
    
    fun getInstrumentsRefreshInterval(): String = prefs.getString(KEY_INSTRUMENTS_REFRESH_INTERVAL, DEFAULT_REFRESH_INTERVAL) ?: DEFAULT_REFRESH_INTERVAL
    fun setInstrumentsRefreshInterval(interval: String) = prefs.edit().putString(KEY_INSTRUMENTS_REFRESH_INTERVAL, interval).apply()
    
    fun getMaxLogEntries(): Int = prefs.getInt(KEY_MAX_LOG_ENTRIES, DEFAULT_MAX_LOG_ENTRIES)
    fun setMaxLogEntries(max: Int) = prefs.edit().putInt(KEY_MAX_LOG_ENTRIES, max).apply()
    
    // Background Service Settings
    fun isBackgroundServiceEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_BACKGROUND_SERVICE, DEFAULT_BACKGROUND_SERVICE)
    fun setBackgroundServiceEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLE_BACKGROUND_SERVICE, enabled).apply()
    
    fun isKeepScreenAwakeEnabled(): Boolean = prefs.getBoolean(KEY_KEEP_SCREEN_AWAKE, DEFAULT_KEEP_SCREEN_AWAKE)
    fun setKeepScreenAwakeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_AWAKE, enabled).apply()
    
    // Risk Management Settings
    fun getMaxDailyOrders(): Int = prefs.getInt(KEY_MAX_DAILY_ORDERS, DEFAULT_MAX_DAILY_ORDERS)
    fun setMaxDailyOrders(max: Int) = prefs.edit().putInt(KEY_MAX_DAILY_ORDERS, max).apply()
    
    fun getMaxInvestmentPerStock(): Double = prefs.getFloat(KEY_MAX_INVESTMENT_PER_STOCK, DEFAULT_MAX_INVESTMENT_PER_STOCK.toFloat()).toDouble()
    fun setMaxInvestmentPerStock(max: Double) = prefs.edit().putFloat(KEY_MAX_INVESTMENT_PER_STOCK, max.toFloat()).apply()
    
    fun getMaxTotalInvestment(): Double = prefs.getFloat(KEY_MAX_TOTAL_INVESTMENT, DEFAULT_MAX_TOTAL_INVESTMENT.toFloat()).toDouble()
    fun setMaxTotalInvestment(max: Double) = prefs.edit().putFloat(KEY_MAX_TOTAL_INVESTMENT, max.toFloat()).apply()
    
    fun isOrderConfirmationRequired(): Boolean = prefs.getBoolean(KEY_REQUIRE_ORDER_CONFIRMATION, DEFAULT_REQUIRE_CONFIRMATION)
    fun setOrderConfirmationRequired(required: Boolean) = prefs.edit().putBoolean(KEY_REQUIRE_ORDER_CONFIRMATION, required).apply()
    
    fun getAutoLogoutHours(): Int = prefs.getInt(KEY_AUTO_LOGOUT_HOURS, DEFAULT_AUTO_LOGOUT_HOURS)
    fun setAutoLogoutHours(hours: Int) = prefs.edit().putInt(KEY_AUTO_LOGOUT_HOURS, hours).apply()
    
    // Clear all settings
    fun clearAllSettings() = prefs.edit().clear().apply()
}
