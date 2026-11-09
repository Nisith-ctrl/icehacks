package com.icehacks.saythegoal.device

import com.icehacks.saythegoal.core.Bounds
import com.icehacks.saythegoal.core.NodeSignature
import com.icehacks.saythegoal.core.ScreenSnapshot
import com.icehacks.saythegoal.core.signature
import kotlin.math.abs

/** Re-finds a node from an older snapshot in a fresh one, by signature. */
object NodeMatcher {
    /** Max distance (px) the centre may move and still count as the same node (e.g. layout jitter). */
    const val TOLERANCE_PX = 24

    /** Index into [fresh].nodes, or null if the node is gone or ambiguous. */
    fun find(sig: NodeSignature, fresh: ScreenSnapshot): Int? {
        val exact = fresh.nodes.indexOfFirst { it.signature() == sig }
        if (exact >= 0) return exact

        // Same identity fields, bounds moved a little: accept the single closest one.
        val candidates = fresh.nodes.withIndex().filter { (_, n) ->
            n.className == sig.className && n.viewId == sig.viewId && n.text == sig.text &&
                n.contentDescription == sig.contentDescription && n.hint == sig.hint &&
                centreDistance(n.bounds, sig.bounds) <= TOLERANCE_PX
        }
        return candidates.minByOrNull { centreDistance(it.value.bounds, sig.bounds) }?.index
    }

    private fun centreDistance(a: Bounds, b: Bounds): Int {
        val dx = abs((a.left + a.right) / 2 - (b.left + b.right) / 2)
        val dy = abs((a.top + a.bottom) / 2 - (b.top + b.bottom) / 2)
        return maxOf(dx, dy)
    }
}
