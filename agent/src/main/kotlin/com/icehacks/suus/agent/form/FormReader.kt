package com.icehacks.suus.agent.form

import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.UiNode

/** What a form field is, as far as we can tell from Chrome's accessibility tree. */
enum class FieldKind { TEXT, CHOICE, SELECT, CHECKBOX }

data class FormOption(val label: String, val nodeId: String, val checked: Boolean)

data class FormField(
    /** Visible label without markers, e.g. "Full name" (from "Full name (required)"). */
    val label: String,
    val kind: FieldKind,
    val required: Boolean,
    /** The node to act on: the text box, the dropdown, or the checkbox. Null for radio groups. */
    val nodeId: String?,
    /** Radio options (CHOICE) with their checked state. Empty for SELECT until it's opened. */
    val options: List<FormOption> = emptyList(),
    /** Current text, the checked radio's label, or "checked" for a ticked checkbox. Null if empty. */
    val value: String? = null,
) {
    val key get() = normalizeLabel(label)
}

data class FormModel(
    val title: String?,
    val fields: List<FormField>,
    /** The submit/register/send button, if it's on screen. */
    val submitNodeId: String?,
    val submitLabel: String?,
)

/**
 * Reads the fields of a web form from one snapshot of Chrome. Generic: no per-form labels or
 * ids, so a form with different wording and order is read the same way.
 *
 * What Chrome exposes (checked on the live test forms, Chrome 124):
 * - text inputs: `EditText` with the label in `hint` (or a `View` label right before it); value in `text`
 * - radio groups: a `View` (the fieldset, its text = the legend) whose children are `RadioButton`s
 * - `<select>`: a clickable `View` whose text is the label; the chosen value is NOT exposed, and
 *   opening it shows a separate window of `CheckedTextView` options
 * - checkboxes: `CheckBox`
 * Only nodes on screen are in a snapshot, so a long form is read in parts while scrolling.
 */
object FormReader {

    private val COMMIT = Regex("^(submit|send|register|sign up|sign me up|confirm|book|join|rsvp)\\b", RegexOption.IGNORE_CASE)

    fun read(s: ScreenSnapshot): FormModel {
        val web = s.nodes.firstOrNull { it.className.endsWith("WebView") }
        val byId = s.nodes.associateBy { it.id }
        val inPage = if (web == null) s.nodes else s.nodes.filter { isUnder(it, web.id, byId) }

        val fields = mutableListOf<FormField>()
        val seenGroups = mutableSetOf<String>()
        var lastLabel: String? = null
        var submit: UiNode? = null

        for (n in inPage) {
            val text = n.text?.trim().orEmpty()
            when {
                n.className.endsWith("RadioButton") -> {
                    val group = n.parentId ?: n.id
                    if (group in seenGroups) continue
                    seenGroups += group
                    val radios = inPage.filter { it.parentId == group && it.className.endsWith("RadioButton") }
                    val legend = byId[group]?.text?.takeIf { it.isNotBlank() }
                        ?: inPage.firstOrNull { it.parentId == group && !it.checkable && !it.text.isNullOrBlank() }?.text
                        ?: lastLabel ?: "Choice"
                    val options = radios.map { FormOption(it.text?.trim().orEmpty(), it.id, it.checked) }
                    fields += FormField(clean(legend), FieldKind.CHOICE, isRequired(legend), null, options,
                        options.firstOrNull { it.checked }?.label)
                    lastLabel = null
                }
                n.className.endsWith("CheckBox") -> {
                    val label = text.ifBlank { lastLabel ?: "Checkbox" }
                    fields += FormField(clean(label), FieldKind.CHECKBOX, isRequired(label), n.id,
                        value = if (n.checked) "checked" else null)
                    lastLabel = null
                }
                n.editable -> {
                    val label = n.hint?.takeIf { it.isNotBlank() }
                        ?: n.labeledById?.let { byId[it]?.text }
                        ?: lastLabel ?: continue
                    val value = text.takeIf { it.isNotBlank() && it != n.hint }
                    fields += FormField(clean(label), FieldKind.TEXT, isRequired(label), n.id, value = value)
                    lastLabel = null
                }
                n.className.endsWith("Button") && COMMIT.containsMatchIn(text) -> submit = n
                // A <select>: clickable, not a button, its text repeats the label just above it.
                n.clickable && !n.checkable && text.isNotBlank() && lastLabel != null && clean(text) == clean(lastLabel!!) -> {
                    fields += FormField(clean(text), FieldKind.SELECT, isRequired(text), n.id)
                    lastLabel = null
                }
                !n.clickable && !n.checkable && text.isNotBlank() -> lastLabel = text
            }
        }
        val title = inPage.firstOrNull { it.className.endsWith("TextView") && !it.text.isNullOrBlank() }?.text
        return FormModel(title, fields, submit?.id, submit?.text?.trim())
    }

    /** The options of an opened `<select>` popup (CheckedTextView rows), or empty if none is open. */
    fun selectOptions(s: ScreenSnapshot): List<FormOption> =
        s.nodes.filter { it.className.endsWith("CheckedTextView") && !it.text.isNullOrBlank() }
            .map { FormOption(it.text!!.trim(), it.id, it.checked) }

    fun isRequired(label: String) =
        label.contains("(required)", ignoreCase = true) || label.trimEnd().endsWith("*")

    /** "Full name (required)" -> "Full name"; "Email *" -> "Email". */
    fun clean(label: String) = label
        .replace(Regex("\\(required\\)", RegexOption.IGNORE_CASE), "")
        .replace("*", "")
        .replace(Regex("\\s+"), " ")
        .trim().trimEnd(':', '?').trim()

    private fun isUnder(n: UiNode, ancestorId: String, byId: Map<String, UiNode>): Boolean {
        var p = n.parentId
        while (p != null) {
            if (p == ancestorId) return true
            p = byId[p]?.parentId
        }
        return false
    }
}

/** Lower-case, letters and digits only: for matching labels across scrolls and forms. */
fun normalizeLabel(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
