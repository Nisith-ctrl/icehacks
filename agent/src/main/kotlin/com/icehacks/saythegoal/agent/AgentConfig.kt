package com.icehacks.saythegoal.agent

/** The test persona's "known details". Must match fixtures/seed/README.md (T04). */
data class Profile(
    val fullName: String = "Alex Rivera",
    val email: String = "alex.rivera.test@example.com",
    val phone: String = "555-0142",
)

data class AgentConfig(
    /** Groq's OpenAI-compatible API (free tier). See decision 2026-11-09-llm-provider-groq. */
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val apiKey: String = BuildConfig.GROQ_API_KEY,
    val model: String = BuildConfig.GROQ_MODEL,
    val visionModel: String = BuildConfig.GROQ_VISION_MODEL,
    val profile: Profile = Profile(),
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
)

/** Spoken app names the model may launch, mapped to packages (all in core.AllowedApps). */
val LAUNCHABLE_APPS = mapOf(
    "gmail" to "com.google.android.gm",
    "calendar" to "com.google.android.calendar",
    "chrome" to "com.android.chrome",
    "messages" to "com.google.android.apps.messaging",
)
