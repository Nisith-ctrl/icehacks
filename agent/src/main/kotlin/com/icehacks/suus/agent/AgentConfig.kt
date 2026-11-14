package com.icehacks.suus.agent

/**
 * The user's "known details". The defaults are the test persona (fixtures/seed/README.md, T04) and
 * are only used in tests; the app reads the real ones from [AgentConfig.profileStore].
 */
data class Profile(
    val fullName: String = "Alex Rivera",
    val email: String = "alex.rivera.test@example.com",
    val phone: String = "555-0142",
)

data class AgentConfig(
    /** Groq's OpenAI-compatible API (free tier). See decision 2026-11-09-llm-provider-groq. */
    val baseUrl: String = BuildConfig.GROQ_BASE_URL, // Groq itself, or the SuuS relay in release builds
    val apiKey: String = BuildConfig.GROQ_API_KEY,
    val model: String = BuildConfig.GROQ_MODEL,
    val visionModel: String = BuildConfig.GROQ_VISION_MODEL,
    val profile: Profile = Profile(),
    /** Where the app keeps the user's details ("About you" screen). When set, it replaces [profile]. */
    val profileStore: ProfileStore? = null,
    /** The user's choices from the app's "Make SuuS yours" screen (defaults in tests). */
    val settingsStore: SettingsStore? = null,
    /**
     * Chrome may only be used on these hosts (exact host or a subdomain of it). The deployed forms
     * are on saythegoal-forms-nisith.vercel.app (decision 2026-11-09-test-form-urls);
     * 10.0.2.2/localhost are for local testing.
     */
    val formHosts: List<String> = listOf("saythegoal-forms-nisith.vercel.app", "localhost", "10.0.2.2"),
    val maxActions: Int = 40,
    val maxTurns: Int = 70,
    /** Kept small: Groq's free tier allows ~8K tokens per minute, and every turn re-sends these. */
    val historyLines: Int = 12,
    /** Longest wait for a rate-limit retry before giving up on a turn. */
    val maxRateLimitWaitSec: Int = 65,
    /**
     * Fill known form fields in code the first time a form page is seen, and put answers to the
     * user's questions straight into the matching field. On in the app (createAgent); off by default
     * so tests that script every step keep their exact steps.
     */
    val autoFillForms: Boolean = false,
    /** How long to let a link's redirect land before the form-host guard stops the run. */
    val redirectWaitMs: Long = 4_000,
) {
    /** The details to use right now: the saved profile in the app, the test persona in tests. */
    fun currentProfile(): Profile = profileStore?.load() ?: profile

    /** Tone, detail and voice choices right now (read fresh, so changes apply on the next turn). */
    fun currentSettings(): SuusSettings = settingsStore?.load() ?: SuusSettings()
}

/** Spoken app names the model may launch, mapped to packages (all in core.AllowedApps). */
val LAUNCHABLE_APPS = mapOf(
    "gmail" to "com.google.android.gm",
    "calendar" to "com.google.android.calendar",
    "chrome" to "com.android.chrome",
    "messages" to "com.google.android.apps.messaging",
)
