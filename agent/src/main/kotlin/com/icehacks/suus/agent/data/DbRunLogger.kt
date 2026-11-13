package com.icehacks.suus.agent.data

/** [RunLogger] that writes to the `run_sessions` / `run_events` tables of [SuusDatabase]. */
class DbRunLogger(
    private val db: SuusDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val warn: (String, Throwable) -> Unit = { _, _ -> },
) : RunLogger {

    private var open: Long? = null // the session that is running
    private var last: Long? = null // the most recent session, running or ended
    private var lastEndedAt = 0L

    @Synchronized
    override fun sessionStarted(kind: SessionKind, text: String, takeOver: Boolean) = safe("sessionStarted") {
        open?.let { db.endSession(it, clock(), "unknown", "a new command started before this one ended") }
        val id = db.insertSession(kind.wire, text, takeOver, clock())
        open = id
        last = id
    }

    override fun actionPerformed(action: String, target: String?, ok: Boolean, detail: String?) =
        event(EventType.ACTION, action, target, detail, ok)

    override fun questionAsked(question: String, answer: String?) =
        event(EventType.QUESTION, null, question, answer, answer != null)

    override fun approvalRequested(readback: String, commitWord: String, outcome: String) =
        event(EventType.APPROVAL, commitWord, readback, outcome, outcome == "APPROVED")

    override fun agentSaid(text: String) = event(EventType.SAID, null, text, null, null)

    override fun stopRequested() = event(EventType.STOP, null, "stop", null, null)

    /**
     * A touch between commands counts for the session that just ended (the user fixing something by
     * hand), but only within [TOUCH_GRACE_MS]: setting up the phone an hour later isn't part of that run.
     */
    @Synchronized
    override fun userTouch(what: String) {
        if (open == null && clock() - lastEndedAt > TOUCH_GRACE_MS) return
        event(EventType.TOUCH, null, what, null, null)
    }

    @Synchronized
    override fun sessionOutcome(outcome: String, detail: String?) = safe("sessionOutcome") {
        val id = open ?: return@safe
        db.setSessionOutcome(id, outcome, detail)
    }

    @Synchronized
    override fun sessionEnded(outcome: String, detail: String?) = safe("sessionEnded") {
        val id = open ?: return@safe
        db.endSession(id, clock(), outcome, detail)
        lastEndedAt = clock()
        open = null
    }

    /** Wipes the run log (preferences are kept). */
    @Synchronized
    fun clear() = safe("clear") {
        db.clearRuns()
        open = null
        last = null
    }

    fun sessions(): List<RunSession> = db.sessions()
    fun events(): List<RunEvent> = db.events()

    @Synchronized
    private fun event(type: String, name: String?, label: String?, detail: String?, ok: Boolean?) = safe(type) {
        val id = open ?: last ?: return@safe // nothing to attach to before the first command
        db.insertEvent(id, clock(), type, name?.take(MAX), label?.take(MAX), detail?.take(MAX), ok)
    }

    private inline fun safe(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            warn("run log: $what failed", e)
        }
    }

    companion object {
        private const val MAX = 500
        const val TOUCH_GRACE_MS = 60_000L
    }
}
