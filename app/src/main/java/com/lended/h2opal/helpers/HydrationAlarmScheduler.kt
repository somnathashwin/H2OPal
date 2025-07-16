package com.lended.h2opal.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lended.h2opal.receivers.HydrationAlarmReceiver
import java.util.*

object HydrationAlarmScheduler {

    fun scheduleRepeatingAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HydrationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val interval = 60 * 60 * 1000L // 1 hour

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + interval,
            interval,
            pendingIntent
        )
    }
}
