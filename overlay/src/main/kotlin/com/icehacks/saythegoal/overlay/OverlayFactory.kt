package com.icehacks.saythegoal.overlay

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.icehacks.saythegoal.core.Bounds
import com.icehacks.saythegoal.core.CharacterMode
import com.icehacks.saythegoal.core.Overlay

/**
 * Called by the app's AccessibilityService. T04 replaces the fake with the real overlay.
 * Overlay windows must be TYPE_ACCESSIBILITY_OVERLAY + FLAG_NOT_TOUCHABLE so device taps pass
 * through; the device layer already excludes accessibility-overlay windows from snapshots.
 */
fun createOverlay(service: AccessibilityService): Overlay = FakeOverlay()

private const val TAG = "STG-Overlay"

class FakeOverlay : Overlay {
    override fun setMode(mode: CharacterMode) { Log.i(TAG, "mode=$mode") }
    override fun caption(text: String?) { Log.i(TAG, "caption=$text") }
    override suspend fun pointAt(target: Bounds, caption: String) { Log.i(TAG, "pointAt $target: $caption") }
    override suspend fun showResult(target: Bounds, ok: Boolean) { Log.i(TAG, "result $target ok=$ok") }
    override fun highlight(targets: List<Bounds>) { Log.i(TAG, "highlight $targets") }
}
