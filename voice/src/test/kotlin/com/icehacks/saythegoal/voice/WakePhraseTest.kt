package com.icehacks.saythegoal.voice

import com.icehacks.saythegoal.core.UserCommand
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Wake word "Suus" (decision 2026-11-10-wake-word-suus) and how the recognizer may spell it. */
class WakePhraseTest {

    @Test
    fun spellingsOfSuusWake() {
        for (heard in listOf(
            "Suus open Gmail", "suus, open Gmail", "Sous open Gmail", "Soos open Gmail", "Sues open Gmail",
            "Sue's open Gmail", "Seuss open Gmail", "Zeus open Gmail", "Zoos open Gmail", "Sus open Gmail",
            "Suss open Gmail", "Suse open Gmail", "Suze open Gmail", "Souse open Gmail",
            "Hey Suus open Gmail", "hey Seuss open Gmail", "OK sous open Gmail", "hi Zeus, open Gmail",
            // the recognizer sometimes splits it in two
            "sue us open Gmail", "su us open Gmail", "sue s open Gmail", "hey sue us open Gmail",
        )) {
            assertEquals(heard, "open Gmail", CommandParser.afterWake(heard))
        }
    }

    @Test
    fun bareWakeReturnsEmpty() {
        assertEquals("", CommandParser.afterWake("Suus"))
        assertEquals("", CommandParser.afterWake("Seuss."))
        assertEquals("", CommandParser.afterWake("hey sous"))
        assertEquals("", CommandParser.afterWake("sue us"))
    }

    @Test
    fun ordinarySpeechDoesNotWake() {
        for (said in listOf(
            "", "Sue", "sue me", "so what", "soup is ready", "sauce please", "suits", "sushi tonight",
            "open Gmail", "hey there", "hey", "OK Google", "use the one by the door", "this is it",
            "Hey Goal open Gmail", "us too", "news at six",
        )) {
            assertNull(said, CommandParser.afterWake(said))
        }
    }

    @Test
    fun wakeThenCommandIsClassified() {
        assertEquals(UserCommand.Stop, CommandParser.classify(CommandParser.afterWake("Suus, stop")!!))
        assertEquals(
            UserCommand.Goal("take over. Attach yesterday's dog photo.", takeOver = true),
            CommandParser.classify(CommandParser.afterWake("Sous take over. Attach yesterday's dog photo.")!!),
        )
    }

    @Test
    fun routerSignalsWakesForTheSetupScreen() = runTest {
        val router = VoiceRouter(now = { 0L })
        val bare = async { router.wakes.first() }
        yield()
        assertEquals(VoiceRouter.Routed.WOKE, router.onTranscript("Seuss"))
        bare.await()
        val withCommand = async { router.wakes.first() }
        yield()
        assertEquals(VoiceRouter.Routed.COMMAND, router.onTranscript("Zeus what do you remember"))
        withCommand.await()
    }
}
