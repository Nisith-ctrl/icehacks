package com.icehacks.suus.voice

/**
 * Maps a spoken answer to one of the offered choices: "the second one" -> choices[1],
 * "email please" -> "Email", "use the one by the door" -> "The one by the door".
 * Returns null when nothing (or more than one choice) matches clearly.
 */
object ChoiceMatcher {

    private val ORDINALS = mapOf(
        "first" to 0, "1st" to 0, "one" to 0, "second" to 1, "2nd" to 1, "two" to 1,
        "third" to 2, "3rd" to 2, "three" to 2, "fourth" to 3, "4th" to 3, "four" to 3,
    )

    private val FILLER = setOf(
        "the", "a", "an", "one", "please", "use", "that", "this", "i", "want", "would", "like", "pick", "choose",
        "go", "with", "by", "in", "on", "at", "of", "to", "it", "is", "my", "me", "for", "and", "or", "um", "uh",
        "just", "option", "yes", "ok", "okay", "lets", "let's", "number",
    )

    /**
     * What VoiceIO.ask returns when choices were offered: the matched choice when the answer is
     * just a choice ("the second one", "email please"); the raw transcript when it says more
     * ("Email. Remember that for event signups.") or matches nothing, so the agent can interpret it.
     */
    fun resolve(answer: String, choices: List<String>): String {
        val choice = match(answer, choices) ?: return answer
        val choiceTokens = tokens(choice)
        val extra = CommandParser.normalize(answer).split(" ")
            .filter { it.isNotBlank() && it !in FILLER && it !in ORDINALS && it != "last" && it !in choiceTokens }
        return if (extra.isEmpty()) choice else answer
    }

    fun match(answer: String, choices: List<String>): String? {
        if (choices.isEmpty()) return null
        val a = CommandParser.normalize(answer)
        val words = a.split(" ").filter { it.isNotBlank() }

        // 1. A choice said in full (longest wins, so "text message" beats "text").
        val whole = choices.filter { c -> containsPhrase(a, CommandParser.normalize(c)) }
        if (whole.size == 1) return whole[0]
        if (whole.size > 1) return whole.maxBy { it.length }

        // 2. Ordinals: "the second one", "the last one", "number two".
        if ("last" in words) return choices.last()
        val ordinal = words.firstNotNullOfOrNull { ORDINALS[it] }
        val mentionsOrdinal = words.any { it in ORDINALS && it !in setOf("one") } ||
            (words.contains("one") && words.size <= 3 && words.none { w -> choices.any { tokens(it).contains(w) && w != "one" } })
        if (ordinal != null && mentionsOrdinal && ordinal < choices.size) return choices[ordinal]

        // 3. Content-word overlap: "by the door" -> "The one by the door"; "text" -> "Text message".
        //    Also "set up" -> "Setup crew" (spaces dropped) and "food service" -> "Food serving" (same stem).
        val answerTokens = words.filter { it !in FILLER }.toSet()
        if (answerTokens.isEmpty()) return null
        val compact = a.replace(" ", "")
        fun hit(t: String) = t in answerTokens || (t.length >= 4 && compact.contains(t)) ||
            (t.length >= 5 && answerTokens.any { w -> w.length >= 5 && w.take(5) == t.take(5) })
        val scores = choices.associateWith { c -> tokens(c).count { hit(it) } }
        val best = scores.values.max()
        if (best == 0) return null
        val top = scores.filterValues { it == best }.keys
        return top.singleOrNull()
    }

    private fun tokens(choice: String) =
        CommandParser.normalize(choice).split(" ").filter { it.isNotBlank() && it !in FILLER }.toSet()

    private fun containsPhrase(text: String, phrase: String) =
        phrase.isNotBlank() && (" $text ").contains(" $phrase ")
}
