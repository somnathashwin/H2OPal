package com.lended.h2opal.helpers

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lended.h2opal.R
import kotlin.random.Random

object NotificationUtils {
    fun postHydrationNotification(context: Context, quote: String, level: String, mood: String, iconRes: Int) {
        val builder = NotificationCompat.Builder(context, "hydration_channel")
            .setSmallIcon(iconRes)
            .setContentTitle("Hydration Reminder\nI'm $mood")
            .setContentText("$quote\n$level")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$quote\n$level"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            notify(Random.nextInt(), builder.build())
        }
    }
}