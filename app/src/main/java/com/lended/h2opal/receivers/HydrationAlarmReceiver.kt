package com.lended.h2opal.receivers


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lended.h2opal.helpers.HydrationManager

class HydrationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Use same key as MainActivity
        val prefs = context.getSharedPreferences("hydration_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("last_user_id", "default_user") ?: "default_user"
        HydrationManager.initialize(context, userId)
        HydrationManager.decayHydrationOverTime()
    }
}
