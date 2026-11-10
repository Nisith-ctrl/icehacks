package com.icehacks.suus.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.icehacks.suus.core.Bounds
import com.icehacks.suus.core.CharacterMode
import com.icehacks.suus.core.Overlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The on-screen character. One full-screen TYPE_ACCESSIBILITY_OVERLAY window that is not touchable
 * or focusable, so taps (ours and the user's) and the keyboard pass straight through. The device
 * layer only reads TYPE_APPLICATION windows and ignores events from our package, so this window is
 * never in snapshots or fingerprints and its animations don't delay "screen settled".
 */
class CharacterOverlay(private val service: AccessibilityService) : Overlay {

    private val main = Handler(Looper.getMainLooper())
    private val wm = service.getSystemService(WindowManager::class.java)
    private val stage = StageView(service)
    private var attached = false
    private var move: ValueAnimator? = null
    private var pulse: ValueAnimator? = null

    // ---------- Overlay ----------

    override fun setMode(mode: CharacterMode) = ui {
        ensureAttached()
        val wasHidden = stage.mode == CharacterMode.HIDDEN
        stage.mode = mode
        if (mode != CharacterMode.ACTING && mode != CharacterMode.WAITING_APPROVAL) stage.focus = null
        if (stage.charX.isNaN() || (wasHidden && mode != CharacterMode.HIDDEN)) park()
        armAutoHide()
        if (animates(mode)) startPulse() else stopPulse()
        placeCaption()
        stage.invalidate()
    }

    override fun caption(text: String?) = ui {
        ensureAttached()
        stage.caption = text
        placeCaption()
        armAutoHide()
        stage.invalidate()
    }

    override fun highlight(targets: List<Bounds>) = ui {
        ensureAttached()
        stage.highlights = targets
        placeCaption()
        armAutoHide()
        stage.invalidate()
    }

    override suspend fun pointAt(target: Bounds, caption: String): Unit = withContext(Dispatchers.Main) {
        ensureAttached()
        main.removeCallbacks(autoHide)
        refreshScreen()
        if (stage.mode == CharacterMode.HIDDEN || stage.mode == CharacterMode.IDLE) setModeNow(CharacterMode.ACTING)
        val p = OverlayLayout.place(target, stage.screen, stage.characterSize, stage.gap)
        val fromX = if (stage.charX.isNaN()) p.cx else stage.charX
        val fromY = if (stage.charY.isNaN()) p.cy else stage.charY
        stage.focus = target
        stage.focusAlpha = 0f
        stage.caption = caption
        val r = (stage.characterSize / 2).toInt()
        val dest = Bounds(p.cx.toInt() - r, p.cy.toInt() - r, p.cx.toInt() + r, p.cy.toInt() + r)
        stage.captionTop = OverlayLayout.captionAtTop(listOf(target, dest) + stage.highlights, stage.screen)

        move?.cancel()
        val walks = kotlin.math.abs(p.cx - fromX) + kotlin.math.abs(p.cy - fromY) > stage.gap
        if (p.cx != fromX) stage.facingLeft = p.cx < fromX
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MOVE_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                stage.charX = fromX + (p.cx - fromX) * t
                stage.charY = fromY + (p.cy - fromY) * t
                stage.focusAlpha = t
                // Step through the walking frames while it moves (one frame per WALK_FRAME_MS).
                if (walks) stage.frame = ((it.currentPlayTime / WALK_FRAME_MS) % StageView.FRAME_COUNT).toInt()
                stage.invalidate()
            }
        }
        move = anim
        withTimeoutOrNull(MAX_POINT_MS) { anim.await() }
        anim.end()
        stage.frame = 0 // standing pose once it arrives
        stage.charX = p.cx
        stage.charY = p.cy
        stage.focusAlpha = 1f
        stage.invalidate()
        Log.i(TAG, "pointAt $target side=${p.side}: $caption")
    }

    override suspend fun showResult(target: Bounds, ok: Boolean): Unit = withContext(Dispatchers.Main) {
        ensureAttached()
        stage.result = target to ok
        stage.invalidate()
        try {
            delay(RESULT_MS)
        } finally {
            stage.result = null
            stage.focus = null // the screen has changed; a stale ring would point at the wrong thing
            stage.invalidate()
        }
    }

    // ---------- internals (main thread) ----------

    /** Caption goes to the end of the screen that covers the focused target and highlights least. */
    private fun placeCaption() {
        val avoid = listOfNotNull(stage.focus) + stage.highlights + listOfNotNull(characterBounds())
        stage.captionTop = OverlayLayout.captionAtTop(avoid, stage.screen)
    }

    private fun characterBounds(): Bounds? {
        if (stage.charX.isNaN() || stage.mode == CharacterMode.HIDDEN) return null
        val r = stage.characterSize / 2
        return Bounds((stage.charX - r).toInt(), (stage.charY - r).toInt(), (stage.charX + r).toInt(), (stage.charY + r).toInt())
    }

    private fun setModeNow(mode: CharacterMode) {
        stage.mode = mode
        if (animates(mode)) startPulse() else stopPulse()
    }

    private fun ensureAttached() {
        if (attached) return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = WINDOW_TITLE
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        wm.addView(stage, lp)
        attached = true
        refreshScreen()
    }

    private fun refreshScreen() {
        val m = wm.currentWindowMetrics
        val insets = m.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        stage.screen = ScreenArea(m.bounds.width(), m.bounds.height(), insets.top, insets.bottom)
    }

    /** Resting spot: bottom-right of the safe area, out of the way of most content. */
    private fun park() {
        refreshScreen()
        val p = OverlayLayout.restingSpot(stage.screen, stage.characterSize, stage.gap)
        stage.charX = p.cx
        stage.charY = p.cy
    }

    /** After the agent finishes (IDLE) or stops (PROBLEM), fade out so nothing stays on top of the app. */
    private val autoHide = Runnable {
        stage.mode = CharacterMode.HIDDEN
        stage.caption = null
        stage.highlights = emptyList()
        stage.focus = null
        stopPulse()
        stage.invalidate()
    }

    private fun armAutoHide() {
        main.removeCallbacks(autoHide)
        if ((stage.mode == CharacterMode.IDLE || stage.mode == CharacterMode.PROBLEM) && stage.highlights.isEmpty()) {
            main.postDelayed(autoHide, AUTO_HIDE_MS)
        }
    }

    /** Modes whose face moves: the sound bars while listening, the dots while thinking. */
    private fun animates(mode: CharacterMode) = mode == CharacterMode.LISTENING || mode == CharacterMode.THINKING

    private fun startPulse() {
        if (pulse != null) return
        pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1_100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { stage.pulse = it.animatedValue as Float; stage.invalidate() }
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        stage.pulse = 0f
    }

    private fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private suspend fun ValueAnimator.await() = suspendCancellableCoroutine { cont ->
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { if (cont.isActive) cont.resume(Unit) }
        })
        cont.invokeOnCancellation { main.post { cancel() } }
        start()
    }

    companion object {
        const val TAG = "SUUS-Overlay"
        const val WINDOW_TITLE = "SuusOverlay"
        private const val MOVE_MS = 420L
        private const val WALK_FRAME_MS = 70L
        private const val MAX_POINT_MS = 650L
        private const val RESULT_MS = 380L
        private const val AUTO_HIDE_MS = 6_000L
    }
}
