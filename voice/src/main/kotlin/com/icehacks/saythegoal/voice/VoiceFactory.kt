package com.icehacks.saythegoal.voice

import android.content.Context
import android.util.Log
import com.icehacks.saythegoal.core.ApprovalGate
import com.icehacks.saythegoal.core.ApprovalOutcome
import com.icehacks.saythegoal.core.ApprovalResult
import com.icehacks.saythegoal.core.Fingerprinter
import com.icehacks.saythegoal.core.ScreenSnapshot
import com.icehacks.saythegoal.core.UserCommand
import com.icehacks.saythegoal.core.VoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Called by the app's AccessibilityService. T02 replaces the fake with the real implementation. */
fun createVoice(context: Context, scope: CoroutineScope): VoiceIO = FakeVoice()

/** Called by the app's AccessibilityService. T02 replaces the fake with the real implementation. */
fun createApprovalGate(voice: VoiceIO, fingerprinter: Fingerprinter): ApprovalGate = FakeApprovalGate()

private const val TAG = "STG-Voice"

/** Never emits commands, logs speech, never answers. */
class FakeVoice : VoiceIO {
    private val flow = MutableSharedFlow<UserCommand>(extraBufferCapacity = 8)
    override val commands: Flow<UserCommand> = flow

    /** Lets a debug hook inject a command as if spoken. */
    fun inject(command: UserCommand) = flow.tryEmit(command)

    override suspend fun say(text: String) { Log.i(TAG, "FakeVoice.say: $text") }

    override suspend fun ask(question: String, choices: List<String>): String? {
        Log.i(TAG, "FakeVoice.ask: $question $choices -> null")
        return null
    }
}

/** Always NO_ANSWER, so nothing can ever be committed through the fake. */
class FakeApprovalGate : ApprovalGate {
    override suspend fun request(readback: String, snapshot: ScreenSnapshot, commitWord: String): ApprovalResult {
        Log.i(TAG, "FakeApprovalGate.request($commitWord): $readback -> NO_ANSWER")
        return ApprovalResult(ApprovalOutcome.NO_ANSWER, null)
    }
}
