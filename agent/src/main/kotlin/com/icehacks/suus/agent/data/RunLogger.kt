package com.icehacks.suus.agent.data

import com.icehacks.suus.agent.Outcome
import kotlinx.serialization.Serializable

/** What started a session: one spoken command. */
enum class SessionKind(val wire: String) { GOAL("goal"), UNDO("undo"), MEMORY("memory") }

/** `run_events.type` values. */
object EventType {
    const val ACTION = "action"     // name = tap | submit | type | scroll | back | launch, label = target, ok
    const val QUESTION = "question" // label = question, detail = answer (null = no answer), ok = answered
    const val APPROVAL = "approval" // name = commit word, label = read-back, detail = APPROVED | CANCELLED | NO_ANSWER
    const val SAID = "said"         // label = what the agent said aloud
    const val STOP = "stop"         // the user said "stop"
    const val TOUCH = "touch"       // the user touched the screen (label = what)
}

/**
 * The agent's run log (T06 M2). One session per spoken command (goal, undo or memory); every other
 * call attaches to the session that is running, or to the one that just ended if none is (e.g.
 * "stop", or a touch between commands), up to 60 s after its end; later ones are dropped. Calls
 * never throw: logging must not break the agent.
 *
 * You normally don't call this directly: [RunLog] wraps the agent's ScreenController, VoiceIO,
 * ApprovalGate and AgentRunner and makes these calls for you.
 */
interface RunLogger {
    fun sessionStarted(kind: SessionKind, text: String, takeOver: Boolean = false)
    fun actionPerformed(action: String, target: String?, ok: Boolean, detail: String? = null)
    fun questionAsked(question: String, answer: String?)
    fun approvalRequested(readback: String, commitWord: String, outcome: String)
    fun agentSaid(text: String)
    fun stopRequested()
    fun userTouch(what: String)

    /** Records how the running session turned out without ending it. The first outcome set wins. */
    fun sessionOutcome(outcome: String, detail: String? = null)

    /** Ends the running session. [outcome] is used only if [sessionOutcome] didn't set one. */
    fun sessionEnded(outcome: String, detail: String? = null)
}

/** Records the agent loop's result: done | stopped | cancelled. */
fun RunLogger.sessionOutcome(outcome: Outcome) = when (outcome) {
    is Outcome.Done -> sessionOutcome("done", outcome.summary)
    is Outcome.Stopped -> sessionOutcome("stopped", outcome.reason)
    Outcome.Cancelled -> sessionOutcome("cancelled", "the user said Cancel at the approval")
}

object NoOpRunLogger : RunLogger {
    override fun sessionStarted(kind: SessionKind, text: String, takeOver: Boolean) {}
    override fun actionPerformed(action: String, target: String?, ok: Boolean, detail: String?) {}
    override fun questionAsked(question: String, answer: String?) {}
    override fun approvalRequested(readback: String, commitWord: String, outcome: String) {}
    override fun agentSaid(text: String) {}
    override fun stopRequested() {}
    override fun userTouch(what: String) {}
    override fun sessionOutcome(outcome: String, detail: String?) {}
    override fun sessionEnded(outcome: String, detail: String?) {}
}

// ---------- stored rows ----------

@Serializable
data class RunSession(
    val id: Long,
    val kind: String,
    val text: String,
    val takeOver: Boolean,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val outcome: String?,
    val outcomeDetail: String?,
)

@Serializable
data class RunEvent(
    val id: Long,
    val sessionId: Long,
    val atMs: Long,
    val type: String,
    val name: String?,
    val label: String?,
    val detail: String?,
    val ok: Boolean?,
)

// ---------- summary ----------

/**
 * Counts for the metrics (T06 M2). For one session `sessions` is 1; for a whole export they are
 * summed and [spanMs] runs from the first start to the last end.
 *
 * - [spokenCommands]: the command that started each session, plus every answered question, every
 *   approval answered with the commit word or "Cancel", and every "stop". Activation ("Suus")
 *   is part of the command's utterance, so it counts once with it.
 * - [userTouches]: clicks reported by the accessibility service while the agent wasn't acting,
 *   during the session or in the 60 s after it. A lower bound (see data/README.md); check it
 *   against the recording.
 */
@Serializable
data class RunSummary(
    val sessions: Int,
    val actions: Int,
    val failedActions: Int,
    val spokenCommands: Int,
    val questions: Int,
    val approvals: Int,
    val approvalsGranted: Int,
    val undos: Int,
    val userTouches: Int,
    val durationMs: Long,
    val spanMs: Long,
)

object RunSummaries {
    fun of(session: RunSession, events: List<RunEvent>): RunSummary {
        val mine = events.filter { it.sessionId == session.id }
        val actions = mine.filter { it.type == EventType.ACTION }
        val approvals = mine.filter { it.type == EventType.APPROVAL }
        val duration = session.endedAtMs?.let { it - session.startedAtMs } ?: 0L
        return RunSummary(
            sessions = 1,
            actions = actions.size,
            failedActions = actions.count { it.ok == false },
            spokenCommands = 1 +
                mine.count { it.type == EventType.QUESTION && it.detail != null } +
                approvals.count { it.detail == "APPROVED" || it.detail == "CANCELLED" } +
                mine.count { it.type == EventType.STOP },
            questions = mine.count { it.type == EventType.QUESTION },
            approvals = approvals.size,
            approvalsGranted = approvals.count { it.detail == "APPROVED" },
            undos = if (session.kind == SessionKind.UNDO.wire) 1 else 0,
            userTouches = mine.count { it.type == EventType.TOUCH },
            durationMs = duration,
            spanMs = duration,
        )
    }

    fun total(sessions: List<RunSession>, events: List<RunEvent>): RunSummary {
        val each = sessions.map { of(it, events) }
        val first = sessions.minOfOrNull { it.startedAtMs }
        val last = sessions.maxOfOrNull { it.endedAtMs ?: it.startedAtMs }
        return RunSummary(
            sessions = sessions.size,
            actions = each.sumOf { it.actions },
            failedActions = each.sumOf { it.failedActions },
            spokenCommands = each.sumOf { it.spokenCommands },
            questions = each.sumOf { it.questions },
            approvals = each.sumOf { it.approvals },
            approvalsGranted = each.sumOf { it.approvalsGranted },
            undos = each.sumOf { it.undos },
            userTouches = each.sumOf { it.userTouches },
            durationMs = each.sumOf { it.durationMs },
            spanMs = if (first != null && last != null) last - first else 0L,
        )
    }
}
