package com.icehacks.saythegoal.voice

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePhraseTest {

    @Test
    fun guruRealSpeechTranscriptsNowWake() {
        // From the dead end note 2026-11-09-hey-goal-wake-phrase-misheard (real speech on the emulator).
        assertEquals("what do you remember", CommandParser.afterWake("Google what do you remember"))
        assertEquals("what do you remember", CommandParser.afterWake("hey girl what do you remember"))
    }

    @Test
    fun nearMissesOfHeyGoal() {
        for (heard in listOf(
            "Hey Goal, open Gmail", "hey gold open Gmail", "hey gol open Gmail", "hey go open Gmail",
            "a goal open Gmail", "hey goal's open Gmail", "hey goals open Gmail", "hi goal open Gmail",
            "OK goal open Gmail", "okay goal open Gmail", "hay goal open Gmail", "hey Google open Gmail",
            "Say the Goal open Gmail", "say the gold open Gmail", "hey say the goal open Gmail",
        )) {
            assertEquals(heard, "open Gmail", CommandParser.afterWake(heard))
        }
    }

    @Test
    fun bareWakeReturnsEmpty() {
        assertEquals("", CommandParser.afterWake("hey goal"))
        assertEquals("", CommandParser.afterWake("Hey girl."))
        assertEquals("", CommandParser.afterWake("say the goal"))
    }

    @Test
    fun ordinarySpeechDoesNotWake() {
        for (said in listOf(
            "OK Google", "Google", "hey Google", "open Gmail", "the goal is to finish today",
            "go to the store", "hey there how are you", "girl what do you remember", "what's the goal",
            "hey Gmail", "say the word", "a game of chess", "",
        )) {
            assertNull(said, CommandParser.afterWake(said))
        }
    }

    @Test
    fun routerSignalsWakesForTheSetupScreen() = runTest {
        val router = VoiceRouter(now = { 0L })
        val bare = async { router.wakes.first() }
        yield()
        assertEquals(VoiceRouter.Routed.WOKE, router.onTranscript("hey girl"))
        bare.await()
        val withCommand = async { router.wakes.first() }
        yield()
        assertEquals(VoiceRouter.Routed.COMMAND, router.onTranscript("Google what do you remember"))
        withCommand.await()
    }
}
