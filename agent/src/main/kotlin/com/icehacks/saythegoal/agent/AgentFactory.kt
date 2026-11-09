package com.icehacks.saythegoal.agent

import android.content.Context
import android.util.Log
import com.icehacks.saythegoal.core.AgentRunner
import com.icehacks.saythegoal.core.ApprovalGate
import com.icehacks.saythegoal.core.Overlay
import com.icehacks.saythegoal.core.ScreenController
import com.icehacks.saythegoal.core.UserCommand
import com.icehacks.saythegoal.core.VoiceIO
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
    val config = AgentConfig()
    val planner: Planner = if (config.apiKey.isBlank()) NoKeyPlanner else GroqPlanner(config)
    val prefs = FilePreferenceStore(File(context.filesDir, "saved_preferences.json"))
    val ledger = DraftLedger()
    val loop = AgentLoop(
        screen, voice, approval, overlay, planner, prefs, ledger,
        calendar = AndroidCalendarSource(context),
        photos = AndroidPhotoSource(context),
        config = config,
    )
    Log.i(AgentLoop.TAG, "agent ready: model=${config.model}, key set=${config.apiKey.isNotBlank()}")
    return SayTheGoalAgent(screen, voice, overlay, loop, prefs, ledger)
}

private object NoKeyPlanner : Planner {
    override suspend fun next(turn: PlannerTurn) =
        PlannerStep("stop", tool("reason" to "No Groq API key is set up, so I can't plan. Add GROQ_API_KEY to local.properties and rebuild."))
}

class FakeAgentRunner : AgentRunner {
    override suspend fun handle(command: UserCommand) {
        Log.i(AgentLoop.TAG, "FakeAgentRunner.handle: $command")
    }
}
