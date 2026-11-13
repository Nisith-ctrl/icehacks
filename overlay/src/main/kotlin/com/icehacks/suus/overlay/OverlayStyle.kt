package com.icehacks.suus.overlay

/**
 * How the on-screen robot looks, set by the app from the user's choices ("Make SuuS yours"). Read
 * every frame, so a new size shows the next time the robot moves or pops up.
 */
object OverlayStyle {
    /** 1 = the standard 88 dp robot. The app offers 0.8, 1 and 1.3. */
    @Volatile
    var robotScale: Float = 1f
}
