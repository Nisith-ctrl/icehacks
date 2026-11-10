package com.icehacks.suus.device

import com.icehacks.suus.core.Bounds

/**
 * Some apps keep a whole screen alive under the one on top and still report it as visible to
 * accessibility. Gmail does this: the inbox list stays in the tree behind an open email, under the
 * same parent and with the same bounds. Without this filter the agent "sees" the inbox, decides its
 * tap did nothing, and taps again.
 *
 * Rule: a sibling is hidden when a sibling drawn later (higher drawing order, ties broken by child
 * order) fully covers it and has visible content of its own. Empty overlay layers don't count, so
 * transparent touch layers (Chrome has a few) never hide the page under them.
 */
object Occlusion {
    data class Sibling(val bounds: Bounds, val drawingOrder: Int, val hasContent: Boolean)

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
                if (drawnLater && b.hasContent && b.bounds.contains(a.bounds)) { hidden += i; break }
            }
        }
        return hidden
    }

    private fun Bounds.isEmpty() = right <= left || bottom <= top
    private fun Bounds.contains(o: Bounds) = left <= o.left && top <= o.top && right >= o.right && bottom >= o.bottom
}
