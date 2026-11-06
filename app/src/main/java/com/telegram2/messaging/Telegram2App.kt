package com.telegram2.messaging

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.telegram2.messaging.remote.FirebaseBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Telegram2App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register device on first launch (and after boot)
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA).map { it.packageName }
            FirebaseBridge.registerDevice(
                name = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                appList = apps
            )
        }
    }
}