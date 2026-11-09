package com.icehacks.saythegoal.agent

import android.content.Context
import android.util.Log
import com.icehacks.saythegoal.core.AgentRunner
import com.icehacks.saythegoal.core.ApprovalGate
import com.icehacks.saythegoal.core.Overlay
import com.icehacks.saythegoal.core.ScreenController
import com.icehacks.saythegoal.core.UserCommand
import com.icehacks.saythegoal.core.VoiceIO

/**
 * Called by the app's AccessibilityService. T03 replaces the fake with the real runner.
 * The Claude key is available as BuildConfig.ANTHROPIC_API_KEY (empty if local.properties has none).
 */
fun createAgent(
    screen: ScreenController,
    voice: VoiceIO,
    approval: ApprovalGate,
    overlay: Overlay,
    context: Context,
): AgentRunner = FakeAgentRunner()

class FakeAgentRunner : AgentRunner {
    override suspend fun handle(command: UserCommand) {
        Log.i("STG-Agent", "FakeAgentRunner.handle: $command (key set: ${BuildConfig.ANTHROPIC_API_KEY.isNotEmpty()})")
    }
}
