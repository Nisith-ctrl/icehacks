package com.icehacks.saythegoal.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.core.content.ContextCompat
import com.icehacks.saythegoal.core.ApprovalGate
import com.icehacks.saythegoal.core.ApprovalOutcome
import com.icehacks.saythegoal.core.ApprovalResult
import com.icehacks.saythegoal.core.DebugActions
import com.icehacks.saythegoal.core.Fingerprinter
import com.icehacks.saythegoal.core.ScreenSnapshot
import com.icehacks.saythegoal.core.UserCommand
import com.icehacks.saythegoal.core.VoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow

private const val TAG = "STG-Voice"

/**
 * Called by the app's AccessibilityService. Real speech in/out; debug builds also accept
 * `adb shell am broadcast -a com.icehacks.saythegoal.DEBUG_UTTERANCE --es text "..."`,
 * which is handled exactly like recognized speech (answers a pending question, or becomes a command).
 */
fun createVoice(context: Context, scope: CoroutineScope): VoiceIO {
    val voice = runCatching { AndroidVoice(context, scope) }
        .onFailure { Log.w(TAG, "AndroidVoice failed, using FakeVoice", it) }
        .getOrNull()
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (voice == null) return FakeVoice()
    if (debuggable) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                intent.getStringExtra("text")?.let { voice.inject(it) }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(DebugActions.UTTERANCE), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "DEBUG_UTTERANCE registered")
    }
    return voice
}

/**
 * Fires each time a wake phrase is heard, alone or before a command. For the setup screen's wake
 * check (T05): `wakeSignals(SayTheGoalAccessibilityService.instance!!.voice).first()`.
 * Empty for voices that can't hear (FakeVoice).
 */
fun wakeSignals(voice: VoiceIO): Flow<Unit> = (voice as? AndroidVoice)?.router?.wakes ?: emptyFlow()

/** Spoken approval: only the commit word or "Cancel" count. */
fun createApprovalGate(voice: VoiceIO, fingerprinter: Fingerprinter): ApprovalGate =
    SpokenApprovalGate(voice, fingerprinter)

/** Scripted voice for tests: [answers] are returned by ask() in order (null = silence). Logs say(). */
class FakeVoice(answers: List<String?> = emptyList()) : VoiceIO {
    private val queue = ArrayDeque(answers)
    private val flow = MutableSharedFlow<UserCommand>(extraBufferCapacity = 8)
    override val commands: Flow<UserCommand> = flow
    val spoken = mutableListOf<String>()

    fun inject(command: UserCommand) = flow.tryEmit(command)

    override suspend fun say(text: String) {
        spoken += text
        runCatching { Log.i(TAG, "FakeVoice.say: $text") }
    }

    override suspend fun ask(question: String, choices: List<String>): String? {
        spoken += question
        val a = queue.removeFirstOrNull()
        runCatching { Log.i(TAG, "FakeVoice.ask: $question $choices -> $a") }
        return a
    }
}

/** Never approves. Used when no real gate is wired. */
class FakeApprovalGate : ApprovalGate {
    override suspend fun request(readback: String, snapshot: ScreenSnapshot, commitWord: String): ApprovalResult {
        runCatching { Log.i(TAG, "FakeApprovalGate.request($commitWord): $readback -> NO_ANSWER") }
        return ApprovalResult(ApprovalOutcome.NO_ANSWER, null)
    }
}
