package com.icehacks.saythegoal.device

import android.os.SystemClock
import kotlinx.coroutines.delay

/**
 * Settles on accessibility events: the service calls [onEvent] for every content/state/scroll
 * event from other apps. [awaitSettled] waits at least [minWaitMs], then until no event has
 * arrived for [quietMs], giving up after [maxWaitMs].
 */
class EventSettler(
    private val now: () -> Long = { SystemClock.uptimeMillis() },
    private val minWaitMs: Long = 150,
    private val quietMs: Long = 300,
    private val maxWaitMs: Long = 5_000,
) : Settler {
    @Volatile private var lastEventAt: Long = 0

    fun onEvent() { lastEventAt = now() }

    override suspend fun awaitSettled() {
        val start = now()
        delay(minWaitMs)
        while (true) {
            val t = now()
            if (t - lastEventAt >= quietMs || t - start >= maxWaitMs) return
            delay(50)
        }
    }
}
