package com.example.stocksmonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class LoginReminderReceiver : BroadcastReceiver() {
    
    companion object {
        const val CHANNEL_ID = "login_reminder_channel"
        const val NOTIFICATION_ID = 100
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("LoginReminderReceiver", "Alarm received!")
        
        // Check if today is a weekday (Monday to Sunday) - allow all days including Sunday
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        android.util.Log.d("LoginReminderReceiver", "Day of week: $dayOfWeek")
        
        if (dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY || dayOfWeek == Calendar.SATURDAY) {
            showLoginReminderNotification(context)
        } else {
            android.util.Log.d("LoginReminderReceiver", "Skipping notification - no matching day")
        }
    }
    
    private fun showLoginReminderNotification(context: Context) {
        android.util.Log.d("LoginReminderReceiver", "Showing notification...")
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Login Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily login reminder notifications"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
            android.util.Log.d("LoginReminderReceiver", "Notification channel created")
        }
        
        // Create intent to open MainActivity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time to Login!")
            .setContentText("Don't forget to login to Stocks Monitor")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
        android.util.Log.d("LoginReminderReceiver", "Notification sent with ID: $NOTIFICATION_ID")
    }
}
