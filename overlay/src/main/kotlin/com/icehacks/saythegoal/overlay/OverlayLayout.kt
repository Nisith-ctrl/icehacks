package com.icehacks.saythegoal.overlay

import com.icehacks.saythegoal.core.Bounds

/** Screen size and the system bar insets, in px. */
data class ScreenArea(val width: Int, val height: Int, val insetTop: Int, val insetBottom: Int) {
    val safeTop get() = insetTop
    val safeBottom get() = height - insetBottom
}

enum class Side { ABOVE, BELOW, LEFT, RIGHT, CORNER }

data class Placement(val side: Side, val cx: Float, val cy: Float)

/** Pure geometry for the character and caption. No Android types, so it is unit-tested. */
object OverlayLayout {

    /**
     * Centre of the character (a circle of [size] px) beside [target], on the side with the most
     * room, [gap] px away so it never covers the target. Falls back to the bottom-right corner of the
     * safe area when the target fills the screen.
     */
    fun place(target: Bounds, screen: ScreenArea, size: Float, gap: Float): Placement {
        val need = size + gap
        val room = mapOf(
            Side.ABOVE to (target.top - screen.safeTop).toFloat(),
            Side.BELOW to (screen.safeBottom - target.bottom).toFloat(),
            Side.LEFT to target.left.toFloat(),
            Side.RIGHT to (screen.width - target.right).toFloat(),
        )
        val half = size / 2
        val tcx = (target.left + target.right) / 2f
        val tcy = (target.top + target.bottom) / 2f
        val side = room.filter { it.value >= need }.maxByOrNull { it.value }?.key
            ?: return Placement(Side.CORNER, screen.width - gap - half, screen.safeBottom - gap - half)

        fun cx(x: Float) = x.coerceIn(half, screen.width - half)
        fun cy(y: Float) = y.coerceIn(screen.safeTop + half, screen.safeBottom - half)
        return when (side) {
            Side.ABOVE -> Placement(side, cx(tcx), target.top - gap - half)
            Side.BELOW -> Placement(side, cx(tcx), target.bottom + gap + half)
            Side.LEFT -> Placement(side, target.left - gap - half, cy(tcy))
            Side.RIGHT -> Placement(side, target.right + gap + half, cy(tcy))
            Side.CORNER -> error("unreachable")
        }
    }

    /**
     * Where the character waits when it isn't pointing at anything: the middle of the right edge,
     * a little above centre. Corners hold Send/Submit buttons, nav and app bars; mid-edge rarely does.
     */
    fun restingSpot(screen: ScreenArea, size: Float, gap: Float): Placement {
        val half = size / 2
        val cy = screen.safeTop + (screen.safeBottom - screen.safeTop) * 0.42f
        return Placement(Side.RIGHT, screen.width - gap - half, cy.coerceIn(screen.safeTop + half, screen.safeBottom - half))
    }

    /**
     * True if the caption goes at the top of the safe area. Picks the end of the screen whose
     * caption band (about [bandFraction] of the safe height) overlaps the targets least, so the
     * caption doesn't cover the control being used or the read-back highlights. Ties go to the top:
     * bottoms of screens hold Send, Submit and compose boxes; tops hold titles.
     */
    fun captionAtTop(targets: List<Bounds>, screen: ScreenArea, bandFraction: Float = 0.2f): Boolean {
        if (targets.isEmpty()) return true
        val band = ((screen.safeBottom - screen.safeTop) * bandFraction).toInt()
        fun overlap(top: Int, bottom: Int) = targets.sumOf { t ->
            val h = minOf(t.bottom, bottom) - maxOf(t.top, top)
            if (h > 0) h.toLong() * (t.right - t.left) else 0L
        }
        val atTop = overlap(screen.safeTop, screen.safeTop + band)
        val atBottom = overlap(screen.safeBottom - band, screen.safeBottom)
        return atBottom >= atTop
    }
}
