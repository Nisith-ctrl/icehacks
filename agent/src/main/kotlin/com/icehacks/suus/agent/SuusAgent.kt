package com.icehacks.suus.agent

import android.util.Log
import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.AgentRunner
import com.icehacks.suus.core.CharacterMode
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.Overlay
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.UserCommand
import com.icehacks.suus.core.VoiceIO
import com.icehacks.suus.agent.data.NoOpRunLogger
import com.icehacks.suus.agent.data.RunLogger
import com.icehacks.suus.agent.data.sessionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Routes each command: goals run the [AgentLoop]; "undo …" removes exactly one of the agent's
 * own draft edits (deterministic, no model call) and checks nothing else changed; memory
 * commands list or forget saved preferences; Stop resets the character.
 */
class SuusAgent(
    private val screen: ScreenController,
    private val voice: VoiceIO,
    private val overlay: Overlay,
    private val loop: AgentLoop,
    private val prefs: PreferenceStore,
    private val ledger: DraftLedger,
    private val log: (String) -> Unit = { runCatching { Log.i(AgentLoop.TAG, it) } },
    private val runLog: RunLogger = NoOpRunLogger,
) : AgentRunner {

    override suspend fun handle(command: UserCommand) {
        log("handle $command")
        try {
            when (command) {
                is UserCommand.Goal -> {
                    // Acknowledge at once (while the first model call runs), so the user knows it heard them.
                    val ack = ACKS.random()
                    overlay.setMode(CharacterMode.THINKING)
                    overlay.caption(ack)
                    val outcome = coroutineScope {
                        launch { voice.say(ack) }
                        loop.run(command.text, command.takeOver)
                    }
                    log("outcome $outcome")
                    runLog.sessionOutcome(outcome)
                    if (loop.submitted) loop.notes.clear()
                }
                is UserCommand.Undo -> undo(command.text)
                is UserCommand.Memory -> memory(command.text)
                UserCommand.Stop -> {
                    overlay.highlight(emptyList())
                    overlay.setMode(CharacterMode.IDLE)
                    overlay.caption("Stopped.")
                    voice.say("Stopped.")
                }
            }
        } catch (e: CancellationException) {
            overlay.highlight(emptyList())
            overlay.setMode(CharacterMode.IDLE)
            overlay.caption("Stopped.")
            throw e
        }
    }

    // ---------- selective undo ----------

    suspend fun undo(request: String) {
        val edit = ledger.pick(request)
        if (edit == null) {
            say("There's nothing I added that I can undo.")
            return
        }
        val before = screen.snapshot()
        if (!ledger.stillApplied(edit, before)) {
            say("The ${edit.label} isn't how I left it, so I didn't change anything.")
            return
        }
        val keep = DraftLedger.fieldTexts(before, except = edit.afterText)
        overlay.setMode(CharacterMode.ACTING)

        val result: ActionResult = when (edit.kind) {
            DraftLedger.Kind.ATTACHMENT -> {
                val remove = before.nodes.firstOrNull { DraftLedger.removeControl(it) == edit.marker }!!
                overlay.pointAt(remove.bounds, "Removing the ${edit.label}")
                screen.perform(DeviceAction.Tap(remove.id), before).also { overlay.showResult(remove.bounds, it is ActionResult.Done) }
            }
            DraftLedger.Kind.TEXT -> {
                val field = before.nodes.first { it.editable && (it.text ?: "") == (edit.afterText ?: "") }
                overlay.pointAt(field.bounds, "Putting the ${edit.label} back")
                screen.perform(DeviceAction.EnterText(field.id, edit.beforeText ?: ""), before)
                    .also { overlay.showResult(field.bounds, it is ActionResult.Done) }
            }
            DraftLedger.Kind.CHOICE -> {
                say("I can't un-pick ${edit.label}, but I can pick a different option if you tell me which.")
                return
            }
        }
        if (result !is ActionResult.Done) {
            say("I couldn't undo the ${edit.label}. Nothing else was changed.")
            return
        }
        ledger.remove(edit)
        val after = result.after
        val stillThere = ledger.stillApplied(edit, after)
        val afterTexts = DraftLedger.fieldTexts(after)
        val lost = keep.filter { it !in afterTexts }
        when {
            stillThere -> say("The ${edit.label} still seems to be there. Please check the screen.")
            lost.isNotEmpty() -> say("I removed the ${edit.label}, but some other text changed too. Please check: ${lost.joinToString()}.")
            else -> say("Removed the ${edit.label}. Everything else is as it was.")
        }
    }

    // ---------- memory ----------

    suspend fun memory(text: String) {
        val t = text.lowercase()
        val all = prefs.all()
        if (t.startsWith("forget")) {
            if (Regex("\\b(everything|all)\\b").containsMatchIn(t)) {
                prefs.clear()
                say("Okay, I forgot all saved preferences.")
                return
            }
            val words = t.removePrefix("forget").split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in setOf("the", "that", "preference", "about", "my") }
            val match = all.lastOrNull { p ->
                val hay = "${p.scope} ${scopeLabel(p.scope)} ${p.key} ${p.value}".lowercase()
                words.any { w -> hay.contains(w.removeSuffix("s")) }
            }
            if (match == null) {
                say("I don't have a saved preference like that.")
            } else {
                prefs.remove(match.scope, match.key)
                say("Okay, I forgot: ${match.spoken()}.")
            }
            return
        }
        if (all.isEmpty()) say("I haven't saved any preferences.")
        else say("I remember: " + all.joinToString("; ") { it.spoken() } + ".")
    }

    private suspend fun say(text: String) {
        overlay.setMode(CharacterMode.IDLE)
        overlay.caption(text)
        voice.say(text)
    }

    private companion object {
        val ACKS = listOf("On it!", "Sure thing!", "Got it, let's do this!", "Okay, on my way!", "You got it!")
    }
}
