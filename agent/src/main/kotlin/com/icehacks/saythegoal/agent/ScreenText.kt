package com.icehacks.saythegoal.agent

import com.icehacks.saythegoal.core.ScreenSnapshot
import com.icehacks.saythegoal.core.UiNode

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

    fun render(s: ScreenSnapshot): String {
        val byId = s.nodes.associateBy { it.id }
        val lines = mutableListOf<String>()
        lines += "app: ${appName(s.packageName)} (${s.packageName})" + (s.title?.let { "  title: \"${clip(it)}\"" } ?: "")
        chromeUrl(s)?.let { lines += "url: $it" }
        for (n in s.nodes) {
            if (!useful(n)) continue
            val parts = mutableListOf(n.id, n.className.substringAfterLast('.'))
            n.text?.takeIf { it.isNotBlank() }?.let { parts += "\"${clip(it)}\"" }
            n.contentDescription?.takeIf { it.isNotBlank() && it != n.text }?.let { parts += "desc=\"${clip(it)}\"" }
            n.hint?.takeIf { it.isNotBlank() && it != n.text }?.let { parts += "hint=\"${clip(it)}\"" }
            n.labeledById?.let { byId[it] }?.let { l -> (l.text ?: l.contentDescription)?.let { parts += "label=\"${clip(it)}\"" } }
            val flags = mutableListOf<String>()
            if (n.editable) flags += "edit"
            if (n.checkable) flags += if (n.checked) "check, checked" else "check"
            else if (n.clickable) flags += "tap"
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
        pkg == "com.icehacks.saythegoal" -> "Say the Goal"
        else -> pkg
    }

    private fun clip(t: String) = t.replace('\n', ' ').replace("\"", "'").let { if (it.length > MAX_TEXT) it.take(MAX_TEXT) + "…" else it }
}
