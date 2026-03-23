package com.example.stocksmonitor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class DailyOrderReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "DailyOrderReceiver"
        const val PREF_DAILY_ORDER_ENABLED = "daily_order_enabled"
        const val ORDER_TOKEN = "505685"
        const val ORDER_EXCHANGE = "BSE"
        const val ORDER_QUANTITY = 100
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        
        Logger.d(TAG, "========== DAILY ORDER ALARM TRIGGERED ==========")
        Logger.d(TAG, "Daily order alarm received at ${Calendar.getInstance().time}")
        
        // Check if daily order is enabled
        val sharedPrefs = context.getSharedPreferences("StocksMonitorPrefs", Context.MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean(PREF_DAILY_ORDER_ENABLED, false)
        
        if (!isEnabled) {
            Logger.w(TAG, "Daily order is disabled - skipping")
            return
        }
        
        // Check if today is weekday (Monday to Friday)
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        Logger.d(TAG, "Day of week: $dayOfWeek (1=Sunday, 2=Monday, 7=Saturday)")
        
        // Skip weekends
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            Logger.d(TAG, "Skipping order - weekend")
            NotificationHelper.showOrderNotification(
                context,
                "Daily Order Skipped",
                "Today is weekend - no order placed"
            )
            rescheduleNextAlarm(context)
            return
        }
        
        Logger.d(TAG, "Weekday confirmed - placing order for token $ORDER_TOKEN")
        
        // Get credentials from SharedPreferences
        val kiteApiKey = "7mov9qt27tpmk2ft" // KITE_API_KEY constant
        val kiteAccessToken = sharedPrefs.getString("kite_access_token", null)
        
        if (kiteAccessToken.isNullOrEmpty()) {
            Logger.w(TAG, "Kite access token not found - cannot place order")
            NotificationHelper.showOrderNotification(
                context,
                "Daily Order Failed",
                "Kite access token not found. Please login to Kite."
            )
            rescheduleNextAlarm(context)
            return
        }
        
        Logger.d(TAG, "Access token found: ${kiteAccessToken.take(20)}...")
        
        // Look up symbol from token
        val stockManager = StockManager(context)
        val allInstruments = stockManager.getAllExchangesStocks()
        Logger.d(TAG, "Total instruments loaded: ${allInstruments.size}")
        
        val instrument = allInstruments.find { 
            it.instrumentToken == ORDER_TOKEN && it.exchange == ORDER_EXCHANGE 
        }
        
        if (instrument == null) {
            Logger.w(TAG, "Instrument with token $ORDER_TOKEN not found in $ORDER_EXCHANGE")
            NotificationHelper.showOrderNotification(
                context,
                "Daily Order Failed",
                "Instrument token $ORDER_TOKEN not found in $ORDER_EXCHANGE exchange"
            )
            rescheduleNextAlarm(context)
            return
        }
        
        val symbol = instrument.tradingSymbol
        Logger.d(TAG, "Found symbol: $symbol for token $ORDER_TOKEN")
        
        // Place MARKET order
        KiteOrderManager.placeMarketOrder(
            kiteApiKey,
            kiteAccessToken,
            symbol,
            ORDER_EXCHANGE,
            ORDER_QUANTITY
        ) { orderId, error ->
            if (orderId != null) {
                Logger.d(TAG, "Order placed successfully! Order ID: $orderId")
                
                // Show notification
                NotificationHelper.showOrderNotification(
                    context,
                    "Daily Order Placed ✓",
                    "Order for $symbol ($ORDER_QUANTITY qty) placed successfully. Order ID: $orderId"
                )
            } else {
                Logger.e(TAG, "Failed to place order: $error")
                
                // Show error notification
                NotificationHelper.showOrderNotification(
                    context,
                    "Daily Order Failed ✗",
                    "Failed to place order for $symbol: $error"
                )
            }
        }
        
        // Schedule next alarm
        rescheduleNextAlarm(context)
        Logger.d(TAG, "========== DAILY ORDER PROCESSING COMPLETE ==========")
    }
    
    private fun rescheduleNextAlarm(context: Context) {
        // Get configured time from settings
        val settingsManager = SettingsManager(context)
        val orderHour = settingsManager.getDailyOrderHour()
        val orderMinute = settingsManager.getDailyOrderMinute()
        
        // Reschedule for next day at configured time
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, DailyOrderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001, // Unique request code for daily orders
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1) // Next day
            set(Calendar.HOUR_OF_DAY, orderHour)
            set(Calendar.MINUTE, orderMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
        
        Logger.d(TAG, "Next alarm scheduled for ${calendar.time}")
    }
}
