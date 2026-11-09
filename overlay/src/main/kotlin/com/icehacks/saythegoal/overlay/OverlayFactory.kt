package com.icehacks.saythegoal.overlay

import android.accessibilityservice.AccessibilityService
import android.content.pm.ApplicationInfo
import android.util.Log
import com.icehacks.saythegoal.core.Bounds
import com.icehacks.saythegoal.core.CharacterMode
import com.icehacks.saythegoal.core.Overlay

/**
 * Called by the app's AccessibilityService. Returns the real character overlay; falls back to
 * [FakeOverlay] if the overlay window can't be created. Debug builds also register DEBUG_OVERLAY.
 */
fun createOverlay(service: AccessibilityService): Overlay {
    val overlay: Overlay = runCatching { CharacterOverlay(service) }
        .onFailure { Log.w(TAG, "CharacterOverlay failed, using FakeOverlay", it) }
        .getOrElse { FakeOverlay() }
    val debuggable = service.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (debuggable) OverlayDebugReceiver(service, overlay).register()
    return overlay
}

private const val TAG = "STG-Overlay"

/** Logs calls only. Useful in tests and when the overlay can't be shown. */
class FakeOverlay : Overlay {
    override fun setMode(mode: CharacterMode) { Log.i(TAG, "mode=$mode") }
    override fun caption(text: String?) { Log.i(TAG, "caption=$text") }
    override suspend fun pointAt(target: Bounds, caption: String) { Log.i(TAG, "pointAt $target: $caption") }
    override suspend fun showResult(target: Bounds, ok: Boolean) { Log.i(TAG, "result $target ok=$ok") }
    override fun highlight(targets: List<Bounds>) { Log.i(TAG, "highlight $targets") }
}
