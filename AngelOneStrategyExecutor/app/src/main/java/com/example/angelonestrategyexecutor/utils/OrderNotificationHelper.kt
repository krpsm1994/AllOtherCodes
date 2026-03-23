package com.example.angelonestrategyexecutor.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.angelonestrategyexecutor.MainActivity
import com.example.angelonestrategyexecutor.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helper for posting order-status-change notifications.
 *
 * Usage (from a coroutine or composable with context):
 *   OrderNotificationHelper.notify(context, "RELIANCE", "Order Closed", "Stoploss hit @ ₹120.50")
 */
object OrderNotificationHelper {

    private const val CHANNEL_ID = "order_status_channel"
    private const val CHANNEL_NAME = "Order Status"

    /** Auto-incrementing ID so each notification appears separately. */
    private val notifIdCounter = AtomicInteger(2000)

    fun notify(
        context: Context,
        symbol: String,
        title: String,
        message: String,
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel once (no-op if already exists)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications for buy/sell order status changes"
            }
            manager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("[$symbol] $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(notifIdCounter.getAndIncrement(), notification)
    }
}
