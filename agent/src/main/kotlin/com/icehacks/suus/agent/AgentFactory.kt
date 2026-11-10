package com.icehacks.suus.agent

import android.content.Context
import android.util.Log
import com.icehacks.suus.core.AgentRunner
import com.icehacks.suus.core.ApprovalGate
import com.icehacks.suus.core.Overlay
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.UserCommand
import com.icehacks.suus.core.VoiceIO
import com.icehacks.suus.agent.data.createRunLog

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
    val runLog = createRunLog(context) // T06: logs every command, action, question and approval
    val loggedScreen = runLog.screen(screen)
    val loggedVoice = runLog.voice(voice)
    val loggedApproval = runLog.approval(approval)
    val config = AgentConfig(autoFillForms = true)
    val planner: Planner = if (config.apiKey.isBlank()) NoKeyPlanner else GroqPlanner(config)
    val prefs = sharedPreferenceStore(context)
    val ledger = DraftLedger()
    val loop = AgentLoop(
        loggedScreen, loggedVoice, loggedApproval, overlay, planner, prefs, ledger,
        calendar = AndroidCalendarSource(context),
        photos = AndroidPhotoSource(context),
        config = config,
    )
    Log.i(AgentLoop.TAG, "agent ready: model=${config.model}, key set=${config.apiKey.isNotBlank()}")
    return runLog.agent(SuusAgent(loggedScreen, loggedVoice, overlay, loop, prefs, ledger, runLog = runLog.logger))
}

/**
 * The one preference store in the app process (T06's SQLite store, one instance per process),
 * shared by the agent and the "What I remember" screen (T05), so a preference deleted on screen is
 * forgotten by the running agent at once.
 */
fun sharedPreferenceStore(context: Context): ObservablePreferenceStore = createPreferenceStore(context)

private object NoKeyPlanner : Planner {
    override suspend fun next(turn: PlannerTurn) =
        PlannerStep("stop", tool("reason" to "No Groq API key is set up, so I can't plan. Add GROQ_API_KEY to local.properties and rebuild."))
}

class FakeAgentRunner : AgentRunner {
    override suspend fun handle(command: UserCommand) {
        Log.i(AgentLoop.TAG, "FakeAgentRunner.handle: $command")
    }
}
