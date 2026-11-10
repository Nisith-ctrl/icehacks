package com.icehacks.suus.agent.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.icehacks.suus.agent.AgentLoop
import com.icehacks.suus.agent.DraftLedger
import com.icehacks.suus.agent.FakePlanner
import com.icehacks.suus.agent.FakeScreen
import com.icehacks.suus.agent.Outcome
import com.icehacks.suus.agent.PlannerStep
import com.icehacks.suus.agent.RecordingOverlay
import com.icehacks.suus.agent.SuusAgent
import com.icehacks.suus.agent.ScriptedGate
import com.icehacks.suus.agent.TestVoice
import com.icehacks.suus.agent.n
import com.icehacks.suus.agent.screenOf
import com.icehacks.suus.agent.tool
import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.AgentRunner
import com.icehacks.suus.core.ApprovalOutcome
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.UserCommand
import com.icehacks.suus.core.VoiceIO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RunLogTest {
    private lateinit var db: SuusDatabase
    private var now = 1_000L
    private val clock = { now }

    @Before fun open() { db = SuusDatabase(BundledSQLiteDriver(), ":memory:") }
    @After fun close() = db.close()

    private val chrome = "com.android.chrome"
    private fun form(name: String = "", method: String? = null) = screenOf(
        chrome,
        n("n0", "saythegoal-forms-nisith.vercel.app/library", cls = "android.widget.EditText", viewId = "com.android.chrome:id/url_bar"),
        n("n1", name, cls = "android.widget.EditText", hint = "Full name", editable = true),
        n("n2", "Email", cls = "android.widget.RadioButton", checkable = true, checked = method == "Email", clickable = true),
        n("n3", "Text message", cls = "android.widget.RadioButton", checkable = true, checked = method == "Text message", clickable = true),
        n("n4", "Submit", cls = "android.widget.Button", clickable = true),
    )
    private val confirmation = screenOf(chrome,
        n("n0", "saythegoal-forms-nisith.vercel.app/library/confirmed", viewId = "com.android.chrome:id/url_bar"), n("n1", "Registration confirmed"))

    /** The real agent (loop + router) with the run log wrapped around its parts, as createAgent will do it. */
    private fun loggedAgent(log: RunLog, screen: ScreenController, voice: VoiceIO, gate: ScriptedGate, planner: FakePlanner): AgentRunner {
        val s = log.screen(screen)
        val v = log.voice(voice)
        val a = log.approval(gate)
        val overlay = RecordingOverlay()
        val prefs = DbPreferenceStore(db)
        val ledger = DraftLedger()
        val loop = AgentLoop(s, v, a, overlay, planner, prefs, ledger, null, null,
            now = { ZonedDateTime.of(2026, 11, 9, 9, 0, 0, 0, ZoneId.of("UTC")) }, log = {})
        return log.agent(SuusAgent(s, v, overlay, loop, prefs, ledger, log = {}))
    }

    @Test
    fun libraryDemoIsLoggedEndToEnd() = runTest {
        val logger = DbRunLogger(db, clock)
        val log = RunLog(logger, clock)
        val screen = FakeScreen(form()) { a, cur ->
            now += 700
            when {
                a is DeviceAction.EnterText -> form(a.text)
                a == DeviceAction.Tap("n2") -> form(cur.nodes[1].text ?: "", "Email")
                a is DeviceAction.Tap && a.isCommit -> confirmation
                else -> null
            }
        }
        val planner = FakePlanner(listOf(
            PlannerStep("enter_text", tool("node_id" to "n1", "text" to "Alex Rivera", "caption" to "Your name")),
            PlannerStep("ask_user", tool(
                "question" to "Which confirmation method should I use?",
                "choices" to listOf("Email", "Text message"),
                "remember_scope" to "event_registration", "remember_key" to "confirmation_method",
            )),
            PlannerStep("tap", tool("node_id" to "n2", "caption" to "Email")),
            PlannerStep("submit", tool("node_id" to "n4", "readback" to "Library workshop, Thursday 10 AM, Alex Rivera, by email.", "commit_word" to "Submit")),
            PlannerStep("done", tool("summary" to "You're registered for Thursday at 10.")),
        ))
        val voice = TestVoice(listOf("Email. Remember that for event signups."))
        val agent = loggedAgent(log, screen, voice, ScriptedGate(ApprovalOutcome.APPROVED), planner)

        agent.handle(UserCommand.Goal("find the library workshop invite and register me", takeOver = false))

        val session = logger.sessions().single()
        assertEquals("goal", session.kind)
        assertFalse(session.takeOver)
        assertEquals("finished", session.outcome)
        assertEquals("You're registered for Thursday at 10.", session.outcomeDetail)

        val events = logger.events()
        val actions = events.filter { it.type == EventType.ACTION }
        assertEquals(listOf("type", "tap", "submit"), actions.map { it.name })
        assertEquals(listOf("Full name", "Email", "Submit"), actions.map { it.label })
        assertTrue(actions.all { it.ok == true })
        assertEquals("11 chars", actions[0].detail)
        assertTrue("typed values are never logged", events.none { "Alex Rivera" in (it.label ?: "") + (it.detail ?: "") && it.type == EventType.ACTION })

        val q = events.single { it.type == EventType.QUESTION }
        assertEquals("Which confirmation method should I use?", q.label)
        assertEquals("Email. Remember that for event signups.", q.detail)
        val approval = events.single { it.type == EventType.APPROVAL }
        assertEquals("Submit", approval.name)
        assertEquals("APPROVED", approval.detail)
        assertTrue(events.any { it.type == EventType.SAID && it.label == "Saved: use Email for event signups." })

        val m = RunSummaries.of(session, events)
        assertEquals(3, m.actions)
        assertEquals(0, m.failedActions)
        assertEquals(3, m.spokenCommands) // the goal, the answer, "Submit"
        assertEquals(1, m.questions)
        assertEquals(1, m.approvals)
        assertEquals(1, m.approvalsGranted)
        assertEquals(0, m.userTouches)
        assertEquals(2_100L, m.durationMs)

        // The saved preference went to the shared database store.
        assertEquals("Email", DbPreferenceStore(db).all().single().value)
    }

    @Test
    fun stopEndsTheRunningSessionAndCountsAsSpoken() = runTest {
        val logger = DbRunLogger(db, clock)
        val log = RunLog(logger, clock)
        val asking = CompletableDeferred<Unit>()
        val voice = object : VoiceIO {
            override val commands = emptyFlow<UserCommand>()
            override suspend fun say(text: String) {}
            override suspend fun ask(question: String, choices: List<String>): String? { asking.complete(Unit); awaitCancellation() }
        }
        val planner = FakePlanner(listOf(PlannerStep("ask_user", tool("question" to "Which session?"))))
        val agent = loggedAgent(log, FakeScreen(form()), voice, ScriptedGate(ApprovalOutcome.NO_ANSWER), planner)

        val job = launch { agent.handle(UserCommand.Goal("register me", takeOver = false)) }
        asking.await()
        now = 5_000
        job.cancel()
        job.join()
        agent.handle(UserCommand.Stop)

        val session = logger.sessions().single()
        assertEquals("stopped_by_user", session.outcome)
        assertEquals(5_000L, session.endedAtMs)
        val events = logger.events()
        assertEquals(1, events.count { it.type == EventType.STOP })
        assertEquals(0, events.count { it.type == EventType.QUESTION }) // cancelled mid-question: not an answer
        assertEquals(2, RunSummaries.of(session, events).spokenCommands)
    }

    @Test
    fun undoAndMemoryCommandsAreTheirOwnSessions() = runTest {
        val logger = DbRunLogger(db, clock)
        val log = RunLog(logger, clock)
        val agent = loggedAgent(log, FakeScreen(form()), TestVoice(), ScriptedGate(ApprovalOutcome.NO_ANSWER), FakePlanner(emptyList()))
        agent.handle(UserCommand.Undo("undo the photo"))
        agent.handle(UserCommand.Memory("what do you remember"))
        assertEquals(listOf("undo", "memory"), logger.sessions().map { it.kind })
        assertEquals(listOf("There's nothing I added that I can undo.", "I haven't saved any preferences."), logger.sessions().map { it.outcomeDetail })
        assertEquals(1, RunSummaries.total(logger.sessions(), logger.events()).undos)
    }

    @Test
    fun anAgentCrashIsLoggedAsError() = runTest {
        val logger = DbRunLogger(db, clock)
        val agent = RunLog(logger, clock).agent(object : AgentRunner {
            override suspend fun handle(command: UserCommand) = error("boom")
        })
        runCatching { agent.handle(UserCommand.Goal("x", false)) }
        assertEquals("error", logger.sessions().single().outcome)
    }

    @Test
    fun failedAndRefusedActionsAreLoggedWithTheReason() = runTest {
        val logger = DbRunLogger(db, clock)
        val log = RunLog(logger, clock)
        logger.sessionStarted(SessionKind.GOAL, "send it")
        val refusing = object : ScreenController {
            override suspend fun snapshot() = form()
            override suspend fun perform(action: DeviceAction, on: ScreenSnapshot, approval: com.icehacks.suus.core.Approval?) =
                ActionResult.NotAllowed("commit without approval")
        }
        log.screen(refusing).perform(DeviceAction.Tap("n4", isCommit = true), form())
        log.screen(refusing).perform(DeviceAction.Scroll(null, com.icehacks.suus.core.ScrollDirection.DOWN), form())
        val (submit, scroll) = logger.events()
        assertEquals("submit", submit.name)
        assertEquals(false, submit.ok)
        assertEquals("not allowed: commit without approval", submit.detail)
        assertEquals("scroll", scroll.name)
        assertNull(scroll.label)
        assertEquals("down; not allowed: commit without approval", scroll.detail)
    }

    @Test
    fun clicksCountAsUserTouchesOnlyWhenTheAgentIsNotActing() = runTest {
        val logger = DbRunLogger(db, clock)
        val log = RunLog(logger, clock)
        val CLICK = 1
        val LONG_CLICK = 2
        val SCROLLED = 4096

        log.onUiEvent(CLICK, "com.google.android.gm") // no session yet: dropped
        logger.sessionStarted(SessionKind.GOAL, "register me")

        val inFlight = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val slow = object : ScreenController {
            override suspend fun snapshot() = form()
            override suspend fun perform(action: DeviceAction, on: ScreenSnapshot, approval: com.icehacks.suus.core.Approval?): ActionResult {
                inFlight.complete(Unit); release.await(); return ActionResult.Done(on, true)
            }
        }
        val job = launch { log.screen(slow).perform(DeviceAction.Tap("n2"), form()) }
        inFlight.await()
        log.onUiEvent(CLICK, chrome)                     // the agent's own tap: ignored
        release.complete(Unit); job.join()
        now += RunLog.QUIET_AFTER_ACTION_MS - 1
        log.onUiEvent(CLICK, chrome)                     // echo just after the action: ignored
        now += 10
        log.onUiEvent(CLICK, chrome)                     // the user: counted
        log.onUiEvent(LONG_CLICK, "com.google.android.apps.messaging") // counted
        log.onUiEvent(SCROLLED, chrome)                  // not a touch event we count
        log.onUiEvent(CLICK, "com.icehacks.suus")  // our own app (setup screen): ignored
        logger.sessionEnded("finished")

        val touches = logger.events().filter { it.type == EventType.TOUCH }
        assertEquals(listOf("tap in com.android.chrome", "long press in com.google.android.apps.messaging"), touches.map { it.label })
        assertEquals(2, RunSummaries.of(logger.sessions().single(), logger.events()).userTouches)
    }

    @Test
    fun loggerRules() {
        val logger = DbRunLogger(db, clock)
        logger.actionPerformed("tap", "Gmail", true) // before any session: dropped
        logger.sessionStarted(SessionKind.GOAL, "first")
        logger.sessionStarted(SessionKind.GOAL, "second", takeOver = true) // first never ended
        logger.sessionOutcome(Outcome.Cancelled)
        now = 4_000
        logger.sessionEnded("finished", "last words")
        logger.userTouch("tap in launcher") // between commands: goes on the last session

        val (first, second) = logger.sessions()
        assertEquals("unknown", first.outcome)
        assertEquals("cancelled", second.outcome) // the explicit outcome wins over the default
        assertEquals("the user said Cancel at the approval", second.outcomeDetail)
        assertTrue(second.takeOver)
        assertEquals(4_000L, second.endedAtMs)
        assertEquals(listOf(second.id), logger.events().map { it.sessionId })

        logger.clear()
        assertTrue(logger.sessions().isEmpty())
        assertTrue(logger.events().isEmpty())
        logger.agentSaid("after clear, no session") // dropped
        assertTrue(logger.events().isEmpty())
        logger.sessionStarted(SessionKind.MEMORY, "what do you remember")
        assertTrue("ids are never reused after a clear", logger.sessions().single().id > second.id)
    }

    @Test
    fun aBrokenDatabaseNeverBreaksTheAgent() {
        val warnings = mutableListOf<String>()
        val closed = SuusDatabase(BundledSQLiteDriver(), ":memory:").also { it.close() }
        val logger = DbRunLogger(closed, clock) { msg, _ -> warnings += msg }
        logger.sessionStarted(SessionKind.GOAL, "x")
        logger.actionPerformed("tap", "Gmail", true)
        logger.sessionEnded("finished")
        assertTrue(warnings.isNotEmpty())
    }

    @Test
    fun noOpLoggerDoesNothing() = runTest {
        val agent = RunLog(NoOpRunLogger).agent(object : AgentRunner { override suspend fun handle(command: UserCommand) {} })
        agent.handle(UserCommand.Goal("x", false))
        agent.handle(UserCommand.Stop)
    }
}
