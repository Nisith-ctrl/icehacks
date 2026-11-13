package com.icehacks.suus.agent

import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.SuusJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Demo 2 as a Gmail draft (emulator, Nov 13): the draft to maya@example.com with "Made it home!",
 * Gmail's attach menu, and the draft after the yard photo was attached through Attach file.
 */
class GmailDraftFixtureTest {
    private fun fixture(name: String): ScreenSnapshot =
        SuusJson.decodeFromString(File("../fixtures/screens/$name.json").readText())
    private fun ScreenSnapshot.node(label: String) = nodes.first { (it.text ?: it.contentDescription) == label }

    @Test
    fun sendIsACommitAndThePaperclipIsNot() {
        val draft = fixture("gmail-draft-maya")
        assertTrue(AgentLoop.looksLikeCommit(draft, draft.node("Send")))
        assertFalse(AgentLoop.looksLikeCommit(draft, draft.node("Attach file")))
    }

    @Test
    fun insertPhotoIsRedirectedToAttachFile() {
        val menu = fixture("gmail-attach-menu")
        val insert = menu.node("Insert photo")
        assertEquals("Attach file", AgentLoop.attachInsteadOfInsert(menu, insert).text)
        val attach = menu.node("Attach file")
        assertEquals(attach, AgentLoop.attachInsteadOfInsert(menu, attach))
    }

    @Test
    fun theAttachedPhotoCanBeFoundAndUndone() {
        val before = fixture("gmail-draft-maya")
        val after = fixture("gmail-draft-attached")
        val ledger = DraftLedger()
        val edit = ledger.detectAttachment(before, after, "dog photo (yard)")
        assertNotNull(edit)
        assertEquals("Remove attachment IMG_0418.png", edit!!.marker)
        assertTrue(ledger.stillApplied(edit, after))
        assertFalse(ledger.stillApplied(edit, before))
        assertEquals(edit, ledger.pick("undo the photo"))
        // The draft's text is untouched by the attachment.
        assertTrue(DraftLedger.fieldTexts(after).any { it.startsWith("Made it home!") })
    }

    @Test
    fun readBacksSayThePhotoNotItsFileName() {
        assertEquals("Sending email to maya@example.com with the dog photo by the door",
            AgentLoop.spokenFileNames("Sending email to maya@example.com with the photo IMG_0422.png", "dog photo (door)"))
        assertEquals("with the dog photo in the yard", AgentLoop.spokenFileNames("with IMG_0418.png", "dog photo (yard)"))
        assertEquals("with the photo", AgentLoop.spokenFileNames("with IMG_0418.png", null))
        assertEquals("dog photo by the door", AgentLoop.spokenLabel("dog photo (by the door)"))
        assertEquals("no change here", AgentLoop.spokenFileNames("no change here", "dog photo (door)"))
    }

    @Test
    fun aSentDraftIsRecognisedByItsComposeScreenClosing() {
        val draft = fixture("gmail-draft-attached")
        assertFalse(AgentLoop.gmailComposeGone(draft))
        assertEquals("maya@example.com", AgentLoop.gmailRecipient(draft))
        val home = fixture("home-screen")
        assertTrue(AgentLoop.gmailComposeGone(home))
        val inbox = fixture("gmail-inbox")
        assertTrue(AgentLoop.gmailComposeGone(inbox))
    }

    @Test
    fun aLateAttachmentIsRecordedWithItsLabel() {
        val ledger = DraftLedger()
        val e = ledger.recordAttachment("dog photo (door)", "com.google.android.gm", "Remove attachment IMG_0422.png")
        assertEquals(e, ledger.pick("undo the photo"))
        assertEquals("dog photo (door)", e.label)
    }
}
