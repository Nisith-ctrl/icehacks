package com.icehacks.saythegoal.voice

import com.icehacks.saythegoal.core.UserCommand

/** Rule-based and fast. The agent does the deeper understanding; this only routes utterances. */
object CommandParser {

    /**
     * The wake word is **"Suus"** (said like "soose"), optionally after "hey"/"hi"/"ok".
     * Decision 2026-11-10-wake-word-suus. Real speech is matched by sound, see [afterWake].
     */
    const val WAKE_WORD = "Suus"

    /** Sent to the recognizer as biasing hints (API 33+), so it is likelier to return "suus". */
    val BIASING_PHRASES = arrayListOf("suus", "hey suus", "Suus")

    /** Optional words before the wake word. */
    private val LEADS = setOf("hey", "hay", "hi", "ok", "okay")

    /**
     * "Suus" as the recognizer may spell it: an s/z sound, an "oo" vowel in any common spelling,
     * then an s/z sound. Matches suus, sous, soos, sues, sue's, seuss, zeus, zoos, sus, suss, suse.
     */
    private val SUUS = Regex("^[sz](u|uu|oo|ou|ue|eu|ew|oe|ui)(s|ss|se|z|zz|ze|ce)s?$")

    private val STOP_WORDS = setOf(
        "stop", "cancel", "stop it", "stop that", "stop now", "never mind", "nevermind", "cancel that", "abort",
    )

    fun normalize(text: String): String = text.lowercase()
        .replace("e-mail", "email")
        .replace(Regex("[^a-z0-9' ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * If [text] starts with the wake word, returns the rest (possibly empty); otherwise null.
     *
     * "Suus" isn't a word the recognizer knows, so it comes back as "sous", "Seuss", "Zeus",
     * "Sue's", or split in two ("sue us", "su us"). All of those count, with or without a lead
     * word ("hey Suus"). A false wake can't commit anything: Submit/Send still need spoken approval.
     */
    fun afterWake(text: String): String? {
        val original = text.trim().split(Regex("\\s+"))
        val pairs = original.map { it to normalize(it) }.filter { it.second.isNotEmpty() }
        val w = pairs.map { it.second }
        fun rest(consumed: Int) = pairs.drop(consumed).joinToString(" ") { it.first }.trimStart(',', '.', ' ')
        if (w.isEmpty()) return null

        val start = if (w[0] in LEADS && w.size >= 2) 1 else 0
        // One word: "suus", "sous", "Seuss", "hey Zeus"
        if (soundsLikeSuus(w[start])) return rest(start + 1)
        // Split in two: "sue us", "su us", "sue s", "soo s"
        val next = w.getOrNull(start + 1)
        if (next != null && w[start].length <= 3 && soundsLikeSuus(w[start] + next)) return rest(start + 2)
        if (next == "us" && w[start] in setOf("sue", "su", "soo", "zoo")) return rest(start + 2)
        return null
    }

    /** True if [word] sounds like the wake word "Suus" (see [SUUS]). */
    fun soundsLikeSuus(word: String): Boolean = SUUS.matches(word.replace("'", ""))

    fun classify(text: String): UserCommand {
        val n = normalize(text)
        return when {
            n in STOP_WORDS || n.startsWith("stop ") && n.split(" ").size <= 3 -> UserCommand.Stop
            n.startsWith("undo") || n.startsWith("take back") || n.startsWith("remove what you") -> UserCommand.Undo(text.trim())
            n.startsWith("what do you remember") || n.startsWith("what have you saved") ||
                n.startsWith("what did you save") || n.startsWith("forget") -> UserCommand.Memory(text.trim())
            n.startsWith("take over") || n.contains(" take over") -> UserCommand.Goal(text.trim(), takeOver = true)
            else -> UserCommand.Goal(text.trim(), takeOver = false)
        }
    }
}
