package com.icehacks.suus.agent

import android.content.Context

/**
 * How the user wants SuuS to behave: its tone, how much it explains, how fast it talks and how big
 * the robot is. Chosen on the app's "Make SuuS yours" screen (and in first-run setup), read fresh by
 * the agent on every turn, so a change applies to the next thing SuuS says.
 */
enum class Tone { FRIENDLY, CALM, BRIEF }

/** How much SuuS says about what it's doing. */
enum class Detail {
    /** Only questions, approvals and the result are spoken. */
    QUIET,
    /** A short "on it" and the main moments (the default). */
    MAIN_STEPS,
    /** Every action is said out loud as it happens ("Opening Gmail"). Slower, but nothing is a surprise. */
    EVERY_STEP,
}

enum class VoiceSpeed(val rate: Float) { SLOWER(0.85f), NORMAL(1.0f), FASTER(1.2f) }

enum class RobotSize(val scale: Float) { SMALL(0.8f), MEDIUM(1.0f), LARGE(1.3f) }

data class SuusSettings(
    val tone: Tone = Tone.FRIENDLY,
    val detail: Detail = Detail.MAIN_STEPS,
    val speed: VoiceSpeed = VoiceSpeed.NORMAL,
    val robotSize: RobotSize = RobotSize.MEDIUM,
    /** First-run setup (permissions, About you, these choices) has been finished once. */
    val onboarded: Boolean = false,
)

interface SettingsStore {
    fun load(): SuusSettings
    fun save(settings: SuusSettings)
}

class SharedPrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.applicationContext.getSharedPreferences("suus_settings", Context.MODE_PRIVATE)

    override fun load() = SuusSettings(
        tone = enumOr(prefs.getString(KEY_TONE, null), Tone.FRIENDLY),
        detail = enumOr(prefs.getString(KEY_DETAIL, null), Detail.MAIN_STEPS),
        speed = enumOr(prefs.getString(KEY_SPEED, null), VoiceSpeed.NORMAL),
        robotSize = enumOr(prefs.getString(KEY_ROBOT, null), RobotSize.MEDIUM),
        onboarded = prefs.getBoolean(KEY_ONBOARDED, false),
    )

    override fun save(settings: SuusSettings) {
        prefs.edit()
            .putString(KEY_TONE, settings.tone.name)
            .putString(KEY_DETAIL, settings.detail.name)
            .putString(KEY_SPEED, settings.speed.name)
            .putString(KEY_ROBOT, settings.robotSize.name)
            .putBoolean(KEY_ONBOARDED, settings.onboarded)
            .apply()
    }

    private companion object {
        const val KEY_TONE = "tone"
        const val KEY_DETAIL = "detail"
        const val KEY_SPEED = "speed"
        const val KEY_ROBOT = "robot_size"
        const val KEY_ONBOARDED = "onboarded"

        inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
    }
}

/** For the agent and the app's settings screens. */
fun createSettingsStore(context: Context): SettingsStore = SharedPrefsSettingsStore(context)

/** Words SuuS says, by tone. */
object SuusVoiceLines {
    fun acks(tone: Tone): List<String> = when (tone) {
        Tone.FRIENDLY -> listOf("On it!", "Sure thing!", "Got it, let's do this!", "Okay, on my way!", "You got it!")
        Tone.CALM -> listOf("Okay. I'll take care of that.", "Alright, starting now.", "Okay, one moment.")
        Tone.BRIEF -> listOf("On it.", "Okay.")
    }

    /** The system-prompt line about how to talk. */
    fun style(tone: Tone, detail: Detail): String {
        val voice = when (tone) {
            Tone.FRIENDLY -> "Sound like a warm, upbeat helper whenever your words are spoken (questions, done summaries, stop reasons): short friendly sentences, e.g. \"All done! You're signed up for Sunday afternoon.\" Never robotic."
            Tone.CALM -> "Sound calm and plain whenever your words are spoken: gentle, unhurried, no exclamation marks, e.g. \"Done. You're signed up for Sunday afternoon.\""
            Tone.BRIEF -> "Be as brief as possible whenever your words are spoken: a few words, no pleasantries, e.g. \"Done. Sunday, 2 PM.\""
        }
        val depth = when (detail) {
            Detail.QUIET -> "The user wants little talk: keep done summaries to one short sentence and captions to two or three words."
            Detail.MAIN_STEPS -> "Keep captions short and plain."
            Detail.EVERY_STEP -> "The user wants each step explained: every caption is read aloud, so make each one a short, clear sentence of what you're doing and why, e.g. \"Opening the invite to find the times\"."
        }
        return "$voice\n- $depth"
    }
}
