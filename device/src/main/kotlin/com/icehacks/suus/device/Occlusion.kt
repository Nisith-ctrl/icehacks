package com.icehacks.suus.device

import com.icehacks.suus.core.Bounds

/**
 * Some apps keep a whole screen alive under the one on top and still report it as visible to
 * accessibility. Gmail does this: the inbox list stays in the tree behind an open email, under the
 * same parent and with the same bounds. Without this filter the agent "sees" the inbox, decides its
 * tap did nothing, and taps again.
 *
 * Rule: a sibling is hidden when a sibling drawn later (higher drawing order, ties broken by child
 * order) fully covers it and its visible content spreads over at least half of it. Layers that are
 * empty or hold only a small control don't count: Chrome has transparent touch layers, and Google
 * Calendar draws a full-screen layer over its event list that only holds the "+" button.
 */
object Occlusion {
    /** [content]: the box around the sibling's visible text/descriptions, or null if it shows none. */
    data class Sibling(val bounds: Bounds, val drawingOrder: Int, val content: Bounds?)

    /** Indexes (into [siblings], in child order) of the siblings that are covered. */
    fun covered(siblings: List<Sibling>): Set<Int> {
        if (siblings.size < 2) return emptySet()
        val hidden = HashSet<Int>()
        for (i in siblings.indices) {
            val a = siblings[i]
            if (a.bounds.isEmpty()) continue
            for (j in siblings.indices) {
                if (j == i) continue
                val b = siblings[j]
                val drawnLater = b.drawingOrder > a.drawingOrder || (b.drawingOrder == a.drawingOrder && j > i)
                if (drawnLater && b.bounds.contains(a.bounds) && coversMostOf(b.content, a.bounds)) { hidden += i; break }
            }
        }
        return hidden
    }

    private fun coversMostOf(content: Bounds?, target: Bounds): Boolean {
        content ?: return false
        val w = minOf(content.right, target.right) - maxOf(content.left, target.left)
        val h = minOf(content.bottom, target.bottom) - maxOf(content.top, target.top)
        if (w <= 0 || h <= 0) return false
        return 2L * w * h >= target.area()
    }

    private fun Bounds.area() = (right - left).toLong() * (bottom - top)
    private fun Bounds.isEmpty() = right <= left || bottom <= top
    private fun Bounds.contains(o: Bounds) = left <= o.left && top <= o.top && right >= o.right && bottom >= o.bottom
}
