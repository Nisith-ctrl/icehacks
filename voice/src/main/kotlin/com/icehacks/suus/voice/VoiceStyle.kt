package com.icehacks.suus.voice

/**
 * How SuuS's voice sounds, set by the app from the user's choices ("Make SuuS yours"). The service
 * and the app's screens share one process, so a change applies to the next spoken line.
 */
object VoiceStyle {
    /** The lively default (a little quicker than Android's 1.0). */
    const val DEFAULT_RATE = 1.12f

    @Volatile
    var speechRate: Float = DEFAULT_RATE
}
