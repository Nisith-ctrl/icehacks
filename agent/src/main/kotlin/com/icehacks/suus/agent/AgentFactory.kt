package com.icehacks.suus.agent

import android.content.Context
import android.util.Log
import com.icehacks.suus.core.AgentRunner
import com.icehacks.suus.core.ApprovalGate
import com.icehacks.suus.core.Overlay
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.UserCommand
import com.icehacks.suus.core.VoiceIO
import java.io.File

/**
 * Called by the app's AccessibilityService. Needs GROQ_API_KEY in local.properties (free key from
 * console.groq.com); without it the agent still runs and says that no key is set up.
 */
fun createAgent(
    screen: ScreenController,
    voice: VoiceIO,
    approval: ApprovalGate,
    overlay: Overlay,
    context: Context,
): AgentRunner {
    val config = AgentConfig(autoFillForms = true)
    val planner: Planner = if (config.apiKey.isBlank()) NoKeyPlanner else GroqPlanner(config)
    val prefs = sharedPreferenceStore(context)
    val ledger = DraftLedger()
    val loop = AgentLoop(
        screen, voice, approval, overlay, planner, prefs, ledger,
        calendar = AndroidCalendarSource(context),
        photos = AndroidPhotoSource(context),
        config = config,
    )
    Log.i(AgentLoop.TAG, "agent ready: model=${config.model}, key set=${config.apiKey.isNotBlank()}")
    return SuusAgent(screen, voice, overlay, loop, prefs, ledger)
}

/**
 * The one preference store in the app process, shared by the agent and the "What I remember"
 * screen (T05), so a preference deleted on screen is forgotten by the running agent at once.
 * T06 (Advay): when `createPreferenceStore(context)` lands, return it from here (keep it a single
 * instance) and both sides switch over with no other change.
 */
fun sharedPreferenceStore(context: Context): PreferenceStore =
    sharedStore ?: synchronized(storeLock) {
        sharedStore ?: FilePreferenceStore(File(context.applicationContext.filesDir, "saved_preferences.json"))
            .also { sharedStore = it }
    }

private val storeLock = Any()
@Volatile private var sharedStore: PreferenceStore? = null

private object NoKeyPlanner : Planner {
    override suspend fun next(turn: PlannerTurn) =
        PlannerStep("stop", tool("reason" to "No Groq API key is set up, so I can't plan. Add GROQ_API_KEY to local.properties and rebuild."))
}

class FakeAgentRunner : AgentRunner {
    override suspend fun handle(command: UserCommand) {
        Log.i(AgentLoop.TAG, "FakeAgentRunner.handle: $command")
    }
}
