package com.icehacks.suus.device

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EventSettlerTest {
    @Test
    fun returnsAfterQuietPeriod() = runTest {
        val s = EventSettler(now = { testScheduler.currentTime })
        val start = testScheduler.currentTime
        s.awaitSettled()
        assertTrue(testScheduler.currentTime - start in 150..400)
    }

    @Test
    fun givesUpAfterMaxWaitWhenEventsKeepComing() = runTest {
        val s = EventSettler(now = { testScheduler.currentTime })
        val noisy = launch { while (true) { s.onEvent(); kotlinx.coroutines.delay(100) } }
        val start = testScheduler.currentTime
        s.awaitSettled()
        noisy.cancel()
        assertTrue(testScheduler.currentTime - start in 5_000..5_100)
    }
}
