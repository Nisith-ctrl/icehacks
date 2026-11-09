package com.icehacks.saythegoal.agent

import com.icehacks.saythegoal.core.ScreenSnapshot
import com.icehacks.saythegoal.core.UiNode

/**
 * Records the agent's own draft edits (never the user's), so one specific edit can be undone
 * later while everything else stays. Cleared when a draft is submitted or sent.
 */
class DraftLedger {
    enum class Kind { TEXT, CHOICE, ATTACHMENT }

    data class Edit(
        val id: Int,
        val kind: Kind,
        val label: String,           // "dog photo (yard)", "Email field"
        val packageName: String,
        val beforeText: String?,     // TEXT: field text before
        val afterText: String?,      // TEXT: text we set; CHOICE: option label
        val marker: String?,         // ATTACHMENT: content description of the attachment's remove control
        val fieldKey: String?,       // TEXT: viewId or hint/label used to find the field again
    )

    private val edits = mutableListOf<Edit>()
    private var nextId = 1

    fun all(): List<Edit> = edits.toList()
    fun isEmpty() = edits.isEmpty()
    fun clear() = edits.clear()
    fun remove(e: Edit) = edits.remove(e)

    fun recordText(before: ScreenSnapshot, node: UiNode, newText: String) {
        val label = fieldLabel(before, node)
        edits += Edit(nextId++, Kind.TEXT, label, before.packageName, node.text, newText, null, node.viewId ?: node.hint ?: label)
    }

    fun recordChoice(before: ScreenSnapshot, node: UiNode, label: String?) {
        edits += Edit(nextId++, Kind.CHOICE, label ?: (node.text ?: node.contentDescription ?: "an option"),
            before.packageName, null, node.text ?: node.contentDescription, null, null)
    }

    /**
     * After a tap, detect an attachment the tap added: a new "remove/delete … attachment/image/photo"
     * control that wasn't on the screen before. Returns the edit if one was recorded.
     */
    fun detectAttachment(before: ScreenSnapshot, after: ScreenSnapshot, label: String?): Edit? {
        val old = before.nodes.mapNotNull { removeControl(it) }.toSet()
        val added = after.nodes.mapNotNull { removeControl(it) }.firstOrNull { it !in old } ?: return null
        val e = Edit(nextId++, Kind.ATTACHMENT, label ?: "the attachment", after.packageName, null, null, added, null)
        edits += e
        return e
    }

    /** Pick the edit an undo request refers to: by kind words, then by label words, else the latest. */
    fun pick(request: String): Edit? {
        if (edits.isEmpty()) return null
        val r = request.lowercase()
        val wantsAttachment = Regex("photo|picture|image|attachment|pic\\b").containsMatchIn(r)
        if (wantsAttachment) edits.lastOrNull { it.kind == Kind.ATTACHMENT }?.let { return it }
        val words = r.split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it != "undo" && it != "the" }
        edits.lastOrNull { e -> words.any { e.label.lowercase().contains(it) } }?.let { return it }
        return if (wantsAttachment) null else edits.last()
    }

    /** Does the current screen still show this edit's result? (Otherwise undo must not touch anything.) */
    fun stillApplied(e: Edit, now: ScreenSnapshot): Boolean = when (e.kind) {
        Kind.ATTACHMENT -> now.nodes.any { removeControl(it) == e.marker }
        Kind.TEXT -> now.nodes.any { it.editable && (it.text ?: "") == (e.afterText ?: "") }
        Kind.CHOICE -> now.nodes.any { (it.text ?: it.contentDescription) == e.afterText && (it.checked || it.selected) }
    }

    companion object {
        private val REMOVE = Regex("(remove|delete|discard|clear)", RegexOption.IGNORE_CASE)
        private val MEDIA = Regex("(attachment|image|photo|picture|media|video)", RegexOption.IGNORE_CASE)

        fun removeControl(n: UiNode): String? {
            val d = n.contentDescription ?: n.text ?: return null
            return if (REMOVE.containsMatchIn(d) && MEDIA.containsMatchIn(d)) d else null
        }

        fun fieldLabel(s: ScreenSnapshot, n: UiNode): String {
            n.labeledById?.let { id -> s.nodes.firstOrNull { it.id == id }?.let { l -> (l.text ?: l.contentDescription)?.let { return it } } }
            return n.hint ?: n.contentDescription ?: n.viewId?.substringAfterLast('/') ?: "a field"
        }

        /** Every editable field's text (for "nothing else changed" checks), excluding [except]. */
        fun fieldTexts(s: ScreenSnapshot, except: String? = null): List<String> =
            s.nodes.filter { it.editable }.mapNotNull { it.text }.filter { it.isNotBlank() && it != except }
    }
}
