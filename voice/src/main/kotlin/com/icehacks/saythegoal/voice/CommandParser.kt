package com.icehacks.saythegoal.voice

import com.icehacks.saythegoal.core.UserCommand

/** Rule-based and fast. The agent does the deeper understanding; this only routes utterances. */
object CommandParser {

    /** Wake phrases as people say them. Real speech is matched loosely, see [afterWake]. */
    val WAKE_PHRASES = listOf("hey say the goal", "okay say the goal", "say the goal", "hey goal", "ok goal", "okay goal")

    /** Sent to the recognizer as biasing hints (API 33+), so it is likelier to hear these words. */
    val BIASING_PHRASES = arrayListOf("hey goal", "say the goal", "ok goal")

    /** Words heard before "goal": "hey goal", "hi goal", "ok goal", "a goal" (a common mishearing). */
    private val LEADS = setOf("hey", "hay", "hi", "a", "ok", "okay")

    /** What the recognizer actually returns for "goal" (seen in real speech: "girl", "go"). */
    private val GOAL_MISHEARD = setOf("girl", "go", "gall", "gal", "gul", "gold", "gol", "goel", "gool", "ghoul")

    private val STOP_WORDS = setOf(
        "stop", "cancel", "stop it", "stop that", "stop now", "never mind", "nevermind", "cancel that", "abort",
    )

    fun normalize(text: String): String = text.lowercase()
        .replace("e-mail", "email")
        .replace(Regex("[^a-z0-9' ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * If [text] starts with a wake phrase, returns the rest (possibly empty); otherwise null.
     *
     * Real speech mishears "Hey Goal" ("hey girl", "hey gold", "Google …"), so this matches
     * loosely: a lead word plus anything that sounds like "goal", "say the <goal-ish>", or a leading
     * "Google"/"hey Google" that is followed by a command (a bare "OK Google" belongs to Assistant).
     * A false wake can't commit anything: Submit/Send still need spoken approval.
     */
    fun afterWake(text: String): String? {
        val original = text.trim().split(Regex("\\s+"))
        val pairs = original.map { it to normalize(it) }.filter { it.second.isNotEmpty() }
        val w = pairs.map { it.second }
        fun rest(consumed: Int) = pairs.drop(consumed).joinToString(" ") { it.first }.trimStart(',', '.', ' ')
        if (w.isEmpty()) return null

        // "say the goal" / "hey say the goal" / "okay say the goal"
        val sayAt = if (w.size >= 4 && w[0] in LEADS && w[1] == "say") 1 else if (w.size >= 3 && w[0] == "say") 0 else -1
        if (sayAt >= 0 && w.getOrNull(sayAt + 1) == "the" && w.getOrNull(sayAt + 2)?.let(::soundsLikeGoal) == true) {
            return rest(sayAt + 3)
        }
        // "hey goal", "hi gold", "a goal", "hey girl", "okay goal's"
        if (w.size >= 2 && w[0] in LEADS && soundsLikeGoal(w[1])) return rest(2)
        // "Google, open Gmail" / "hey Google what do you remember": only with a command after it.
        val googleAt = when {
            w[0] == "google" -> 1
            w.size >= 2 && w[0] in LEADS && w[1] == "google" -> 2
            else -> 0
        }
        if (googleAt > 0) return rest(googleAt).ifBlank { null }
        return null
    }

    /** "goal" as the recognizer hears it: goal, goals, goal's, and near misses like gold/gol/girl. */
    fun soundsLikeGoal(word: String): Boolean {
        val w = word.removeSuffix("'s").let { if (it.length > 3 && it.endsWith("s")) it.dropLast(1) else it }
        return w == "goal" || w in GOAL_MISHEARD || editDistance(w, "goal") <= 1
    }

    private fun editDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return d[a.length][b.length]
    }

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
