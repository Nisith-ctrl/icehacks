package com.icehacks.saythegoal.agent

import android.util.Log
import com.icehacks.saythegoal.core.ActionResult
import com.icehacks.saythegoal.core.AllowedApps
import com.icehacks.saythegoal.core.ApprovalGate
import com.icehacks.saythegoal.core.ApprovalOutcome
import com.icehacks.saythegoal.core.Bounds
import com.icehacks.saythegoal.core.CharacterMode
import com.icehacks.saythegoal.core.DeviceAction
import com.icehacks.saythegoal.core.Overlay
import com.icehacks.saythegoal.core.ScreenController
import com.icehacks.saythegoal.core.ScreenSnapshot
import com.icehacks.saythegoal.core.ScrollDirection
import com.icehacks.saythegoal.core.UiNode
import com.icehacks.saythegoal.core.VoiceIO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

sealed interface Outcome {
    data class Done(val summary: String) : Outcome
    data class Stopped(val reason: String) : Outcome
    data object Cancelled : Outcome
}

/**
 * One goal, one model-chosen action at a time. Code validates every choice, points the
 * character at the target, performs it through the device layer, records draft edits, and
 * enforces the guards (allowed apps, form host, action cap, no-progress, approval for commits).
 */
class AgentLoop(
    private val screen: ScreenController,
    private val voice: VoiceIO,
    private val approval: ApprovalGate,
    private val overlay: Overlay,
    private val planner: Planner,
    private val prefs: PreferenceStore,
    private val ledger: DraftLedger,
    private val calendar: CalendarSource?,
    private val photos: PhotoSource?,
    private val config: AgentConfig = AgentConfig(),
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
    private val log: (String) -> Unit = { runCatching { Log.i(TAG, it) } },
) {
    /** Facts carried across goals in one session (e.g. the photo list for "use the other one"). */
    val notes = mutableListOf<String>()
    var submitted = false
        private set

    private val photoDescriptions = HashMap<Long, String?>()

    suspend fun run(goal: String, takeOver: Boolean): Outcome {
        val history = ArrayDeque<String>()
        var feedback: String? = null
        var actions = 0
        var errors = 0
        val screensAfterActions = HashMap<Int, Int>()
        submitted = false

        overlay.setMode(CharacterMode.THINKING)
        overlay.caption("Working on it…")

        for (turn in 1..config.maxTurns) {
            currentCoroutineContext().ensureActive()
            val snap = screen.snapshot()
            val screenText = ScreenText.render(snap)

            // ---- guards ----
            if (actions > 0 && snap.packageName.isNotEmpty() && snap.packageName !in AllowedApps.packages) {
                return stop("I ended up outside the apps I'm allowed to use, so I stopped.")
            }
            ScreenText.chromeUrl(snap)?.let { url ->
                if (looksLikeUrl(url) && !hostAllowed(url)) return stop("That page isn't one of the registration forms, so I stopped.")
            }
            if (errors >= 3) return stop("I couldn't get that to work, so I stopped.")
            if (actions >= config.maxActions) return stop("This is taking too many steps, so I stopped.")

            overlay.setMode(CharacterMode.THINKING)
            val step = planner.next(
                PlannerTurn(
                    goal = goal,
                    takeOver = takeOver,
                    now = now().format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm z")),
                    preferences = prefs.all().map { it.describe() },
                    notes = notes.takeLast(30),
                    history = history.toList(),
                    feedback = feedback,
                    screen = screenText,
                ),
            )
            log("turn $turn: ${step.tool} ${step.input}")
            feedback = null
            val input = step.input

            fun s(k: String) = input[k]?.jsonPrimitive?.contentOrNull
            fun remember(line: String) {
                history += line
                while (history.size > config.historyLines) history.removeFirst()
            }

            when (step.tool) {
                "tap", "enter_text", "scroll", "back", "launch_app" -> {
                    val caption = s("caption") ?: "Working"
                    val node = s("node_id")?.let { id -> snap.nodes.firstOrNull { it.id == id } }
                    val invalid: String? = when (step.tool) {
                        "tap" -> when {
                            node == null -> "There is no node ${s("node_id")} on this screen."
                            looksLikeCommit(node) -> "That is a submit/send button: use the submit tool with a read-back."
                            else -> null
                        }
                        "enter_text" -> when {
                            node == null -> "There is no node ${s("node_id")} on this screen."
                            !node.editable -> "Node ${node.id} is not an editable field."
                            s("text") == null -> "enter_text needs text."
                            else -> null
                        }
                        "scroll" -> if (s("node_id") != null && node == null) "There is no node ${s("node_id")} on this screen." else null
                        "launch_app" -> if (LAUNCHABLE_APPS[s("app")] == null) "Unknown app ${s("app")}." else null
                        else -> null
                    }
                    if (invalid != null) {
                        errors++
                        feedback = invalid
                        remember("${step.tool} rejected: $invalid")
                        continue
                    }
                    val action = when (step.tool) {
                        "tap" -> DeviceAction.Tap(node!!.id)
                        "enter_text" -> DeviceAction.EnterText(node!!.id, s("text")!!)
                        "scroll" -> DeviceAction.Scroll(node?.id, if (s("direction") == "up") ScrollDirection.UP else ScrollDirection.DOWN)
                        "back" -> DeviceAction.Back
                        else -> DeviceAction.LaunchApp(LAUNCHABLE_APPS.getValue(s("app")!!))
                    }
                    val r = act(action, snap, node, caption)
                    actions++
                    when (r) {
                        is ActionResult.Done -> {
                            errors = 0
                            recordEdit(step.tool, snap, r.after, node, s("text"), s("edit_label"))?.let { remember(it) }
                            val target = node?.let { describe(it) } ?: s("app") ?: s("direction") ?: ""
                            remember("${step.tool} $target -> ok, now ${ScreenText.appName(r.after.packageName)}" + if (!r.screenChanged) " (screen didn't change)" else "")
                            if (!r.screenChanged) feedback = "The screen did not change after that."
                            val key = ScreenText.render(r.after).hashCode()
                            val seen = (screensAfterActions[key] ?: 0) + 1
                            screensAfterActions[key] = seen
                            if (seen >= 4) return stop("I keep ending up on the same screen, so I stopped.")
                        }
                        is ActionResult.Failed -> {
                            if (r.reason == "no progress") return stop("I'm not making progress, so I stopped.")
                            errors++
                            feedback = "That didn't work: ${r.reason}."
                            remember("${step.tool} failed: ${r.reason}")
                        }
                        is ActionResult.TargetNotFound -> {
                            errors++
                            feedback = "That element is no longer on screen."
                            remember("${step.tool} failed: target gone")
                        }
                        is ActionResult.NotAllowed -> {
                            errors++
                            feedback = "Not allowed: ${r.reason}."
                            remember("${step.tool} not allowed: ${r.reason}")
                        }
                    }
                }

                "ask_user" -> {
                    val question = s("question") ?: "What should I do?"
                    val choices = input["choices"]?.let { runCatching { it.jsonArray.map { e -> e.jsonPrimitive.content } }.getOrNull() } ?: emptyList()
                    overlay.setMode(CharacterMode.LISTENING)
                    overlay.caption(question)
                    val answer = voice.ask(question, choices)
                        ?: return stop("I didn't hear an answer, so I stopped. Nothing was submitted.")
                    if (answer.trim().lowercase().trimEnd('.', '!') in setOf("stop", "cancel", "never mind")) {
                        return stop("Okay, I stopped.")
                    }
                    val chosen = choices.firstOrNull { it.equals(answer.trim(), ignoreCase = true) } ?: choiceIn(answer, choices)
                    var saved: SavedPreference? = null
                    val scope = s("remember_scope")
                    val key = s("remember_key")
                    if (wantsRemember(answer) && scope != null && key != null) {
                        saved = SavedPreference(scope, key, chosen ?: answer.trim(), answer.trim(), System.currentTimeMillis())
                        prefs.save(saved)
                        overlay.caption("Saved: ${saved.spoken()}")
                        voice.say("Saved: ${saved.spoken()}.")
                    }
                    remember("asked \"$question\" -> \"$answer\"" + (chosen?.let { " (= $it)" } ?: "") + if (saved != null) " [saved preference]" else "")
                    feedback = "The user answered: \"$answer\"" + (chosen?.let { " (choice: $it)" } ?: "") +
                        when {
                            saved != null -> ". Saved as a preference. Saving is not approval to submit."
                            wantsRemember(answer) -> ". They asked you to remember it, but you gave no remember_scope/remember_key, so it was not saved."
                            else -> "."
                        }
                    overlay.setMode(CharacterMode.THINKING)
                }

                "note" -> {
                    s("text")?.let { notes += it }
                    feedback = "Noted."
                }

                "check_calendar" -> {
                    overlay.caption("Checking your calendar…")
                    feedback = checkCalendar(input["options"] as? JsonArray)
                    remember("checked calendar: $feedback")
                }

                "find_photos" -> {
                    overlay.caption("Looking for your photos…")
                    feedback = findPhotos(s("day") ?: "today")
                    notes += "Photos: $feedback"
                    remember("found photos")
                }

                "submit" -> {
                    val node = s("node_id")?.let { id -> snap.nodes.firstOrNull { it.id == id } }
                    val commitWord = if (s("commit_word") == "Send") "Send" else "Submit"
                    val readback = s("readback") ?: ""
                    when {
                        submitted -> {
                            feedback = "Already submitted once. Never submit twice; check the result and call done."
                            continue
                        }
                        node == null -> {
                            errors++
                            feedback = "There is no node ${s("node_id")} on this screen. Scroll so the button is visible."
                            continue
                        }
                        readback.isBlank() -> {
                            errors++
                            feedback = "submit needs a read-back."
                            continue
                        }
                    }
                    node!!
                    overlay.highlight(readbackTargets(snap, node))
                    overlay.setMode(CharacterMode.WAITING_APPROVAL)
                    overlay.caption(readback)
                    val res = approval.request(readback, snap, commitWord)
                    overlay.highlight(emptyList())
                    when (res.outcome) {
                        ApprovalOutcome.CANCELLED -> {
                            overlay.setMode(CharacterMode.IDLE)
                            overlay.caption("Cancelled. Nothing was sent.")
                            voice.say("Okay, cancelled. I left everything as it is.")
                            return Outcome.Cancelled
                        }
                        ApprovalOutcome.NO_ANSWER ->
                            return stop("I didn't hear $commitWord or Cancel, so I stopped. Nothing was sent.")
                        ApprovalOutcome.APPROVED -> {
                            val r = act(DeviceAction.Tap(node.id, isCommit = true), snap, node, if (commitWord == "Send") "Sending" else "Submitting", res.approval)
                            actions++
                            if (r is ActionResult.Done) {
                                submitted = true
                                ledger.clear() // undo stays within the draft stage
                                remember("submitted with \"$commitWord\" after spoken approval")
                                feedback = "Submitted. Read the result page and call done with what it shows (or say you're not sure). Do not submit again."
                            } else {
                                remember("submit refused: $r")
                                feedback = "It was NOT submitted ($r). If the screen changed after approval, check it and call submit again for a fresh approval."
                            }
                        }
                    }
                }

                "done" -> {
                    val summary = s("summary") ?: "Done."
                    overlay.setMode(CharacterMode.IDLE)
                    overlay.caption(summary)
                    voice.say(summary)
                    return Outcome.Done(summary)
                }

                "stop" -> return stop(s("reason") ?: "I stopped.")

                "none" -> {
                    errors++
                    feedback = "Call exactly one tool each turn."
                }

                else -> {
                    errors++
                    feedback = "Unknown tool ${step.tool}."
                }
            }
        }
        return stop("This is taking too many steps, so I stopped.")
    }

    // ---------- helpers ----------

    private suspend fun stop(reason: String): Outcome {
        overlay.highlight(emptyList())
        overlay.setMode(CharacterMode.PROBLEM)
        overlay.caption(reason)
        voice.say(reason)
        return Outcome.Stopped(reason)
    }

    private suspend fun act(
        action: DeviceAction,
        snap: ScreenSnapshot,
        node: UiNode?,
        caption: String,
        approvalToken: com.icehacks.saythegoal.core.Approval? = null,
    ): ActionResult {
        overlay.setMode(CharacterMode.ACTING)
        if (node != null) overlay.pointAt(node.bounds, caption) else overlay.caption(caption)
        val r = screen.perform(action, snap, approvalToken)
        if (node != null) overlay.showResult(node.bounds, r is ActionResult.Done)
        log("perform $action -> ${r::class.simpleName}")
        return r
    }

    private fun recordEdit(tool: String, before: ScreenSnapshot, after: ScreenSnapshot, node: UiNode?, text: String?, label: String?): String? {
        node ?: return null
        return when {
            tool == "enter_text" && text != null -> {
                ledger.recordText(before, node, text)
                null
            }
            tool == "tap" && node.checkable && !node.checked -> {
                ledger.recordChoice(before, node, label)
                null
            }
            tool == "tap" -> ledger.detectAttachment(before, after, label)?.let { "attachment added: ${it.label}" }
            else -> null
        }
    }

    private fun readbackTargets(snap: ScreenSnapshot, submit: UiNode): List<Bounds> =
        snap.nodes.filter { (it.editable && !it.text.isNullOrBlank()) || (it.checkable && it.checked) }.map { it.bounds } + submit.bounds

    private fun checkCalendar(options: JsonArray?): String {
        if (options == null || options.isEmpty()) return "No options given."
        val src = calendar ?: return "The calendar can't be read on this device; open the Calendar app to check."
        return options.joinToString("; ") { el ->
            val o = el as? JsonObject ?: return@joinToString "bad option"
            val label = o["label"]?.jsonPrimitive?.contentOrNull ?: "option"
            val start = o["start"]?.jsonPrimitive?.contentOrNull?.let { TimeText.parse(it) }
            val end = o["end"]?.jsonPrimitive?.contentOrNull?.let { TimeText.parse(it) }
            if (start == null || end == null || end <= start) return@joinToString "$label: couldn't read the time"
            val busy = runCatching { conflicts(src.events(start, end), start, end) }.getOrElse {
                return@joinToString "$label: calendar not readable (${it.message})"
            }
            if (busy.isEmpty()) "$label (${TimeText.spoken(start)}): free"
            else "$label (${TimeText.spoken(start)}): conflicts with " + busy.joinToString { "\"${it.title}\" ${TimeText.spoken(it.startMs)}" }
        }
    }

    private suspend fun findPhotos(day: String): String {
        val src = photos ?: return "Photos can't be read on this device."
        val (start, end) = TimeText.day(day) ?: return "I couldn't understand the day \"$day\"."
        val list = runCatching { src.photosBetween(start, end) }.getOrElse { return "Photos not readable (${it.message})." }
        if (list.isEmpty()) return "No photos from $day."
        return list.take(6).mapIndexed { i, p ->
            val desc = photoDescriptions.getOrPut(p.id) { src.thumbnailJpeg(p)?.let { planner.describePhoto(it) } }
            "photo ${i + 1}: taken ${TimeText.spoken(p.takenMs)}, file ${p.name}" + (desc?.let { ", looks like: $it" } ?: "")
        }.joinToString("; ")
    }

    private fun hostAllowed(url: String): Boolean {
        val host = ScreenText.host(url)
        return config.formHosts.any { host == it || host.endsWith(".$it") }
    }

    companion object {
        const val TAG = "STG-Agent"
        private val COMMIT = Regex("^(submit|send|register|sign up|confirm|place order|pay|book|post)\\b", RegexOption.IGNORE_CASE)

        fun looksLikeCommit(n: UiNode): Boolean {
            val label = (n.text ?: n.contentDescription ?: "").trim()
            return label.isNotEmpty() && COMMIT.containsMatchIn(label) && !n.editable
        }

        fun looksLikeUrl(t: String) = !t.contains(' ') && t.contains('.')

        private fun describe(n: UiNode) = "\"" + (n.text ?: n.contentDescription ?: n.hint ?: n.className.substringAfterLast('.')).take(40) + "\""
    }
}
