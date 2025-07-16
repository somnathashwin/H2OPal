package com.lended.h2opal.helpers


import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.lended.h2opal.helpers.HydrationManager

class HydrationLogWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        // Retrieve userId from SharedPreferences (or another persistent store)
        val prefs = applicationContext.getSharedPreferences("hydration_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("last_user_id", "default_user") ?: "default_user"
        HydrationManager.initialize(applicationContext, userId)
        HydrationManager.logHydrationHistory()
        return Result.success()
    }
}
