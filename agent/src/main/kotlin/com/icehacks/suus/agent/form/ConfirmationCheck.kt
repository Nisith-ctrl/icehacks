package com.icehacks.suus.agent.form

import com.icehacks.suus.core.ScreenSnapshot

/**
 * Checks, in code, that the page after a submit is a confirmation that shows what was sent.
 * The agent only says "you're registered" when this passes; otherwise it says it isn't sure.
 */
object ConfirmationCheck {

    private val CONFIRMED = Regex(
        "\\b(registration confirmed|confirmed|you're signed up|you are signed up|you're registered|you are registered|thank you|thanks for signing up|success)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val NUMBER = Regex("\\b[A-Z]{2,5}-[A-Z0-9]{3,}\\b")
    private val ERROR = Regex("\\b(error|required|please (fill|enter|choose)|missing|invalid)\\b", RegexOption.IGNORE_CASE)

    data class Result(
        val confirmed: Boolean,
        val heading: String?,
        val number: String?,
        val matched: List<String>,
        val missing: List<String>,
    ) {
        fun describe(): String = when {
            confirmed -> "Confirmation page verified: \"$heading\"" + (number?.let { ", confirmation number $it" } ?: "") +
                ". Shows ${matched.size} of ${matched.size + missing.size} submitted values" +
                (if (missing.isNotEmpty()) " (not shown: ${missing.joinToString()})" else "") + "."
            heading != null -> "The page says \"$heading\" but doesn't show what was submitted (missing: ${missing.joinToString()}). Tell the user you're not sure it went through."
            else -> "I can't see a confirmation on this page. Tell the user you're not sure it went through, and don't submit again."
        }
    }

    fun check(after: ScreenSnapshot, submitted: Map<String, String>): Result {
        // The page's own text: not the address bar, and nothing that is just a URL.
        val pageNodes = after.nodes.filter { it.viewId != "com.android.chrome:id/url_bar" && !it.editable }
        val texts = (pageNodes.mapNotNull { it.text?.trim() } + pageNodes.mapNotNull { it.contentDescription?.trim() })
            .filter { it.isNotEmpty() && !(it.contains('/') && !it.contains(' ')) }
        val heading = texts.firstOrNull { CONFIRMED.containsMatchIn(it) && !ERROR.containsMatchIn(it) && it.length < 80 }
        val number = texts.firstNotNullOfOrNull { NUMBER.find(it)?.value }
        val all = texts.joinToString("\n").lowercase()
        val (matched, missing) = submitted.entries.filter { it.value.isNotBlank() }.partition { (_, v) -> shown(all, v) }
        val confirmed = heading != null && (submitted.isEmpty() || matched.size * 2 >= submitted.size)
        return Result(confirmed, heading, number, matched.map { it.key }, missing.map { it.key })
    }

    /** A value counts as shown if it, or its first distinctive part (e.g. the weekday of a session), is on the page. */
    private fun shown(page: String, value: String): Boolean {
        val v = value.lowercase().trim()
        if (page.contains(v)) return true
        val head = v.split(',').first().trim()
        return head.length >= 4 && page.contains(head)
    }
}
