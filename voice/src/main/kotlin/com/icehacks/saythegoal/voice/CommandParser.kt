package com.icehacks.saythegoal.voice

import com.icehacks.saythegoal.core.UserCommand

/** Rule-based and fast. The agent does the deeper understanding; this only routes utterances. */
object CommandParser {

    /** Wake phrases, already normalized. Longest first so "hey say the goal" wins over "say the goal". */
    val WAKE_PHRASES = listOf("hey say the goal", "okay say the goal", "say the goal", "hey goal", "ok goal", "okay goal")

    private val STOP_WORDS = setOf(
        "stop", "cancel", "stop it", "stop that", "stop now", "never mind", "nevermind", "cancel that", "abort",
    )

    fun normalize(text: String): String = text.lowercase()
        .replace("e-mail", "email")
        .replace(Regex("[^a-z0-9' ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** If [text] starts with a wake phrase, returns the rest (possibly empty); otherwise null. */
    fun afterWake(text: String): String? {
        val n = normalize(text)
        for (w in WAKE_PHRASES) {
            if (n == w) return ""
            if (n.startsWith("$w ")) {
                // Return the original casing after the phrase: drop as many words as the phrase has.
                val words = text.trim().split(Regex("\\s+"))
                return words.drop(w.split(" ").size).joinToString(" ").trimStart(',', '.', ' ')
            }
        }
        return null
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
