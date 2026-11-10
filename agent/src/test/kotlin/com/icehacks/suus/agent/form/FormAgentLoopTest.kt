package com.icehacks.suus.agent.form

import com.icehacks.suus.agent.AgentConfig
import com.icehacks.suus.agent.AgentLoop
import com.icehacks.suus.agent.DraftLedger
import com.icehacks.suus.agent.FakePlanner
import com.icehacks.suus.agent.InMemoryPreferenceStore
import com.icehacks.suus.agent.Outcome
import com.icehacks.suus.agent.PlannerStep
import com.icehacks.suus.agent.RecordingOverlay
import com.icehacks.suus.agent.SavedPreference
import com.icehacks.suus.agent.ScriptedGate
import com.icehacks.suus.agent.TestVoice
import com.icehacks.suus.agent.tool
import com.icehacks.suus.core.ApprovalOutcome
import com.icehacks.suus.core.DeviceAction
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** IC4 shape, offline: volunteer form, saved Email preference reused, role asked, fresh approval. */
class FormAgentLoopTest {
    private val emailPref = SavedPreference("event_registration", "confirmation_method", "Email", "Email. Remember that for event signups.", 1L)

    private fun loop(page: FakeChromeForm, voice: TestVoice, planner: FakePlanner) = AgentLoop(
        page, voice, ScriptedGate(ApprovalOutcome.APPROVED), RecordingOverlay(), planner,
        InMemoryPreferenceStore().apply { save(emailPref) }, DraftLedger(), null, null,
        config = AgentConfig(apiKey = "", model = "test", visionModel = "test", autoFillForms = true),
        now = { ZonedDateTime.of(2026, 11, 10, 9, 0, 0, 0, ZoneId.of("America/New_York")) }, log = {},
    )

    @Test
    fun volunteerSignupWithTheFormEngine() = runTest {
        val page = FakeChromeForm()
        val voice = TestVoice(listOf("Food serving please"))
        val planner = FakePlanner(listOf(
            PlannerStep("ask_user", tool("question" to "Which volunteer role would you like?",
                "choices" to listOf("Welcome table", "Food serving", "Setup crew", "Cleanup crew"))),
            PlannerStep("fill_form", tool("caption" to "Choosing your shift", "answers" to buildJsonObject {
                put("Shift", "Sunday, November 15, 9:00 AM to 12:00 PM")
            })),
            PlannerStep("submit", tool("node_id" to "go", "commit_word" to "Submit",
                "readback" to "Harvest Festival, Sunday November 15, 9 to noon, food serving, confirmation by email.")),
            PlannerStep("done", tool("summary" to "You're signed up for Sunday morning.")),
        ))

        val out = loop(page, voice, planner).run("Sign me up for the volunteer shift that fits my calendar", false)

        assertEquals(Outcome.Done("You're signed up for Sunday morning."), out)
        assertEquals("Email", page.confirmBy)              // saved preference reused, not asked
        assertEquals("Food serving", page.role)            // asked once, put straight into the dropdown
        assertEquals("Sunday, November 15, 9:00 AM to 12:00 PM", page.shift)
        assertEquals("Alex Rivera", page.texts["name"])
        assertTrue(page.submittedPage)
        assertEquals(1, page.performed.count { it is DeviceAction.Tap && it.isCommit })
        assertEquals(listOf("Which volunteer role would you like?"), voice.asked)

        // Turn 1: the form was already filled in code; the model is told what's left.
        val t0 = planner.turns[0].feedback!!
        assertTrue(t0, t0.startsWith("I filled the form in code."))
        assertTrue(t0, t0.contains("Preferred volunteer role: ask the user (options: Welcome table, Food serving, Setup crew, Cleanup crew)"))
        // Turn 2: the answer went into the field without another tool call.
        assertTrue(planner.turns[1].feedback!!, planner.turns[1].feedback!!.contains("I put it in \"Preferred volunteer role\""))
        // After submit: verified in code.
        assertTrue(planner.turns[3].feedback!!, planner.turns[3].feedback!!.contains("Confirmation page verified: \"You're signed up\", confirmation number VOL-123456"))
    }

    @Test
    fun aShiftNamedInTheGoalIsPickedInCode() = runTest {
        val page = FakeChromeForm()
        val planner = FakePlanner(listOf(PlannerStep("stop", tool("reason" to "test end"))))
        loop(page, TestVoice(), planner).run("Take over. Fill in this volunteer form with the Sunday shift.", true)
        assertEquals("Sunday, November 15, 9:00 AM to 12:00 PM", page.shift)
        assertEquals("Email", page.confirmBy)
        assertEquals(null, page.role)
    }
}
