package com.icehacks.suus.agent

import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.SuusJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Real Google Messages draft (Maya, "Made it home!") recorded on the API 35 emulator. */
class MessagesFixtureTest {
    private val draft: ScreenSnapshot =
        SuusJson.decodeFromString(File("../fixtures/screens/messages-draft.json").readText())
    private fun node(id: String) = draft.nodes.first { it.id == id }

    @Test
    fun sendIsACommitWhicheverNodeTheModelPicks() {
        // n24 = clickable "Compose:Draft:Send" with no label of its own; n25 = its child "Send SMS";
        // n26 = child TextView "SMS"; n27 = child Button with no label.
        for (id in listOf("n24", "n25", "n26", "n27")) {
            assertTrue("$id should count as Send", AgentLoop.looksLikeCommit(draft, node(id)))
        }
        assertEquals("n24", AgentLoop.clickTarget(draft, node("n25")).id)
    }

    @Test
    fun otherComposeControlsAreNotCommits() {
        for (id in listOf("n17", "n18", "n21", "n22", "n29", "n30", "n35", "n36", "n37")) {
            assertFalse("$id should not count as Send", AgentLoop.looksLikeCommit(draft, node(id)))
        }
    }

    @Test
    fun labelledChildrenOfClickableControlsAreShownAsTappable() {
        val text = ScreenText.render(draft)
        assertTrue(text, text.contains("n18 View desc=\"Show more options\" [tap]"))
        assertTrue(text, text.contains("n25 View desc=\"Send SMS\" [tap]"))
        assertTrue(text, text.contains("n35 TextView \"Maya\" [tap]"))
    }

    @Test
    fun plainTapOnSendIsRefusedInTheLoop() = runTest {
        val screen = FakeScreen(draft)
        val planner = FakePlanner(listOf(PlannerStep("tap", tool("node_id" to "n24", "caption" to "Sending"))))
        loop(screen, planner).run("send it", true)
        assertTrue(screen.performed.isEmpty())
        assertTrue(planner.turns[1].feedback!!.contains("submit tool"))
    }
}
