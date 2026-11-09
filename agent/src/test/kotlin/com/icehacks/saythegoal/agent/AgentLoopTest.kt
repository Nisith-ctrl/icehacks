package com.icehacks.saythegoal.agent

import com.icehacks.saythegoal.core.ApprovalOutcome
import com.icehacks.saythegoal.core.DeviceAction
import com.icehacks.saythegoal.core.ScreenSnapshot
import com.icehacks.saythegoal.core.StgJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class AgentLoopTest {
    private val home: ScreenSnapshot =
        StgJson.decodeFromString(File("../fixtures/screens/home-screen.json").readText())
    private val gmail = screenOf("com.google.android.gm", n("n1", "Inbox"), n("n2", "Library workshop invite", clickable = true))

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

    @Test
    fun ic1OpenGmailFromHomeScreen() = runTest {
        val screen = FakeScreen(home) { a, _ -> if (a == DeviceAction.Tap("n16")) gmail else null }
        val overlay = RecordingOverlay()
        val planner = FakePlanner(listOf(
            PlannerStep("tap", tool("node_id" to "n16", "caption" to "Opening Gmail")),
            PlannerStep("done", tool("summary" to "Gmail is open.")),
        ))
        val voice = TestVoice()
        val out = loop(screen, planner, voice = voice, overlay = overlay).run("open Gmail", false)
        assertEquals(Outcome.Done("Gmail is open."), out)
        assertEquals(DeviceAction.Tap("n16"), screen.performed.single().first)
        assertTrue("point Opening Gmail" in overlay.calls)
        assertEquals(listOf("Gmail is open."), voice.said)
        assertTrue(planner.turns[0].screen.contains("n16 TextView \"Gmail\""))
        assertTrue(planner.turns[1].history.single().contains("tap \"Gmail\" -> ok, now gmail"))
    }

    @Test
    fun submitNeedsSpokenApprovalAndNeverPlainTap() = runTest {
        val screen = FakeScreen(form("Alex Rivera", "Email")) { a, _ -> if (a is DeviceAction.Tap && a.isCommit) confirmation else null }
        val gate = ScriptedGate(ApprovalOutcome.APPROVED)
        val ledger = DraftLedger()
        val planner = FakePlanner(listOf(
            PlannerStep("tap", tool("node_id" to "n4", "caption" to "Submitting")), // rejected: commit via tap
            PlannerStep("submit", tool("node_id" to "n4", "readback" to "Library workshop, Thursday 10 AM, Alex Rivera, by email.", "commit_word" to "Submit")),
            PlannerStep("submit", tool("node_id" to "n1", "readback" to "again", "commit_word" to "Submit")), // second submit refused
            PlannerStep("done", tool("summary" to "You're registered.")),
        ))
        val out = loop(screen, planner, gate = gate, ledger = ledger).run("register me", false)
        assertEquals(Outcome.Done("You're registered."), out)
        val commits = screen.performed.filter { (it.first as? DeviceAction.Tap)?.isCommit == true }
        assertEquals(1, commits.size)
        assertNotNull(commits.single().second)
        assertEquals(1, screen.performed.size) // the plain tap on Submit never reached the device
        assertTrue(planner.turns[1].feedback!!.contains("use the submit tool"))
        assertTrue(planner.turns[3].feedback!!.contains("Never submit twice"))
        assertEquals("Library workshop, Thursday 10 AM, Alex Rivera, by email.", gate.readback)
    }

    @Test
    fun cancelLeavesTheFormAlone() = runTest {
        val screen = FakeScreen(form("Alex Rivera", "Email"))
        val voice = TestVoice()
        val planner = FakePlanner(listOf(
            PlannerStep("submit", tool("node_id" to "n4", "readback" to "Library workshop.", "commit_word" to "Submit")),
        ))
        val out = loop(screen, planner, voice = voice, gate = ScriptedGate(ApprovalOutcome.CANCELLED)).run("register me", false)
        assertEquals(Outcome.Cancelled, out)
        assertTrue(screen.performed.isEmpty())
        assertTrue(voice.said.last().contains("cancelled"))
    }

    @Test
    fun rememberedAnswerIsSavedButIsNotApproval() = runTest {
        val screen = FakeScreen(form("Alex Rivera"))
        val prefs = InMemoryPreferenceStore()
        val voice = TestVoice(listOf("Email. Remember that for event signups."))
        val planner = FakePlanner(listOf(
            PlannerStep("ask_user", tool("question" to "Which confirmation method should I use?",
                "choices" to listOf("Email", "Text message"), "remember_scope" to "event_registration", "remember_key" to "confirmation_method")),
            PlannerStep("stop", tool("reason" to "test end")),
        ))
        loop(screen, planner, voice = voice, prefs = prefs).run("register me", false)
        val saved = prefs.all().single()
        assertEquals("Email", saved.value)
        assertEquals("event_registration", saved.scope)
        assertTrue(voice.said.first().startsWith("Saved: use Email for event signups"))
        assertTrue(planner.turns[1].feedback!!.contains("not approval"))
        assertTrue(planner.turns[1].preferences.single().contains("confirmation method = Email"))
    }

    @Test
    fun oneTimeAnswerIsNotSaved() = runTest {
        val prefs = InMemoryPreferenceStore()
        val planner = FakePlanner(listOf(
            PlannerStep("ask_user", tool("question" to "Which method?", "choices" to listOf("Email", "Text message"),
                "remember_scope" to "event_registration", "remember_key" to "confirmation_method")),
        ))
        loop(FakeScreen(form()), planner, voice = TestVoice(listOf("Text message")), prefs = prefs).run("register me", false)
        assertTrue(prefs.all().isEmpty())
    }

    @Test
    fun invalidChoicesStopAfterThree() = runTest {
        val voice = TestVoice()
        val planner = FakePlanner(List(5) { PlannerStep("tap", tool("node_id" to "n999", "caption" to "x")) })
        val out = loop(FakeScreen(form()), planner, voice = voice).run("x", false)
        assertTrue(out is Outcome.Stopped)
        assertEquals(3, planner.turns.size) // three invalid choices, then it stops without asking again
    }

    @Test
    fun leavingTheFormHostStops() = runTest {
        val elsewhere = screenOf(chrome, n("n0", "evil.example.com/phish", viewId = "com.android.chrome:id/url_bar"))
        val planner = FakePlanner(listOf(PlannerStep("done", tool("summary" to "should not get here"))))
        val out = loop(FakeScreen(elsewhere), planner).run("x", false)
        assertTrue(out is Outcome.Stopped)
        assertTrue(planner.turns.isEmpty())
    }

    @Test
    fun anotherVercelSiteIsNotAllowed() = runTest {
        val other = screenOf(chrome, n("n0", "someone-else.vercel.app/library", viewId = "com.android.chrome:id/url_bar"))
        val planner = FakePlanner(listOf(PlannerStep("done", tool("summary" to "should not get here"))))
        assertTrue(loop(FakeScreen(other), planner).run("x", false) is Outcome.Stopped)
        assertTrue(planner.turns.isEmpty())
    }

    @Test
    fun calendarConflictCheck() = runTest {
        val zone = ZoneId.systemDefault()
        fun ms(h: Int) = ZonedDateTime.of(2026, 11, 10, h, 0, 0, 0, zone).toInstant().toEpochMilli()
        val cal = FakeCalendar(listOf(CalendarEvent("Dentist", ms(14), ms(15))))
        val options = JsonArray(listOf(
            buildJsonObject { put("label", "Session A"); put("start", "2026-11-10T14:30"); put("end", "2026-11-10T16:00") },
            buildJsonObject { put("label", "Session B"); put("start", "2026-11-10T10:00"); put("end", "2026-11-10T11:00") },
        ))
        val planner = FakePlanner(listOf(PlannerStep("check_calendar", tool("options" to options))))
        loop(FakeScreen(gmail), planner, calendar = cal).run("x", false)
        val fb = planner.turns[1].feedback!!
        assertTrue(fb, fb.contains("Session A") && fb.contains("conflicts with \"Dentist\""))
        assertTrue(fb, Regex("Session B \\([^)]*\\): free").containsMatchIn(fb))
        assertEquals(1, conflicts(cal.events(0, 0), ms(14) + 1, ms(16)).size)
        assertTrue(conflicts(cal.events(0, 0), ms(15), ms(16)).isEmpty()) // back-to-back is fine
    }

    @Test
    fun enterTextIsRecordedForUndo() = runTest {
        val screen = FakeScreen(form()) { a, cur ->
            if (a is DeviceAction.EnterText) cur.copy(nodes = cur.nodes.map { if (it.id == a.nodeId) it.copy(text = a.text) else it }) else null
        }
        val ledger = DraftLedger()
        val planner = FakePlanner(listOf(PlannerStep("enter_text", tool("node_id" to "n1", "text" to "Alex Rivera", "caption" to "Filling in your name"))))
        loop(screen, planner, ledger = ledger).run("x", false)
        val e = ledger.all().single()
        assertEquals(DraftLedger.Kind.TEXT, e.kind)
        assertEquals("Full name", e.label)
        assertEquals("Alex Rivera", e.afterText)
    }

    @Test
    fun choicesAndRememberHelpers() {
        assertEquals("Email", choiceIn("Email. Remember that for event signups.", listOf("Email", "Text message")))
        assertEquals("Text message", choiceIn("text message please", listOf("Email", "Text message")))
        assertNull(choiceIn("pizza", listOf("Email", "Text message")))
        assertTrue(wantsRemember("Email. Remember that for event signups."))
        assertTrue(wantsRemember("always use email"))
        assertFalse(wantsRemember("Email"))
        assertTrue(AgentLoop.looksLikeCommit(n("n1", "Submit", clickable = true)))
        assertTrue(AgentLoop.looksLikeCommit(n("n1", desc = "Send SMS", clickable = true)))
        assertFalse(AgentLoop.looksLikeCommit(n("n1", "Session", clickable = true)))
    }
}
