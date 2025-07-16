package com.lended.h2opal.receivers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lended.h2opal.R
import kotlin.random.Random

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        // Check for notification permission on Android 13 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e("ReminderReceiver", "Notification permission not granted")
                return
            }
        }

        val hydration = Random.nextInt(0, 101)
        val quote = getQuoteBasedOnHydrationLevel(hydration)
        val hydrationLevel = "Hydration Level: ${hydration}%"
        val time = getCurrentTime(context)

        // Post notification
        postHydrationNotification(context, quote, hydrationLevel)

        // Save reminder
        saveReminder(context, quote, hydrationLevel, time)
    }

    private fun postHydrationNotification(context: Context, quote: String, level: String) {
        // Create notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "hydration_channel"
            val channelName = "Hydration Reminder"
            val channelDescription = "Channel for hydration notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, "hydration_channel")
            .setSmallIcon(R.drawable.happy_pal) // Replace with your notification icon
            .setContentTitle("Hydration Reminder")
            .setContentText("$quote\n$level")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$quote\n$level"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            notify(Random.nextInt(), builder.build())
        }
    }

    private fun saveReminder(context: Context, quote: String, level: String, time: String) {
        val sharedPreferences = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val json = sharedPreferences.getString("reminder_list_json", null)
        val type = object : com.google.gson.reflect.TypeToken<MutableList<com.lended.h2opal.models.RemainderModel>>() {}.type
        val reminderList: MutableList<com.lended.h2opal.models.RemainderModel> = if (!json.isNullOrEmpty()) {
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
        reminderList.add(0, com.lended.h2opal.models.RemainderModel(quote, level, time))
        sharedPreferences.edit().putString("reminder_list_json", gson.toJson(reminderList)).apply()
    }

    private fun getCurrentTime(context: Context): String {
        val currentTime = System.currentTimeMillis()
        val dateFormat = android.text.format.DateFormat.getTimeFormat(context)
        return dateFormat.format(currentTime)
    }

    private fun getQuoteBasedOnHydrationLevel(hydrationLevel: Int): String {
        return when {
            hydrationLevel >= 90 -> "Excellent! Keep it up 💧"
            hydrationLevel >= 70 -> "Doing good, but don’t slack!"
            hydrationLevel >= 50 -> "Time to drink some water!"
            hydrationLevel >= 30 -> "You’re getting dehydrated!"
            else -> "Danger! Hydrate immediately! 🚨"
        }
    }
}
