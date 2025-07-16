package com.lended.h2opal.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            HydrationAlarmScheduler.scheduleRepeatingAlarm(context)
        }
    }
}
