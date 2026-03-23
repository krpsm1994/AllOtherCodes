package com.example.angelonestrategyexecutor.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.angelonestrategyexecutor.MainActivity
import com.example.angelonestrategyexecutor.R
import java.util.Calendar

/**
 * BroadcastReceiver that fires at the configured time and shows a notification
 * reminding the user to login to the app.
 */
class LoginReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LoginReminder"
        const val CHANNEL_ID = "login_reminder_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, _intent: Intent) {
        // Ensure config is loaded (process may have been killed)
        com.example.angelonestrategyexecutor.data.config.AppConfig.init(context)

        // Only show on configured days
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val enabledDays = com.example.angelonestrategyexecutor.data.config.AppConfig.reminderDays.value
        if (today !in enabledDays) {
            Log.d(TAG, "Today ($today) not in enabled days $enabledDays — skipping notification")
            return
        }

        Log.d(TAG, "Alarm fired — showing login reminder notification")
        showNotification(context)

        // Re-schedule next alarm (exact alarms are one-shot)
        LoginAlarmScheduler.schedule(context)
    }

    private fun showNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (required for API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Login Reminder",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Daily reminder to login to AngelOne Strategy Executor"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap notification → open app
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AngelOne Strategy Executor")
            .setContentText("Market opens at 9:15 AM — Login now to start trading!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
