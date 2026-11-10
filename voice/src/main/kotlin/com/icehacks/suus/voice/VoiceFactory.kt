package com.icehacks.suus.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.core.content.ContextCompat
import com.icehacks.suus.core.ApprovalGate
import com.icehacks.suus.core.ApprovalOutcome
import com.icehacks.suus.core.ApprovalResult
import com.icehacks.suus.core.DebugActions
import com.icehacks.suus.core.Fingerprinter
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.UserCommand
import com.icehacks.suus.core.VoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow

private const val TAG = "SUUS-Voice"

/**
 * Called by the app's AccessibilityService. Real speech in/out; debug builds also accept
 * `adb shell am broadcast -a com.icehacks.suus.DEBUG_UTTERANCE --es text "..."`,
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
 * Fires each time the wake word ("Suus") is heard, alone or before a command. For the setup screen's wake
 * check (T05): `wakeSignals(SuusAccessibilityService.instance!!.voice).first()`.
 * Empty for voices that can't hear (FakeVoice).
 */
fun wakeSignals(voice: VoiceIO): Flow<Unit> = (voice as? AndroidVoice)?.router?.wakes ?: emptyFlow()

/**
 * What the microphone hears, live: partial text while the user is still speaking, then the final
 * text. [forSuus] is true when SuuS is paying attention to it (a question is waiting, the wake word
 * was said, or a session is open), so the app can show it as "you said…" without showing room chatter.
 */
data class Heard(val text: String, val final: Boolean, val forSuus: Boolean)

/** Live transcripts for the overlay's "you said" bubble. Empty for fake voices. */
fun heardSpeech(voice: VoiceIO): Flow<Heard> = (voice as? AndroidVoice)?.heard ?: emptyFlow()

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
