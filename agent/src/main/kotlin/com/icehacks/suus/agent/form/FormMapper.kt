package com.icehacks.suus.agent.form

import com.icehacks.suus.agent.Profile
import com.icehacks.suus.agent.SavedPreference
import com.icehacks.suus.agent.scopeLabel

/** Where a field's value comes from. */
enum class Source { PROFILE, PREFERENCE, CHOSEN_OPTION, ANSWER, ASK, SKIP }

data class FieldPlan(
    val field: FormField,
    val source: Source,
    /** The value to put in, if known (a profile value, a preference, or the user's answer). */
    val target: String?,
    /** Why, in a few words, for the model and the logs. */
    val reason: String,
    /** The preference used, if any (so the agent can say "using your saved preference"). */
    val preference: SavedPreference? = null,
) {
    /** True if the field already holds the target (or, without a target, has any value). */
    val done: Boolean get() {
        val current = this.field.value ?: return false
        return target == null || sameChoice(current, target)
    }
}

/**
 * Decides, per field, where the value comes from. Generic by meaning, not by wording:
 * "Full name" / "Your name" -> the profile name; "Email" / "Email address" -> profile email;
 * "Phone" / "Mobile number" -> profile phone; a choice whose options are dates or times
 * ("Session", "Shift") -> the option picked from the calendar; a choice that offers a saved
 * preference's value and is about the same thing -> that preference; any other required
 * field -> ask the user. Optional fields without a value are skipped.
 */
object FormMapper {

    private val NAME = Regex("\\b(full name|your name|name)\\b", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("\\be-?mail\\b", RegexOption.IGNORE_CASE)
    private val PHONE = Regex("\\b(phone|mobile|cell|telephone|tel)\\b", RegexOption.IGNORE_CASE)
    private val DATE_TIME = Regex(
        "\\b(mon|tues?|wed(nes)?|thu(rs)?|fri|sat(ur)?|sun)(day)?\\b|\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\b|\\d{1,2}:\\d{2}|\\b\\d{1,2}\\s?(am|pm)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @param answers values the model or user supplied, keyed by field label (any wording that
     *   [sameLabel] matches), e.g. {"Session": "Saturday, November 14…", "Preferred volunteer role": "Food serving"}.
     */
    fun plan(
        form: FormModel,
        profile: Profile,
        preferences: List<SavedPreference>,
        answers: Map<String, String> = emptyMap(),
        /** Free text that may name the chosen option (the goal, the agent's notes), e.g. "the Sunday shift". */
        hints: String = "",
    ): List<FieldPlan> = form.fields.map { f -> planField(f, profile, preferences, answers, hints) }

    private fun planField(f: FormField, profile: Profile, prefs: List<SavedPreference>, answers: Map<String, String>, hints: String): FieldPlan {
        answers.entries.firstOrNull { sameLabel(it.key, f.label) }?.let { (_, v) ->
            val target = if (f.kind == FieldKind.CHOICE) f.options.firstOrNull { sameChoice(it.label, v) }?.label ?: v else v
            return FieldPlan(f, Source.ANSWER, target, "given")
        }
        if (f.kind == FieldKind.TEXT) {
            when {
                EMAIL.containsMatchIn(f.label) -> return FieldPlan(f, Source.PROFILE, profile.email, "known email")
                PHONE.containsMatchIn(f.label) -> return FieldPlan(f, Source.PROFILE, profile.phone, "known phone")
                NAME.containsMatchIn(f.label) -> return FieldPlan(f, Source.PROFILE, profile.fullName, "known name")
            }
        }
        if (f.kind == FieldKind.CHOICE || f.kind == FieldKind.SELECT) {
            if (f.kind == FieldKind.CHOICE && f.options.size >= 2 && f.options.all { DATE_TIME.containsMatchIn(it.label) }) {
                val named = optionNamedIn(f.options, hints)
                return if (named != null) FieldPlan(f, Source.CHOSEN_OPTION, named, "the option you picked")
                else FieldPlan(f, Source.CHOSEN_OPTION, null, "dated options: use the one picked from the calendar")
            }
            preferenceFor(f, prefs)?.let { (pref, option) ->
                return FieldPlan(f, Source.PREFERENCE, option, "saved preference for ${scopeLabel(pref.scope)}", pref)
            }
            topicalPreference(f, prefs)?.let { pref ->
                return FieldPlan(f, Source.ASK, null, "the saved preference \"${pref.value}\" isn't offered here; ask the user", pref)
            }
        }
        return if (f.required) FieldPlan(f, Source.ASK, null, "required, no known value: ask the user")
        else FieldPlan(f, Source.SKIP, null, "optional")
    }

    /**
     * A saved preference applies only if the field is about the same thing (its key words appear
     * in the label, e.g. "confirmation_method" vs "How should we send your confirmation?") and the
     * field offers a matching choice. For a SELECT whose options aren't known yet, the topic match
     * is enough; the value is checked when the dropdown opens.
     */
    private fun preferenceFor(f: FormField, prefs: List<SavedPreference>): Pair<SavedPreference, String>? {
        for (p in prefs.sortedByDescending { it.savedAtMs }) {
            if (!isTopical(f, p)) continue
            if (f.kind == FieldKind.SELECT && f.options.isEmpty()) return p to p.value
            f.options.firstOrNull { sameChoice(it.label, p.value) }?.let { return p to it.label }
        }
        return null
    }

    /**
     * The one option whose first part (e.g. "Sunday, November 15" or just "Sunday") appears in
     * [hints]. Null if none or more than one is mentioned, so the model decides.
     */
    fun optionNamedIn(options: List<FormOption>, hints: String): String? {
        if (hints.isBlank()) return null
        val h = " " + normalizeLabel(hints) + " "
        val hits = options.filter { o ->
            val head = normalizeLabel(o.label.substringBefore(','))
            head.isNotEmpty() && h.contains(" $head ")
        }
        return hits.singleOrNull()?.label
    }

    /** A preference about this field's topic, whether or not its value is offered. */
    private fun topicalPreference(f: FormField, prefs: List<SavedPreference>) =
        prefs.sortedByDescending { it.savedAtMs }.firstOrNull { isTopical(f, it) }

    private fun isTopical(f: FormField, p: SavedPreference): Boolean {
        val label = normalizeLabel(f.label)
        val words = p.key.lowercase().split('_', ' ').filter { it.length > 3 && it !in setOf("method", "type", "kind") }
        return words.isNotEmpty() && words.any { label.contains(it) }
    }
}

/** "Email" vs "email", "Text message" vs "text message please", "Saturday, Nov 14…" vs "Saturday". */
fun sameChoice(a: String, b: String): Boolean {
    val x = normalizeLabel(a)
    val y = normalizeLabel(b)
    if (x.isEmpty() || y.isEmpty()) return false
    return x == y || (y.length >= 4 && x.startsWith(y)) || (x.length >= 4 && y.startsWith(x))
}

/** "Session" vs "Session (required)", "preferred volunteer role" vs "Preferred volunteer role". */
fun sameLabel(a: String, b: String): Boolean {
    val x = normalizeLabel(FormReader.clean(a))
    val y = normalizeLabel(FormReader.clean(b))
    return x == y || (x.length >= 4 && y.contains(x)) || (y.length >= 4 && x.contains(y))
}
