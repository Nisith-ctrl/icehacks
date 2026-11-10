package com.icehacks.suus.voice

import com.icehacks.suus.core.UserCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Decides where each transcript goes (mic or debug injection, same path):
 * 1. If an ask()/approval is waiting for an answer, the transcript is that answer.
 * 2. Else, with the wake word ("Suus, …"), the rest is classified into a command and a session starts.
 * 3. Else, right after a bare "Suus" (within [followUpMs]), the next utterance that reads like a
 *    request is the command (room chatter such as "your phone" is still ignored).
 * 4. Else, during an active session (recent speech either way), it becomes a command only if it reads
 *    like a request ([CommandParser.looksLikeRequest]), so chatter near the phone doesn't start tasks.
 * 5. Else it is ignored.
 */
class VoiceRouter(
    private val now: () -> Long = System::currentTimeMillis,
    private val sessionMs: Long = 30_000,
    private val followUpMs: Long = 10_000,
) {
    enum class Routed { ANSWER, COMMAND, WOKE, IGNORED }

    private val _commands = MutableSharedFlow<UserCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<UserCommand> = _commands

    private val _wakes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** Fires whenever the wake word is heard, alone ("Suus") or before a command. */
    val wakes: SharedFlow<Unit> = _wakes

    @Volatile private var pending: CompletableDeferred<String>? = null
    @Volatile private var lastActive: Long = Long.MIN_VALUE / 2
    @Volatile private var wokeAt: Long = Long.MIN_VALUE / 2

    val sessionActive get() = now() - lastActive < sessionMs

    fun markActive() { lastActive = now() }

    fun onTranscript(text: String, forceSession: Boolean = false): Routed {
        val t = text.trim()
        if (t.isEmpty()) return Routed.IGNORED
        pending?.let { p ->
            if (!p.isCompleted) {
                markActive()
                p.complete(t)
                return Routed.ANSWER
            }
        }
        val afterWake = CommandParser.afterWake(t)
        val followUp = now() - wokeAt < followUpMs
        val body = when {
            afterWake != null -> afterWake
            forceSession -> t
            (followUp || sessionActive) && CommandParser.looksLikeRequest(t) -> t
            else -> return Routed.IGNORED
        }
        markActive()
        wokeAt = Long.MIN_VALUE / 2
        if (afterWake != null) _wakes.tryEmit(Unit)
        if (body.isBlank()) {
            wokeAt = now() // "Suus" alone: whatever is said next is the request
            return Routed.WOKE
        }
        _commands.tryEmit(CommandParser.classify(body))
        return Routed.COMMAND
    }

    /** Call before speaking the question, so a quick answer can't slip past as a command. */
    fun expectAnswer(): CompletableDeferred<String> = CompletableDeferred<String>().also { pending = it }

    suspend fun awaitAnswer(expected: CompletableDeferred<String>, timeoutMs: Long): String? =
        try {
            withTimeoutOrNull(timeoutMs) { expected.await() }
        } finally {
            if (pending === expected) pending = null
        }
}
