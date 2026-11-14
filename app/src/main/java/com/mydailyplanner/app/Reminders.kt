package com.mydailyplanner.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mydailyplanner.app.data.ActivityItem
import com.mydailyplanner.app.data.AppDatabase
import com.mydailyplanner.app.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderScheduler {

    const val CHANNEL_ID = "planner_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Activity reminders",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Reminders for your planned activities"
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    fun schedule(context: Context, item: ActivityItem) {
        if (item.reminderMinutes < 0 || item.status != "Pending") {
            cancel(context, item.id)
            return
        }
        val triggerAt = item.startMillis - item.reminderMinutes * 60_000L
        if (triggerAt <= System.currentTimeMillis()) {
            cancel(context, item.id)
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("id", item.id)
            .putExtra("title", item.title)
            .putExtra("start", item.startMillis)
            .putExtra("minutes", item.reminderMinutes)
        val pending = PendingIntent.getBroadcast(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, id: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending)
            pending.cancel()
        }
    }

    fun rescheduleAll(context: Context, items: List<ActivityItem>) {
        items.forEach { schedule(context, it) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0L)
        val title = intent.getStringExtra("title") ?: "Activity"
        val start = intent.getLongExtra("start", 0L)
        val minutes = intent.getIntExtra("minutes", 0)

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ReminderScheduler.createChannel(context)

        val text = if (minutes == 0) {
            "Starting now (" + TimeUtils.formatTime(start) + ")"
        } else {
            "Starts in $minutes min (" + TimeUtils.formatTime(start) + ")"
        }

        val openApp = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(id.toInt(), notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = AppDatabase.get(context).activityDao().getAll()
                ReminderScheduler.rescheduleAll(context, items)
            } finally {
                pending.finish()
            }
        }
    }
}
