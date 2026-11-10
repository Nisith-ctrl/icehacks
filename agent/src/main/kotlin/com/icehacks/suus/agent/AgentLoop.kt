package com.icehacks.suus.agent

import android.util.Log
import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.AllowedApps
import com.icehacks.suus.core.ApprovalGate
import com.icehacks.suus.core.ApprovalOutcome
import com.icehacks.suus.core.Bounds
import com.icehacks.suus.core.CharacterMode
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.Overlay
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.ScrollDirection
import com.icehacks.suus.core.UiNode
import com.icehacks.suus.core.VoiceIO
import com.icehacks.suus.agent.form.ConfirmationCheck
import com.icehacks.suus.agent.form.FormFiller
import com.icehacks.suus.agent.form.FormMapper
import com.icehacks.suus.agent.form.FormReader
import com.icehacks.suus.agent.form.sameLabel
import com.icehacks.suus.agent.form.sameChoice
import com.icehacks.suus.agent.form.normalizeLabel
import kotlinx.serialization.json.JsonPrimitive
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

    /** Fills forms field by field through [act]; remembers dropdown choices Chrome doesn't expose. */
    private val filler = FormFiller(screen, ledger, { a, s, n, c -> act(a, s, n, c) }, log)

    /** What the current form holds (label -> value), for the read-back and the confirmation check. */
    private val formValues = LinkedHashMap<String, String>()

    /** Form pages already auto-filled in this run (keyed by URL without the query). */
    private val autoFilled = mutableSetOf<String>()

    suspend fun run(goal: String, takeOver: Boolean): Outcome {
        val history = ArrayDeque<String>()
        var feedback: String? = null
        var actions = 0
        var errors = 0
        val screensAfterActions = HashMap<Int, Int>()
        submitted = false
        formValues.clear()
        filler.reset()
        autoFilled.clear()

        overlay.setMode(CharacterMode.THINKING)
        overlay.caption("Working on it…")

        for (turn in 1..config.maxTurns) {
            currentCoroutineContext().ensureActive()
            val snap = screen.snapshot()
            val form = formSummary(snap)
            val screenText = ScreenText.render(snap, skipFormFields = form != null)

            // ---- guards ----
            if (actions > 0 && snap.packageName.isNotEmpty() && snap.packageName !in AllowedApps.packages) {
                return stop("I ended up outside the apps I'm allowed to use, so I stopped.")
            }
            ScreenText.chromeUrl(snap)?.let { url ->
                if (looksLikeUrl(url) && !hostAllowed(url)) return stop("That page isn't one of the registration forms, so I stopped.")
            }
            if (errors >= 3) return stop("I couldn't get that to work, so I stopped.")
            if (actions >= config.maxActions) return stop("This is taking too many steps, so I stopped.")

            // First time on a form page: fill what's known in code, then tell the model what's left.
            val page = ScreenText.chromeUrl(snap)?.substringBefore('?')
            if (config.autoFillForms && form != null && page != null && !submitted && autoFilled.add(page)) {
                overlay.setMode(CharacterMode.ACTING)
                overlay.caption("Filling in what I know")
                val report = filler.fill(config.profile, prefs.all(), emptyMap(), hints = hintsFor(goal),
                    maxActions = (config.maxActions - actions).coerceAtLeast(1))
                actions += report.actions
                formValues.putAll(report.values)
                feedback = listOfNotNull(feedback, "I filled the form in code. " + report.describe()).joinToString(" ")
                history += "filled the form in code: ${report.filled.size} filled, ${report.needs.size} still needed"
                while (history.size > config.historyLines) history.removeFirst()
                continue
            }

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
                    form = form,
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
                            looksLikeCommit(snap, node) -> "That is a submit/send button: use the submit tool with a read-back."
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
                    val applied = if (config.autoFillForms) applyAnswerToForm(question, choices, chosen ?: answer.trim(), goal) else null
                    feedback = "The user answered: \"$answer\"" + (chosen?.let { " (choice: $it)" } ?: "") +
                        when {
                            saved != null -> ". Saved as a preference. Saving is not approval to submit."
                            wantsRemember(answer) -> ". They asked you to remember it, but you gave no remember_scope/remember_key, so it was not saved."
                            else -> "."
                        } + (applied?.let { " $it" } ?: "")
                    overlay.setMode(CharacterMode.THINKING)
                }

                "note" -> {
                    s("text")?.let { notes += it }
                    feedback = "Noted."
                }

                "fill_form" -> {
                    if (ScreenText.chromeUrl(snap) == null || FormReader.read(snap).fields.isEmpty()) {
                        errors++
                        feedback = "There's no form on this screen."
                        continue
                    }
                    val answers = (input["answers"] as? JsonObject)?.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                    }?.toMap() ?: emptyMap()
                    overlay.setMode(CharacterMode.ACTING)
                    val report = filler.fill(config.profile, prefs.all(), answers, maxActions = (config.maxActions - actions).coerceAtLeast(1))
                    actions += report.actions
                    formValues.putAll(report.values)
                    if (report.filled.isNotEmpty() || report.needs.isEmpty()) errors = 0
                    feedback = report.describe()
                    remember("fill_form: ${report.filled.size} filled, ${report.needs.size} still needed")
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
                                feedback = "Submitted. " + verifyConfirmation(r.after) +
                                    " Call done with what it shows (or say you're not sure). Do not submit again."
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
                    feedback = step.text?.takeIf { it.startsWith("Your last tool call was malformed") }
                        ?: "Call exactly one tool each turn."
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
        approvalToken: com.icehacks.suus.core.Approval? = null,
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

    /** A short summary of the form on screen for the model, or null when there's no form. */
    private fun formSummary(snap: ScreenSnapshot): String? {
        val url = ScreenText.chromeUrl(snap) ?: return null
        if (!looksLikeUrl(url) || !hostAllowed(url)) return null
        val form = FormReader.read(snap)
        if (form.fields.isEmpty()) return null
        val plans = FormMapper.plan(form, config.profile, prefs.all())
        return buildString {
            form.title?.let { appendLine("Form: $it") }
            for (p in plans) {
                val f = p.field
                val known = f.value ?: formValues.entries.firstOrNull { sameLabel(it.key, f.label) }?.value
                val state = if (known != null) "set to \"$known\"" else "empty"
                val opts = if (f.options.isNotEmpty()) " Options: " + f.options.joinToString(" | ") { it.label } else ""
                val req = if (f.required) ", required" else ""
                val target = p.target?.let { ": \"$it\"" } ?: ""
                appendLine("- ${f.label} (${f.kind.name.lowercase()}$req) $state -> ${p.reason}$target.$opts")
            }
            if (form.submitNodeId != null) append("Submit button: \"${form.submitLabel}\" (${form.submitNodeId})")
            else append("Submit button: not on screen yet (scroll down)")
        }
    }

    private fun hintsFor(goal: String) = (listOf(goal) + notes.takeLast(30)).joinToString(" ")

    /**
     * If a form is on screen and the question was about one of its fields (its label words are in
     * the question, or the offered choices are that field's options), put the answer in that field.
     * Returns the filler's report for the model, or null if nothing matched.
     */
    private suspend fun applyAnswerToForm(question: String, choices: List<String>, value: String, goal: String): String? {
        val snap = screen.snapshot()
        val url = ScreenText.chromeUrl(snap) ?: return null
        if (!looksLikeUrl(url) || !hostAllowed(url)) return null
        val form = FormReader.read(snap)
        val q = " " + normalizeLabel(question) + " "
        val field = form.fields.filter { it.value == null }.firstOrNull { f ->
            val words = normalizeLabel(f.label).split(" ").filter { it.length > 3 }
            (words.isNotEmpty() && words.count { q.contains(" $it ") } * 2 >= words.size) ||
                (choices.isNotEmpty() && f.options.isNotEmpty() && choices.all { c -> f.options.any { sameChoice(it.label, c) } })
        } ?: return null
        overlay.setMode(CharacterMode.ACTING)
        val report = filler.fill(config.profile, prefs.all(), mapOf(field.label to value), hints = hintsFor(goal))
        formValues.putAll(report.values)
        return "I put it in \"${field.label}\". " + report.describe()
    }

    /** Re-reads the page after a submit and checks it's a confirmation showing what was sent. */
    private suspend fun verifyConfirmation(after: ScreenSnapshot): String {
        var result = ConfirmationCheck.check(after, formValues)
        if (!result.confirmed) {
            kotlinx.coroutines.delay(2_000) // the confirmation page may still be loading
            result = ConfirmationCheck.check(screen.snapshot(), formValues)
        }
        log("confirmation check: $result")
        return result.describe()
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
        const val TAG = "SUUS-Agent"
        private val COMMIT = Regex("^(submit|send|register|sign up|confirm|place order|pay|book|post)\\b", RegexOption.IGNORE_CASE)

        /** A view id that names a send/submit control: ends in send/submit (optionally "_button"), e.g. "Compose:Draft:Send". */
        private val COMMIT_ID = Regex("(^|[/:_.-])(send|submit)([_-]?(button|btn))?$", RegexOption.IGNORE_CASE)

        /** The node a tap really activates: itself if clickable, else its nearest clickable ancestor (as the device layer does). */
        fun clickTarget(s: ScreenSnapshot, n: UiNode): UiNode {
            val byId = s.nodes.associateBy { it.id }
            var cur: UiNode? = n
            while (cur != null && !cur.clickable) cur = cur.parentId?.let { byId[it] }
            return cur ?: n
        }

        /**
         * Would tapping [n] submit or send? Checks the node, the control the tap really activates,
         * that control's children (Compose apps put the label "Send SMS" on a child of the clickable
         * node) and its view id (e.g. "Compose:Draft:Send").
         */
        fun looksLikeCommit(s: ScreenSnapshot, n: UiNode): Boolean {
            if (n.editable || n.checkable) return false // a radio/checkbox is an answer, not a commit
            val target = clickTarget(s, n)
            if (target.editable || target.checkable) return false
            if (target.viewId?.let { COMMIT_ID.containsMatchIn(it) } == true) return true
            val parentOf = s.nodes.associate { it.id to it.parentId }
            fun under(x: UiNode, depth: Int = 3): Boolean {
                var p = x.parentId
                repeat(depth) {
                    if (p == null) return false
                    if (p == target.id) return true
                    p = parentOf[p]
                }
                return false
            }
            val labelled = listOf(n, target) + s.nodes.filter { under(it) }
            return labelled.any { node ->
                listOfNotNull(node.text, node.contentDescription).any { COMMIT.containsMatchIn(it.trim()) }
            }
        }

        fun looksLikeCommit(n: UiNode): Boolean = looksLikeCommit(ScreenSnapshot("", null, 0L, listOf(n)), n)

        fun looksLikeUrl(t: String) = !t.contains(' ') && t.contains('.')

        private fun describe(n: UiNode) = "\"" + (n.text ?: n.contentDescription ?: n.hint ?: n.className.substringAfterLast('.')).take(40) + "\""
    }
}
