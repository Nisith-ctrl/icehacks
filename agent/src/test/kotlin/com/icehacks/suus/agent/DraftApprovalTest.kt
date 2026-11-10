package com.icehacks.suus.agent

import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.Approval
import com.icehacks.suus.core.ApprovalGate
import com.icehacks.suus.core.ApprovalOutcome
import com.icehacks.suus.core.ApprovalResult
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.UserCommand
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T03 M4: approval and cancel on a Messages draft the agent took over. */
class DraftApprovalTest {
    private val msgs = "com.google.android.apps.messaging"
    private val withPhoto = screenOf(msgs,
        n("n0", "Maya"),
        n("n1", "Made it home!", cls = "android.widget.EditText", editable = true),
        n("n2", desc = "Remove image attachment", clickable = true),
        n("n3", desc = "Send MMS", clickable = true),
    )
    private val sent = screenOf(msgs, n("n0", "Maya"), n("n1", "Made it home!"), n("n2", "Sent"))

    /** Hands out a fresh approval (new fingerprint) per request and counts them. */
    private class CountingGate(private val outcome: ApprovalOutcome) : ApprovalGate {
        val requests = mutableListOf<String>()
        override suspend fun request(readback: String, snapshot: ScreenSnapshot, commitWord: String): ApprovalResult {
            requests += readback
            return ApprovalResult(outcome, if (outcome == ApprovalOutcome.APPROVED) Approval("fp${requests.size}", requests.size.toLong()) else null)
        }
    }

    /** Refuses the first commit as if the draft changed after approval (what the device controller does). */
    private class StaleOnceScreen(var current: ScreenSnapshot, private val afterSend: ScreenSnapshot) : ScreenController {
        val commits = mutableListOf<Approval?>()
        override suspend fun snapshot() = current
        override suspend fun perform(action: DeviceAction, on: ScreenSnapshot, approval: Approval?): ActionResult {
            if (action !is DeviceAction.Tap || !action.isCommit) return ActionResult.Done(current, false)
            commits += approval
            if (commits.size == 1) return ActionResult.NotAllowed("screen changed after approval")
            current = afterSend
            return ActionResult.Done(afterSend, true)
        }
    }

    @Test
    fun approvalIsInvalidatedWhenTheDraftChangesAfterIt() = runTest {
        val screen = StaleOnceScreen(withPhoto, sent)
        val gate = CountingGate(ApprovalOutcome.APPROVED)
        val ledger = DraftLedger().apply { detectAttachment(screenOf(msgs), withPhoto, "dog photo (door)") }
        val planner = FakePlanner(listOf(
            PlannerStep("submit", tool("node_id" to "n3", "readback" to "To Maya: Made it home!, with the dog photo.", "commit_word" to "Send")),
            PlannerStep("submit", tool("node_id" to "n3", "readback" to "To Maya: Made it home!, with the dog photo.", "commit_word" to "Send")),
            PlannerStep("done", tool("summary" to "Sent to Maya.")),
        ))
        val out = AgentLoop(screen, TestVoice(), gate, RecordingOverlay(), planner, InMemoryPreferenceStore(), ledger, null, null, log = {})
            .run("Take over. Send it.", takeOver = true)

        assertEquals(Outcome.Done("Sent to Maya."), out)
        // The refused send did not count: the draft (and its undo history) stayed until the real send.
        assertTrue(planner.turns[1].feedback!!.contains("NOT submitted"))
        // The second send needed a second spoken approval, not the stale one.
        assertEquals(2, gate.requests.size)
        assertEquals(2, screen.commits.size)
        assertNotEquals(screen.commits[0], screen.commits[1])
        assertTrue(ledger.isEmpty()) // cleared only after the real send
    }

    @Test
    fun refusedSendKeepsTheUndoHistory() = runTest {
        val screen = StaleOnceScreen(withPhoto, sent)
        val ledger = DraftLedger().apply { detectAttachment(screenOf(msgs), withPhoto, "dog photo (door)") }
        val planner = FakePlanner(listOf(
            PlannerStep("submit", tool("node_id" to "n3", "readback" to "To Maya.", "commit_word" to "Send")),
            PlannerStep("stop", tool("reason" to "The draft changed.")),
        ))
        AgentLoop(screen, TestVoice(), CountingGate(ApprovalOutcome.APPROVED), RecordingOverlay(), planner, InMemoryPreferenceStore(), ledger, null, null, log = {})
            .run("Take over. Send it.", takeOver = true)
        assertEquals(withPhoto, screen.current)
        assertEquals("dog photo (door)", ledger.all().single().label)
    }

    @Test
    fun cancelLeavesTheDraftAndItCanStillBeUndone() = runTest {
        val screen = FakeScreen(withPhoto) { a, cur ->
            if (a == DeviceAction.Tap("n2") && cur === withPhoto) screenOf(msgs, n("n0", "Maya"), n("n1", "Made it home!", cls = "android.widget.EditText", editable = true)) else null
        }
        val ledger = DraftLedger().apply { detectAttachment(screenOf(msgs), withPhoto, "dog photo (door)") }
        val voice = TestVoice()
        val planner = FakePlanner(listOf(
            PlannerStep("submit", tool("node_id" to "n3", "readback" to "To Maya: Made it home!, with the dog photo.", "commit_word" to "Send")),
        ))
        val overlay = RecordingOverlay()
        val prefs = InMemoryPreferenceStore()
        val agent = SuusAgent(screen, voice, overlay, loop(screen, planner, voice = voice, gate = ScriptedGate(ApprovalOutcome.CANCELLED), overlay = overlay, prefs = prefs, ledger = ledger), prefs, ledger, log = {})

        agent.handle(UserCommand.Goal("Take over. Send it.", takeOver = true))
        assertTrue(screen.performed.isEmpty()) // nothing tapped, nothing sent
        assertEquals(withPhoto, screen.current)
        assertFalse(ledger.isEmpty())
        assertTrue(voice.said.any { it.contains("cancelled") })

        agent.handle(UserCommand.Undo("Undo the photo."))
        assertEquals(DeviceAction.Tap("n2"), screen.performed.single().first)
        assertTrue(ledger.isEmpty())
    }
}
