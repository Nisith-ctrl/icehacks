package com.icehacks.suus.agent

import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.SuusJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Google Messages 20261027 (updated from Play on 2026-11-11) on the API 35 emulator: the draft to Maya,
 * the + panel, the Files picker it opens, and the dialog the emulator shows because it has no MMS.
 */
class Messages2026FixtureTest {
    private fun fixture(name: String): ScreenSnapshot =
        SuusJson.decodeFromString(File("../fixtures/screens/$name.json").readText())
    private fun ScreenSnapshot.node(label: String) = nodes.first { (it.text ?: it.contentDescription) == label }

    @Test
    fun sendIsACommitAndTheAttachControlsAreNot() {
        val draft = fixture("messages-draft-2026")
        assertTrue(AgentLoop.looksLikeCommit(draft, draft.node("Send SMS")))
        assertFalse(AgentLoop.looksLikeCommit(draft, draft.node("Show attach content screen")))
        val panel = fixture("messages-attach-panel")
        for (label in listOf("Files", "Camera", "GIFs", "Schedule Send")) {
            assertFalse(label, AgentLoop.looksLikeCommit(panel, panel.node(label)))
        }
    }

    @Test
    fun filesListsBothSeedPhotosByName() {
        val files = fixture("files-picker-recent")
        assertEquals("com.google.android.documentsui", files.packageName)
        assertTrue(files.nodes.any { it.text == "IMG_0418.png" })
        assertTrue(files.nodes.any { it.text == "IMG_0422.png" })
    }

    @Test
    fun theUnsupportedDialogIsNotAnAttachment() {
        // Tapping OK must not count as an attachment edit, so "undo the photo" has nothing to remove.
        val draft = fixture("messages-draft-2026")
        val dialog = fixture("messages-attachments-unsupported")
        val ledger = DraftLedger()
        assertEquals(null, ledger.detectAttachment(draft, dialog, "dog photo (yard)"))
        assertTrue(dialog.nodes.any { it.contentDescription?.contains("not supported") == true || it.text?.contains("not supported") == true })
        assertFalse(AgentLoop.looksLikeCommit(dialog, dialog.node("OK")))
    }
}

class PhotoChoiceTest {
    @Test
    fun fileNameChoicesAreSentBackForPlainWords() = kotlinx.coroutines.test.runTest {
        val draft: ScreenSnapshot = SuusJson.decodeFromString(File("../fixtures/screens/files-picker-recent.json").readText())
        val voice = TestVoice(listOf("The one in the yard"))
        val planner = FakePlanner(listOf(
            PlannerStep("ask_user", tool("question" to "Which photo?", "choices" to kotlinx.serialization.json.JsonArray(
                listOf(kotlinx.serialization.json.JsonPrimitive("IMG_0422.png (9.59 KB)"), kotlinx.serialization.json.JsonPrimitive("IMG_0418.png (12.62 KB)"))))),
            PlannerStep("ask_user", tool("question" to "Which photo?", "choices" to kotlinx.serialization.json.JsonArray(
                listOf(kotlinx.serialization.json.JsonPrimitive("The one in the yard"), kotlinx.serialization.json.JsonPrimitive("The one by the door"))))),
            PlannerStep("stop", tool("reason" to "test over")),
        ))
        loop(FakeScreen(draft), planner, voice = voice).run("Take over. Attach the dog photo from yesterday.", takeOver = true)
        assertEquals(listOf("Which photo?"), voice.asked) // the file-name question was never spoken
        assertTrue(planner.turns[1].feedback!!.contains("find_photos"))
    }
}

class FilesPickerTest {
    @Test
    fun aTapOnPreviewPicksTheFileInstead() {
        val files: ScreenSnapshot = SuusJson.decodeFromString(File("../fixtures/screens/files-picker-recent.json").readText())
        val preview = files.nodes.first { it.contentDescription == "Preview the file IMG_0418.png" }
        assertEquals("IMG_0418.png", AgentLoop.pickInsteadOfPreview(files, preview).text)
        val name = files.nodes.first { it.text == "IMG_0422.png" }
        assertEquals(name, AgentLoop.pickInsteadOfPreview(files, name))
    }
}

class UnsupportedAttachmentTest {
    private fun fixture(name: String): ScreenSnapshot =
        SuusJson.decodeFromString(File("../fixtures/screens/$name.json").readText())

    @Test
    fun theAgentStopsWithTheDraftUntouchedWhenMessagesCantAttach() = kotlinx.coroutines.test.runTest {
        val files = fixture("files-picker-recent")
        val dialog = fixture("messages-attachments-unsupported")
        val draft = fixture("messages-draft-2026")
        val screen = FakeScreen(files) { a, cur ->
            when {
                cur === files && a == com.icehacks.suus.core.DeviceAction.Tap("n110") -> dialog
                cur === dialog && a == com.icehacks.suus.core.DeviceAction.Tap("n10") -> draft
                else -> null
            }
        }
        val ledger = DraftLedger()
        val voice = TestVoice()
        val planner = FakePlanner(listOf(
            PlannerStep("tap", tool("node_id" to "n113", "caption" to "Attaching", "edit_label" to "dog photo (yard)")),
            PlannerStep("tap", tool("node_id" to "n24", "caption" to "should never run")),
        ))
        val out = loop(screen, planner, voice = voice, ledger = ledger).run("Take over. Attach the dog photo.", takeOver = true)
        assertTrue(out.toString(), out is Outcome.Stopped && out.reason.contains("left your draft"))
        assertEquals(draft, screen.current) // the dialog was dismissed, nothing else touched
        assertEquals(2, screen.performed.size)
        assertTrue(ledger.isEmpty())
    }
}
