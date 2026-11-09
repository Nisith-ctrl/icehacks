package com.icehacks.saythegoal.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** An explicit "remember that" instruction, scoped to a kind of task. */
@Serializable
data class SavedPreference(
    val scope: String,           // e.g. "event_registration"
    val key: String,             // e.g. "confirmation_method"
    val value: String,           // e.g. "Email"
    val sourceUtterance: String, // what the user actually said
    val savedAtMs: Long,
) {
    fun describe() = "${scope.replace('_', ' ')}: ${key.replace('_', ' ')} = $value (said: \"$sourceUtterance\")"
    fun spoken() = "use ${value} for ${scopeLabel(scope)}"
}

fun scopeLabel(scope: String) = when (scope) {
    "event_registration" -> "event signups"
    else -> scope.replace('_', ' ')
}

interface PreferenceStore {
    fun all(): List<SavedPreference>
    fun save(p: SavedPreference)
    fun remove(scope: String, key: String): Boolean
    fun clear()
}

open class InMemoryPreferenceStore : PreferenceStore {
    protected val items = mutableListOf<SavedPreference>()
    @Synchronized override fun all() = items.toList()
    @Synchronized override fun save(p: SavedPreference) {
        items.removeAll { it.scope == p.scope && it.key == p.key }
        items += p
        persist()
    }
    @Synchronized override fun remove(scope: String, key: String) =
        items.removeAll { it.scope == scope && it.key == key }.also { if (it) persist() }
    @Synchronized override fun clear() { items.clear(); persist() }
    protected open fun persist() {}
}

/** JSON file in the app's private files dir. */
class FilePreferenceStore(private val file: File) : InMemoryPreferenceStore() {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedPreference.serializer())

    init {
        runCatching { if (file.exists()) items += json.decodeFromString(serializer, file.readText()) }
    }

    override fun persist() {
        runCatching { file.writeText(json.encodeToString(serializer, items)) }
    }
}

/** True if the answer asks to keep it for next time ("remember that", "always", "save that"). */
fun wantsRemember(answer: String): Boolean =
    Regex("\\b(remember|always|save that|save it|from now on|next time)\\b", RegexOption.IGNORE_CASE).containsMatchIn(answer)

/** The offered choice an answer names, matched loosely (case-insensitive, whole words). */
fun choiceIn(answer: String, choices: List<String>): String? {
    val a = " " + answer.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace("e mail", "email") + " "
    return choices.filter { c ->
        val words = c.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(" ").filter { it.length > 1 }
        words.isNotEmpty() && words.all { " $it " in a }
    }.maxByOrNull { it.length }
        ?: choices.firstOrNull { c -> c.lowercase().split(" ").firstOrNull()?.let { " $it " in a } == true }
}
