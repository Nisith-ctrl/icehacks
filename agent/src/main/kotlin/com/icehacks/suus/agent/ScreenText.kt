package com.icehacks.suus.agent

import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.UiNode

/**
 * Compact text view of a snapshot for the model: one line per useful node, e.g.
 *   n7 EditText "Alex" hint="Full name" label="Full name" [edit]
 *   n12 RadioButton "Thursday, Nov 12, 10:00" [check, checked]
 *   n20 Button "Submit" [tap]
 * Layout-only nodes (no text, not interactive) are dropped.
 */
object ScreenText {
    private const val MAX_LINES = 160 // token budget: Groq free tier is ~8K tokens/min
    private const val MAX_TEXT = 140

    /**
     * @param skipFormFields when a form summary is sent, leave out the text boxes, radios and
     *   their labels inside the page (fill_form handles them); buttons, links and headings stay.
     */
    fun render(s: ScreenSnapshot, skipFormFields: Boolean = false): String {
        val byId = s.nodes.associateBy { it.id }
        val web = if (skipFormFields) s.nodes.firstOrNull { it.className.endsWith("WebView") }?.id else null
        fun inWeb(n: UiNode): Boolean {
            var p = n.parentId
            while (p != null) { if (p == web) return true; p = byId[p]?.parentId }
            return false
        }
        val lines = mutableListOf<String>()
        lines += "app: ${appName(s.packageName)} (${s.packageName})" + (s.title?.let { "  title: \"${clip(it)}\"" } ?: "")
        chromeUrl(s)?.let { lines += "url: $it" }
        for (n in s.nodes) {
            if (!useful(n)) continue
            if (web != null && inWeb(n) && (n.editable || n.checkable || (!n.clickable && n.className.endsWith(".View")))) continue
            val parts = mutableListOf(n.id, n.className.substringAfterLast('.'))
            n.text?.takeIf { it.isNotBlank() }?.let { parts += "\"${clip(it)}\"" }
            n.contentDescription?.takeIf { it.isNotBlank() && it != n.text }?.let { parts += "desc=\"${clip(it)}\"" }
            n.hint?.takeIf { it.isNotBlank() && it != n.text }?.let { parts += "hint=\"${clip(it)}\"" }
            n.labeledById?.let { byId[it] }?.let { l -> (l.text ?: l.contentDescription)?.let { parts += "label=\"${clip(it)}\"" } }
            val flags = mutableListOf<String>()
            if (n.editable) flags += "edit"
            if (n.checkable) flags += if (n.checked) "check, checked" else "check"
            else if (n.clickable || labelOfClickableParent(n, byId)) flags += "tap"
            if (n.scrollable) flags += "scroll"
            if (n.selected) flags += "selected"
            if (n.focused) flags += "focused"
            if (!n.enabled) flags += "disabled"
            if (flags.isNotEmpty()) parts += "[${flags.joinToString(", ")}]"
            lines += parts.joinToString(" ")
            if (lines.size >= MAX_LINES) {
                lines += "… (${s.nodes.size} nodes, list cut; scroll or be more specific)"
                break
            }
        }
        return lines.joinToString("\n")
    }

    /** Compose apps put the label on a child of the clickable node: tapping the child works too. */
    private fun labelOfClickableParent(n: UiNode, byId: Map<String, UiNode>): Boolean {
        val p = n.parentId?.let { byId[it] } ?: return false
        return p.clickable && p.text.isNullOrBlank() && p.contentDescription.isNullOrBlank() &&
            (!n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank())
    }

    fun useful(n: UiNode) =
        !n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank() || !n.hint.isNullOrBlank() ||
            n.editable || n.checkable || n.scrollable || (n.clickable && n.labeledById != null)

    /** Chrome's address bar text, if this is Chrome. */
    fun chromeUrl(s: ScreenSnapshot): String? =
        if (s.packageName != "com.android.chrome") null
        else s.nodes.firstOrNull { it.viewId == "com.android.chrome:id/url_bar" }?.text

    fun host(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()

    fun appName(pkg: String) = LAUNCHABLE_APPS.entries.firstOrNull { it.value == pkg }?.key ?: when {
        pkg.contains("launcher") -> "home screen"
        pkg.contains("media.module") || pkg.contains("documentsui") -> "photo picker"
        pkg == "com.icehacks.suus" -> "SuuS"
        else -> pkg
    }

    private fun clip(t: String) = t.replace('\n', ' ').replace("\"", "'").let { if (it.length > MAX_TEXT) it.take(MAX_TEXT) + "…" else it }
}
