package com.icehacks.suus.agent.form

import com.icehacks.suus.agent.AgentLoop
import com.icehacks.suus.agent.DraftLedger
import com.icehacks.suus.agent.Profile
import com.icehacks.suus.agent.SavedPreference
import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.ScrollDirection
import com.icehacks.suus.core.UiNode

/**
 * Fills a web form from a [FormMapper] plan, one checked action at a time, scrolling through
 * long forms. Never presses submit: that stays with the spoken-approval path.
 *
 * @param act performs one device action the way the agent does (overlay points first, then
 *   ScreenController.perform). Returns the device result.
 */
class FormFiller(
    private val screen: ScreenController,
    private val ledger: DraftLedger,
    private val act: suspend (DeviceAction, ScreenSnapshot, UiNode?, String) -> ActionResult,
    private val log: (String) -> Unit = {},
) {
    /** Dropdown values we chose, keyed by page + field (Chrome doesn't expose a select's value). */
    private val chosenInSelects = HashMap<String, String>()

    /** Forget dropdown choices: call at the start of each run (a new tab of the same form starts empty). */
    /**
     * Options of fields the user must be asked about, by field label, as last read on the page
     * (a dropdown's options are only known after it has been opened once). Used to show and speak
     * the choices when SuuS asks, even if the model leaves them out.
     */
    val knownOptions = mutableMapOf<String, List<String>>()

    fun reset() {
        chosenInSelects.clear()
        knownOptions.clear()
    }

    data class Report(
        val filled: List<String>,
        val needs: List<String>,
        val failed: List<String>,
        val actions: Int,
        val submitNodeId: String?,
        val submitLabel: String?,
        /** label -> value the form now holds (what we set or what was already there). */
        val values: Map<String, String>,
    ) {
        fun describe(): String = buildString {
            if (filled.isNotEmpty()) append("Filled: ").append(filled.joinToString("; ")).append(". ")
            if (needs.isNotEmpty()) append("Still needed: ").append(needs.joinToString("; ")).append(". ")
            if (failed.isNotEmpty()) append("Couldn't set: ").append(failed.joinToString("; ")).append(". ")
            if (submitNodeId != null) append("The '$submitLabel' button is on screen ($submitNodeId); call submit when nothing is still needed.")
            else append("The submit button isn't on screen yet; scroll down to reach it.")
        }.trim()
    }

    suspend fun fill(
        profile: Profile,
        preferences: List<SavedPreference>,
        answers: Map<String, String>,
        hints: String = "",
        maxActions: Int = 20,
    ): Report {
        val handled = mutableSetOf<String>()
        /** Fields whose option wasn't among the visible ones: retried once after the next scroll. */
        val retryAfterScroll = mutableSetOf<String>()
        val retried = mutableSetOf<String>()
        var atBottom = false
        val filled = mutableListOf<String>()
        val needs = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val values = LinkedHashMap<String, String>()
        var actions = 0
        var scrolls = 0
        var submitId: String? = null
        var submitLabel: String? = null

        // A later pass may start scrolled down (e.g. at the submit button). Go back to the top when
        // the page heading is off screen or an answer names a field that isn't visible.
        run {
            var snap = screen.snapshot()
            var form = FormReader.read(snap)
            var ups = 0
            while (ups < 6 && actions < maxActions &&
                (form.title == null || answers.keys.any { a -> form.fields.none { sameLabel(a, it.label) } })
            ) {
                val r = act(DeviceAction.Scroll(null, ScrollDirection.UP), snap, null, "Back to the top of the form")
                actions++
                ups++
                if (r !is ActionResult.Done || !r.screenChanged) break
                snap = r.after
                form = FormReader.read(snap)
            }
        }

        outer@ while (actions < maxActions) {
            val snap = screen.snapshot()
            val page = pageKey(snap)
            val form = withChosenSelects(FormReader.read(snap), page)
            if (form.submitNodeId != null) { submitId = form.submitNodeId; submitLabel = form.submitLabel }
            val plans = FormMapper.plan(form, profile, preferences, answers, hints)

            for (p in plans) {
                val f = p.field
                if (f.key in handled) continue
                handled += f.key
                val mayBeCutOff = f.kind == FieldKind.CHOICE && form.submitNodeId == null && !atBottom &&
                    f.key !in retried && scrolls < 6
                if (mayBeCutOff && (p.source == Source.ASK || (p.target != null && f.options.none { sameChoice(it.label, p.target) }))) {
                    handled -= f.key
                    retryAfterScroll += f.key
                    continue
                }
                when (p.source) {
                    Source.PROFILE, Source.PREFERENCE, Source.ANSWER, Source.CHOSEN_OPTION.takeIf { p.target != null } -> {
                        if (p.done) {
                            values[f.label] = f.value!!
                            filled += "${f.label} = ${f.value} (already there)"
                            continue
                        }
                        val target = p.target ?: continue
                        val result = apply(snap, f, target, page)
                        log("fill: ${f.label} (${f.kind}, ${p.source}) -> '$target' node=${f.nodeId ?: f.options.joinToString { it.nodeId + "=" + it.label }}: " +
                            if (result.ok) "ok" else "FAILED ${result.problem}")
                        actions += result.actions
                        if (result.ok) {
                            values[f.label] = result.value ?: target
                            filled += "${f.label} = ${result.value ?: target} (${p.reason})"
                        } else if (f.kind == FieldKind.CHOICE && result.actions == 0 && f.key !in retried && scrolls < 6 && !atBottom) {
                            // Part of the radio group may be below the fold: look again after scrolling.
                            handled -= f.key
                            retryAfterScroll += f.key
                            continue
                        } else {
                            failed += "${f.label}: ${result.problem}"
                            if (p.source == Source.PREFERENCE) needs += "${f.label}: the saved preference '${target}' isn't offered here; ask the user" +
                                (result.options.takeIf { it.isNotEmpty() }?.let { " (options: ${it.joinToString()})" } ?: "")
                        }
                        continue@outer // ids change after an action: read the screen again
                    }
                    Source.CHOSEN_OPTION -> {
                        if (f.value != null) { values[f.label] = f.value; filled += "${f.label} = ${f.value} (already chosen)" }
                        else needs += "${f.label}: pick one with fill_form answers {\"${f.label}\": \"…\"} after check_calendar (options: ${f.options.joinToString { "'${it.label}'" }})"
                    }
                    Source.ASK -> {
                        if (f.value != null) { values[f.label] = f.value; filled += "${f.label} = ${f.value} (already there)"; continue }
                        val why = if (p.preference != null) p.reason else "ask the user"
                        var opts = f.options.map { it.label }
                        if (f.kind == FieldKind.SELECT && opts.isEmpty()) {
                            val r = readSelectOptions(snap, f)
                            actions += r.second
                            opts = r.first
                            if (opts.isNotEmpty()) knownOptions[f.label] = opts
                            needs += "${f.label}: $why" + (if (opts.isNotEmpty()) " (options: ${opts.joinToString()})" else "")
                            continue@outer
                        }
                        if (opts.isNotEmpty()) knownOptions[f.label] = opts
                        needs += "${f.label}: $why" + (if (opts.isNotEmpty()) " (options: ${opts.joinToString()})" else "")
                    }
                    Source.SKIP -> {}
                }
            }
            if (submitId != null && retryAfterScroll.isEmpty()) break
            if (atBottom || scrolls >= 6) break
            val before = snap
            val r = act(DeviceAction.Scroll(null, ScrollDirection.DOWN), before, null, "Scrolling down the form")
            actions++
            scrolls++
            retried += retryAfterScroll
            retryAfterScroll.clear()
            // Already at the bottom: one last pass over the same screen settles the deferred fields.
            if (r !is ActionResult.Done || !r.screenChanged) atBottom = true
        }
        log("fill_form: filled=${filled.size} needs=${needs.size} failed=${failed.size} actions=$actions" +
            (if (failed.isNotEmpty()) " failed=$failed" else "") + (if (needs.isNotEmpty()) " needs=$needs" else ""))
        return Report(filled, needs, failed, actions, submitId, submitLabel, values)
    }

    private data class Applied(val ok: Boolean, val actions: Int, val value: String? = null, val problem: String = "", val options: List<String> = emptyList())

    private suspend fun apply(snap: ScreenSnapshot, f: FormField, target: String, page: String): Applied {
        val caption = "Filling in ${f.label.lowercase()}"
        when (f.kind) {
            FieldKind.TEXT -> {
                val node = snap.nodes.firstOrNull { it.id == f.nodeId } ?: return Applied(false, 0, problem = "field not on screen")
                val r = act(DeviceAction.EnterText(node.id, target), snap, node, caption)
                if (r is ActionResult.Done) ledger.recordText(snap, node, target)
                return Applied(r is ActionResult.Done, 1, target, problem = r.toString())
            }
            FieldKind.CHOICE -> {
                val opt = f.options.firstOrNull { sameChoice(it.label, target) }
                    ?: return Applied(false, 0, problem = "no option like '$target'", options = f.options.map { it.label })
                val node = snap.nodes.first { it.id == opt.nodeId }
                if (AgentLoop.looksLikeCommit(snap, node)) return Applied(false, 0, problem = "option looks like a submit button")
                val r = act(DeviceAction.Tap(node.id), snap, node, "Choosing ${opt.label}")
                if (r is ActionResult.Done) ledger.recordChoice(snap, node, "${f.label}: ${opt.label}")
                return Applied(r is ActionResult.Done, 1, opt.label, problem = r.toString())
            }
            FieldKind.CHECKBOX -> {
                val want = target.lowercase() in setOf("yes", "true", "checked", "on", "agree", "i agree")
                val node = snap.nodes.firstOrNull { it.id == f.nodeId } ?: return Applied(false, 0, problem = "checkbox not on screen")
                if (node.checked == want) return Applied(true, 0, if (want) "checked" else "unchecked")
                val r = act(DeviceAction.Tap(node.id), snap, node, caption)
                return Applied(r is ActionResult.Done, 1, if (want) "checked" else "unchecked", problem = r.toString())
            }
            FieldKind.SELECT -> {
                val node = snap.nodes.firstOrNull { it.id == f.nodeId } ?: return Applied(false, 0, problem = "dropdown not on screen")
                val open = act(DeviceAction.Tap(node.id), snap, node, "Opening ${f.label.lowercase()}")
                if (open !is ActionResult.Done) return Applied(false, 1, problem = "couldn't open the dropdown")
                val popup = open.after
                val opts = FormReader.selectOptions(popup)
                val opt = opts.firstOrNull { sameChoice(it.label, target) }
                if (opt == null) {
                    act(DeviceAction.Back, popup, null, "Closing the list")
                    return Applied(false, 2, problem = "no option like '$target'", options = opts.map { it.label }.filterNot { it.startsWith("Choose", true) })
                }
                val optNode = popup.nodes.first { it.id == opt.nodeId }
                val r = act(DeviceAction.Tap(optNode.id), popup, optNode, "Choosing ${opt.label}")
                if (r is ActionResult.Done) {
                    chosenInSelects[page + "|" + f.key] = opt.label
                    ledger.recordChoice(popup, optNode, "${f.label}: ${opt.label}")
                }
                return Applied(r is ActionResult.Done, 2, opt.label, problem = r.toString())
            }
        }
    }

    /**
     * Options of the dropdown [field] on [snap], opening it once if they aren't known yet (cached in
     * [knownOptions]). For showing and speaking the choices when SuuS asks about it.
     */
    suspend fun optionsOf(snap: ScreenSnapshot, field: FormField): List<String> {
        knownOptions[field.label]?.let { return it }
        if (field.kind != FieldKind.SELECT) return field.options.map { it.label }
        val opts = readSelectOptions(snap, field).first
        if (opts.isNotEmpty()) knownOptions[field.label] = opts
        return opts
    }

    /** Opens a dropdown just to read its options, then closes it. Returns (options, actions used). */
    private suspend fun readSelectOptions(snap: ScreenSnapshot, f: FormField): Pair<List<String>, Int> {
        val node = snap.nodes.firstOrNull { it.id == f.nodeId } ?: return emptyList<String>() to 0
        val open = act(DeviceAction.Tap(node.id), snap, node, "Checking the ${f.label.lowercase()} options")
        if (open !is ActionResult.Done) return emptyList<String>() to 1
        val opts = FormReader.selectOptions(open.after).map { it.label }.filterNot { it.startsWith("Choose", true) || it.startsWith("Select", true) }
        act(DeviceAction.Back, open.after, null, "Closing the list")
        return opts to 2
    }

    private fun withChosenSelects(form: FormModel, page: String) = form.copy(
        fields = form.fields.map { f ->
            if (f.kind == FieldKind.SELECT && f.value == null) chosenInSelects[page + "|" + f.key]?.let { f.copy(value = it) } ?: f else f
        },
    )

    private fun pageKey(s: ScreenSnapshot): String =
        s.nodes.firstOrNull { it.viewId == "com.android.chrome:id/url_bar" }?.text?.substringBefore('?') ?: s.packageName
}
