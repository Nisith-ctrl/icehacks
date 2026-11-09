package com.icehacks.saythegoal.agent

import com.icehacks.saythegoal.core.DeviceAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoAndMemoryTest {
    private val msgs = "com.google.android.apps.messaging"
    private val draft = screenOf(msgs,
        n("n0", "Maya"),
        n("n1", "Made it home!", cls = "android.widget.EditText", editable = true),
        n("n2", desc = "Attach", clickable = true),
    )
    private val picker = screenOf("com.google.android.providers.media.module",
        n("n0", desc = "Photo taken on Nov 8, 3:12 PM", clickable = true),
        n("n1", desc = "Photo taken on Nov 8, 6:40 PM", clickable = true),
    )
    private val withPhoto = screenOf(msgs,
        n("n0", "Maya"),
        n("n1", "Made it home!", cls = "android.widget.EditText", editable = true),
        n("n2", desc = "Attach", clickable = true),
        n("n3", desc = "Remove image attachment", clickable = true),
    )

    private fun agent(screen: FakeScreen, ledger: DraftLedger, voice: TestVoice, prefs: PreferenceStore = InMemoryPreferenceStore(), planner: Planner = FakePlanner(emptyList())): SayTheGoalAgent {
        val overlay = RecordingOverlay()
        return SayTheGoalAgent(screen, voice, overlay, loop(screen, planner, voice = voice, overlay = overlay, prefs = prefs, ledger = ledger), prefs, ledger, log = {})
    }

    @Test
    fun attachThenUndoOnlyThePhoto() = runTest {
        val screen = FakeScreen(draft) { a, cur ->
            when {
                a == DeviceAction.Tap("n2") && cur === draft -> picker
                a == DeviceAction.Tap("n0") && cur === picker -> withPhoto
                a == DeviceAction.Tap("n3") && cur === withPhoto -> draft
                else -> null
            }
        }
        val ledger = DraftLedger()
        val voice = TestVoice()
        val planner = FakePlanner(listOf(
            PlannerStep("tap", tool("node_id" to "n2", "caption" to "Opening attachments")),
            PlannerStep("tap", tool("node_id" to "n0", "caption" to "Attaching the photo", "edit_label" to "dog photo (yard)")),
            PlannerStep("done", tool("summary" to "Attached the dog photo.")),
        ))
        val a = agent(screen, ledger, voice, planner = planner)
        a.handle(com.icehacks.saythegoal.core.UserCommand.Goal("Take over. Attach yesterday's dog photo.", takeOver = true))
        val e = ledger.all().single()
        assertEquals(DraftLedger.Kind.ATTACHMENT, e.kind)
        assertEquals("dog photo (yard)", e.label)

        a.handle(com.icehacks.saythegoal.core.UserCommand.Undo("Undo the photo."))
        assertEquals(DeviceAction.Tap("n3"), screen.performed.last().first)
        assertEquals(draft, screen.current)
        assertTrue(voice.said.last(), voice.said.last().startsWith("Removed the dog photo (yard). Everything else is as it was."))
        assertTrue(ledger.isEmpty())
    }

    @Test
    fun undoRefusesWhenTheScreenNoLongerMatches() = runTest {
        val ledger = DraftLedger()
        ledger.detectAttachment(draft, withPhoto, "dog photo (yard)")
        val screen = FakeScreen(draft) // user already removed it by hand
        val voice = TestVoice()
        agent(screen, ledger, voice).undo("undo the photo")
        assertTrue(screen.performed.isEmpty())
        assertTrue(voice.said.last().contains("didn't change anything"))
    }

    @Test
    fun undoWithNothingToUndo() = runTest {
        val voice = TestVoice()
        agent(FakeScreen(draft), DraftLedger(), voice).undo("undo the photo")
        assertTrue(voice.said.last().startsWith("There's nothing I added"))
    }

    @Test
    fun undoTextRestoresTheOldValue() = runTest {
        val form = screenOf("com.android.chrome", n("n1", "Alex Rivera", cls = "android.widget.EditText", hint = "Full name", editable = true),
            n("n2", "alex@example.com", cls = "android.widget.EditText", hint = "Email", editable = true))
        val ledger = DraftLedger()
        ledger.recordText(form.copy(nodes = listOf(form.nodes[0].copy(text = ""), form.nodes[1])), form.nodes[0].copy(text = ""), "Alex Rivera")
        val screen = FakeScreen(form) { a, cur ->
            if (a is DeviceAction.EnterText) cur.copy(nodes = cur.nodes.map { if (it.id == a.nodeId) it.copy(text = a.text) else it }) else null
        }
        val voice = TestVoice()
        agent(screen, ledger, voice).undo("undo the name")
        assertEquals(DeviceAction.EnterText("n1", ""), screen.performed.single().first)
        assertTrue(voice.said.last().startsWith("Removed the Full name"))
        assertEquals("alex@example.com", screen.current.nodes[1].text)
    }

    @Test
    fun memoryListAndForget() = runTest {
        val prefs = InMemoryPreferenceStore()
        prefs.save(SavedPreference("event_registration", "confirmation_method", "Email", "Email. Remember that.", 1L))
        val voice = TestVoice()
        val a = agent(FakeScreen(draft), DraftLedger(), voice, prefs)
        a.memory("What do you remember?")
        assertEquals("I remember: use Email for event signups.", voice.said.last())
        a.memory("Forget the pizza preference")
        assertEquals("I don't have a saved preference like that.", voice.said.last())
        a.memory("Forget the event signup preference")
        assertEquals("Okay, I forgot: use Email for event signups.", voice.said.last())
        assertTrue(prefs.all().isEmpty())
    }
}
