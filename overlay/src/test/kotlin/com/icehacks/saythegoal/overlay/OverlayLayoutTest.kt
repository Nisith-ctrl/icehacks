package com.icehacks.saythegoal.overlay

import com.icehacks.saythegoal.core.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayoutTest {
    private val screen = ScreenArea(1080, 2400, insetTop = 136, insetBottom = 66)
    private val size = 168f
    private val gap = 24f

    private fun circleHitsTarget(p: Placement, t: Bounds): Boolean {
        val r = size / 2
        val nx = p.cx.coerceIn(t.left.toFloat(), t.right.toFloat())
        val ny = p.cy.coerceIn(t.top.toFloat(), t.bottom.toFloat())
        val dx = p.cx - nx
        val dy = p.cy - ny
        return dx * dx + dy * dy < r * r
    }

    private fun insideSafeArea(p: Placement): Boolean {
        val r = size / 2
        return p.cx - r >= 0 && p.cx + r <= screen.width && p.cy - r >= screen.safeTop && p.cy + r <= screen.safeBottom
    }

    @Test
    fun submitButtonNearBottomGetsCharacterAbove() {
        val submit = Bounds(100, 2100, 980, 2250)
        val p = OverlayLayout.place(submit, screen, size, gap)
        assertEquals(Side.ABOVE, p.side)
        assertFalse(circleHitsTarget(p, submit))
        assertTrue(insideSafeArea(p))
    }

    @Test
    fun fieldNearTopGetsCharacterBelow() {
        val field = Bounds(40, 200, 1040, 320)
        val p = OverlayLayout.place(field, screen, size, gap)
        assertEquals(Side.BELOW, p.side)
        assertFalse(circleHitsTarget(p, field))
    }

    @Test
    fun tallNarrowTargetOnLeftGetsCharacterToTheRight() {
        val bar = Bounds(0, 140, 200, 2330)
        val p = OverlayLayout.place(bar, screen, size, gap)
        assertEquals(Side.RIGHT, p.side)
        assertFalse(circleHitsTarget(p, bar))
        assertTrue(insideSafeArea(p))
    }

    @Test
    fun fullScreenTargetFallsBackToCorner() {
        val all = Bounds(0, 0, 1080, 2400)
        val p = OverlayLayout.place(all, screen, size, gap)
        assertEquals(Side.CORNER, p.side)
        assertTrue(insideSafeArea(p))
    }

    @Test
    fun iconInCornerStaysOnScreen() {
        val icon = Bounds(940, 2200, 1080, 2330)
        val p = OverlayLayout.place(icon, screen, size, gap)
        assertFalse(circleHitsTarget(p, icon))
        assertTrue(insideSafeArea(p))
    }

    @Test
    fun captionGoesToTheOtherHalf() {
        assertTrue(OverlayLayout.captionAtTop(listOf(Bounds(0, 2000, 100, 2100)), screen))
        assertFalse(OverlayLayout.captionAtTop(listOf(Bounds(0, 200, 100, 300)), screen))
        assertFalse(OverlayLayout.captionAtTop(emptyList(), screen))
    }

    @Test
    fun captionAvoidsReadBackHighlights() {
        // Form fields high up and the Submit button low down: bottom band covers Submit, top band a field.
        val fields = listOf(Bounds(40, 200, 1040, 320), Bounds(40, 1000, 1040, 1120))
        val submit = Bounds(40, 2150, 1040, 2300)
        assertFalse(OverlayLayout.captionAtTop(fields, screen))           // nothing at the bottom -> bottom
        assertTrue(OverlayLayout.captionAtTop(fields + submit, screen))   // Submit is bigger -> top
    }
}
