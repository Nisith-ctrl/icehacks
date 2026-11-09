package com.icehacks.saythegoal.voice

import com.icehacks.saythegoal.core.ApprovalOutcome
import com.icehacks.saythegoal.core.Bounds
import com.icehacks.saythegoal.core.Fingerprinter
import com.icehacks.saythegoal.core.ScreenSnapshot
import com.icehacks.saythegoal.core.UiNode
import com.icehacks.saythegoal.core.UserCommand
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    @Test
    fun classifiesUtterances() {
        assertEquals(UserCommand.Stop, CommandParser.classify("Stop!"))
        assertEquals(UserCommand.Stop, CommandParser.classify("cancel"))
        assertEquals(UserCommand.Stop, CommandParser.classify("never mind"))
        assertTrue(CommandParser.classify("Undo the photo.") is UserCommand.Undo)
        assertTrue(CommandParser.classify("What do you remember?") is UserCommand.Memory)
        assertTrue(CommandParser.classify("Forget the event signup preference") is UserCommand.Memory)
        assertEquals(
            UserCommand.Goal("Take over. Attach yesterday's dog photo.", takeOver = true),
            CommandParser.classify("Take over. Attach yesterday's dog photo."),
        )
        assertEquals(
            UserCommand.Goal("Find the library workshop invite and register me", takeOver = false),
            CommandParser.classify("Find the library workshop invite and register me"),
        )
        // "stop" inside a longer goal is not a Stop
        assertTrue(CommandParser.classify("find the bus stop schedule email and open the link") is UserCommand.Goal)
    }

    @Test
    fun wakePhrase() {
        assertEquals("open Gmail", CommandParser.afterWake("Hey Goal, open Gmail"))
        assertEquals("", CommandParser.afterWake("hey goal"))
        assertEquals("register me", CommandParser.afterWake("Say the goal register me"))
        assertNull(CommandParser.afterWake("open Gmail"))
    }
}

class ChoiceMatcherTest {
    private val methods = listOf("Email", "Text message")
    private val photos = listOf("The one in the yard", "The one by the door")

    @Test
    fun matchesChoices() {
        assertEquals("Email", ChoiceMatcher.match("email please", methods))
        assertEquals("Email", ChoiceMatcher.match("E-mail.", methods))
        assertEquals("Text message", ChoiceMatcher.match("text", methods))
        assertEquals("Text message", ChoiceMatcher.match("the second one", methods))
        assertEquals("Email", ChoiceMatcher.match("the first one", methods))
        assertEquals("The one by the door", ChoiceMatcher.match("use the one by the door", photos))
        assertEquals("The one in the yard", ChoiceMatcher.match("yard", photos))
        assertEquals("Text message", ChoiceMatcher.match("the last one", methods))
        assertNull(ChoiceMatcher.match("pizza", methods))
    }

    @Test
    fun resolveKeepsExtraInstructions() {
        assertEquals("Email", ChoiceMatcher.resolve("Email please", methods))
        assertEquals("Text message", ChoiceMatcher.resolve("the second one", methods))
        val withRemember = "Email. Remember that for event signups."
        assertEquals(withRemember, ChoiceMatcher.resolve(withRemember, methods))
        assertEquals("pizza", ChoiceMatcher.resolve("pizza", methods))
    }
}

class VoiceRouterTest {
    private var clock = 0L
    private val router = VoiceRouter(now = { clock }, sessionMs = 90_000)

    @Test
    fun wakeThenSessionThenTimeout() = runTest {
        assertEquals(VoiceRouter.Routed.IGNORED, router.onTranscript("open gmail"))
        val first = async { router.commands.first() }
        yield()
        assertEquals(VoiceRouter.Routed.COMMAND, router.onTranscript("Hey Goal, open Gmail"))
        assertEquals(UserCommand.Goal("open Gmail", takeOver = false), first.await())
        clock += 30_000
        val stop = async { router.commands.first() }
        yield()
        assertEquals(VoiceRouter.Routed.COMMAND, router.onTranscript("stop"))
        assertEquals(UserCommand.Stop, stop.await())
        clock += 200_000
        assertEquals(VoiceRouter.Routed.IGNORED, router.onTranscript("stop"))
        assertEquals(VoiceRouter.Routed.WOKE, router.onTranscript("hey goal"))
    }

    @Test
    fun pendingQuestionTakesTheAnswer() = runTest {
        val expected = router.expectAnswer()
        assertEquals(VoiceRouter.Routed.ANSWER, router.onTranscript("Email. Remember that for event signups."))
        assertEquals("Email. Remember that for event signups.", router.awaitAnswer(expected, 1_000))
    }
}

class SpokenApprovalGateTest {
    private val snap = ScreenSnapshot(
        "com.android.chrome", "Form", 0L,
        listOf(UiNode("n0", null, "android.widget.Button", "Submit", null, null, null, null, Bounds(0, 0, 1, 1),
            true, false, false, false, false, false, false, true)),
    )
    private val fp = object : Fingerprinter { override fun of(snapshot: ScreenSnapshot) = "fp-${snapshot.nodes.size}" }

    private suspend fun gate(vararg answers: String?) =
        FakeVoice(answers.toList()).let { it to SpokenApprovalGate(it, fp, now = { 42L }).request("Library workshop, Thursday 10 AM.", snap, "Submit") }

    @Test
    fun approveCancelSilenceAndNonCommitWords() = runTest {
        val (_, ok) = gate("Submit.")
        assertEquals(ApprovalOutcome.APPROVED, ok.outcome)
        assertEquals("fp-1", ok.approval!!.fingerprint)
        assertEquals(42L, ok.approval!!.grantedAtMs)

        assertEquals(ApprovalOutcome.CANCELLED, gate("cancel").second.outcome)
        assertEquals(ApprovalOutcome.NO_ANSWER, gate(null).second.outcome)

        val (voice, afterYes) = gate("yes", "sure", "Submit it")
        assertEquals(ApprovalOutcome.APPROVED, afterYes.outcome)
        assertTrue(voice.spoken[1].startsWith("Say Submit"))

        assertEquals(ApprovalOutcome.NO_ANSWER, gate("don't submit", null).second.outcome)
        assertNull(gate("ok", "yes", "yes", "yes").second.approval)
    }
}
