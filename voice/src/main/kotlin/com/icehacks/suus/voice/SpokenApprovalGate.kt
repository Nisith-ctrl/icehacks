package com.icehacks.suus.voice

import com.icehacks.suus.core.Approval
import com.icehacks.suus.core.ApprovalGate
import com.icehacks.suus.core.ApprovalOutcome
import com.icehacks.suus.core.ApprovalResult
import com.icehacks.suus.core.Fingerprinter
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.VoiceIO

/**
 * Speaks the read-back, then accepts only the commit word ("Submit"/"Send") or "Cancel".
 * "Yes", "sure", "ok" are not approval: it re-prompts. Two silences (VoiceIO.ask -> null) = NO_ANSWER.
 * The returned Approval is the fingerprint of the snapshot that was read back; the device layer
 * re-checks it right before the tap, so any change after approval needs a new request.
 */
class SpokenApprovalGate(
    private val voice: VoiceIO,
    private val fingerprinter: Fingerprinter,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxPrompts: Int = 3,
) : ApprovalGate {

    override suspend fun request(readback: String, snapshot: ScreenSnapshot, commitWord: String): ApprovalResult {
        var answer = voice.ask("$readback Say $commitWord to go ahead, or Cancel.")
        repeat(maxPrompts) {
            when (decide(answer, commitWord)) {
                Decision.COMMIT -> return ApprovalResult(
                    ApprovalOutcome.APPROVED, Approval(fingerprinter.of(snapshot), now()),
                )
                Decision.CANCEL -> return ApprovalResult(ApprovalOutcome.CANCELLED, null)
                Decision.SILENT -> return ApprovalResult(ApprovalOutcome.NO_ANSWER, null)
                Decision.UNCLEAR -> answer = voice.ask("Say $commitWord to ${commitWord.lowercase()} it, or Cancel.")
            }
        }
        return ApprovalResult(ApprovalOutcome.NO_ANSWER, null)
    }

    enum class Decision { COMMIT, CANCEL, SILENT, UNCLEAR }

    companion object {
        private val NEGATIONS = setOf("don't", "dont", "not", "no", "never", "wait", "hold")

        fun decide(answer: String?, commitWord: String): Decision {
            if (answer == null) return Decision.SILENT
            val words = CommandParser.normalize(answer).split(" ").filter { it.isNotBlank() }
            if (words.isEmpty()) return Decision.SILENT
            if ("cancel" in words || "stop" in words) return Decision.CANCEL
            val commit = CommandParser.normalize(commitWord)
            if (commit in words && words.none { it in NEGATIONS }) {
                return Decision.COMMIT
            }
            return Decision.UNCLEAR
        }
    }
}
