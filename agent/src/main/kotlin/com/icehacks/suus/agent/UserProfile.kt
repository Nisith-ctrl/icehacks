package com.icehacks.suus.agent

import android.content.Context

/**
 * The user's own details (name, email, phone) that forms ask for. Set once on the app's
 * "About you" screen, or learned when SuuS asks for a missing one by voice; used for every form.
 * Blank fields are unknown: SuuS asks for them instead of guessing.
 */
interface ProfileStore {
    fun load(): Profile
    fun save(profile: Profile)
}

/** Stored in the app's private SharedPreferences, so the setup screen and the agent see the same values. */
class SharedPrefsProfileStore(context: Context) : ProfileStore {
    private val prefs = context.applicationContext.getSharedPreferences("suus_profile", Context.MODE_PRIVATE)

    override fun load() = Profile(
        fullName = prefs.getString(KEY_NAME, "").orEmpty(),
        email = prefs.getString(KEY_EMAIL, "").orEmpty(),
        phone = prefs.getString(KEY_PHONE, "").orEmpty(),
    )

    override fun save(profile: Profile) {
        prefs.edit()
            .putString(KEY_NAME, profile.fullName.trim())
            .putString(KEY_EMAIL, profile.email.trim())
            .putString(KEY_PHONE, profile.phone.trim())
            .apply()
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
        const val KEY_PHONE = "phone"
    }
}

/** For the agent and the app's "About you" screen. */
fun createProfileStore(context: Context): ProfileStore = SharedPrefsProfileStore(context)

/** Which profile detail a question or form label is about, if any. */
enum class ProfileDetail { NAME, EMAIL, PHONE }

object ProfileText {
    private val EMAIL = Regex("\\be-?mail\\b", RegexOption.IGNORE_CASE)
    private val PHONE = Regex("\\b(phone|mobile|cell|telephone|number to (call|text))\\b", RegexOption.IGNORE_CASE)
    private val NAME = Regex("\\b(full name|your name|name)\\b", RegexOption.IGNORE_CASE)

    fun detailIn(text: String): ProfileDetail? = when {
        EMAIL.containsMatchIn(text) -> ProfileDetail.EMAIL
        PHONE.containsMatchIn(text) -> ProfileDetail.PHONE
        NAME.containsMatchIn(text) -> ProfileDetail.NAME
        else -> null
    }

    /**
     * A spoken email as text: "Alex dot Rivera at gmail dot com" -> "alex.rivera@gmail.com".
     * Returns null if it doesn't look like an email afterwards.
     */
    fun email(spoken: String): String? {
        var t = " " + spoken.trim().lowercase().trimEnd('.', '!', '?') + " "
        t = t.replace(Regex("^ (my email is|it's|it is|email is|the email is) "), " ")
        t = t.replace(" at sign ", "@").replace(" at ", "@")
            .replace(" dot ", ".").replace(" period ", ".")
            .replace(" underscore ", "_").replace(" dash ", "-").replace(" hyphen ", "-")
        t = t.replace(" ", "").replace(",", "")
        return t.takeIf { Regex("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$").matches(it) }
    }

    /** Digits of a spoken phone number, kept as said ("555 014 2" -> "5550142"); null if under 7 digits. */
    fun phone(spoken: String): String? {
        val words = mapOf(
            "zero" to "0", "oh" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        )
        val digits = spoken.lowercase().split(Regex("[^a-z0-9+]+")).joinToString("") { w ->
            words[w] ?: w.filter { it.isDigit() || it == '+' }
        }
        return digits.takeIf { it.count(Char::isDigit) >= 7 }
    }

    /** A spoken name, tidied: "my name is alex rivera" -> "Alex Rivera". */
    fun name(spoken: String): String? {
        val t = spoken.trim().trimEnd('.', '!', '?')
            .replace(Regex("^(my name is|it's|it is|i'm|i am|call me)\\s+", RegexOption.IGNORE_CASE), "")
        if (t.isBlank() || t.split(" ").size > 5) return null
        return t.split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
