package com.icehacks.saythegoal.agent

/** The test persona's "known details". T04 owns the real values (fixtures/seed/README.md). */
data class Profile(
    val fullName: String = "Alex Rivera",
    val email: String = "alex.rivera.test@example.com",
    val phone: String = "555-0142",
)

data class AgentConfig(
    /** Team decision 2026-11-09-architecture-and-module-split: Sonnet for latency. */
    val model: String = "claude-sonnet-5-5",
    val apiKey: String = BuildConfig.ANTHROPIC_API_KEY,
    val profile: Profile = Profile(),
    /**
     * Chrome may only be used on these hosts (suffix match). Placeholder until T04 records the
     * deployed form URLs; `vercel.app` covers the planned deployment.
     */
    val formHosts: List<String> = listOf("vercel.app", "localhost", "10.0.2.2"),
    val maxActions: Int = 40,
    val maxTurns: Int = 70,
    val historyLines: Int = 24,
)

/** Spoken app names the model may launch, mapped to packages (all in core.AllowedApps). */
val LAUNCHABLE_APPS = mapOf(
    "gmail" to "com.google.android.gm",
    "calendar" to "com.google.android.calendar",
    "chrome" to "com.android.chrome",
    "messages" to "com.google.android.apps.messaging",
)
