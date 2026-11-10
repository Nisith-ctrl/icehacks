package com.icehacks.suus.device

import com.icehacks.suus.core.Bounds
import com.icehacks.suus.device.Occlusion.Sibling
import org.junit.Assert.assertEquals
import org.junit.Test

class OcclusionTest {
    private val pane = Bounds(0, 136, 1080, 2211)

    @Test
    fun gmailInboxBehindAnOpenEmailIsHidden() {
        // n10 conversation_list_place_holder, then n71 conversation_view_container (same bounds, drawn later)
        val s = listOf(Sibling(pane, 0, true), Sibling(pane, 0, true))
        assertEquals(setOf(0), Occlusion.covered(s))
    }

    @Test
    fun drawingOrderBeatsChildOrder() {
        val s = listOf(Sibling(pane, 2, true), Sibling(pane, 1, true))
        assertEquals(setOf(1), Occlusion.covered(s))
    }

    @Test
    fun anEmptyOverlayHidesNothing() {
        val s = listOf(Sibling(pane, 0, true), Sibling(pane, 0, false))
        assertEquals(emptySet<Int>(), Occlusion.covered(s))
    }

    @Test
    fun aSmallerViewOnTopHidesNothing() {
        val fab = Bounds(660, 2020, 1020, 2170)
        val s = listOf(Sibling(pane, 0, true), Sibling(fab, 1, true))
        assertEquals(emptySet<Int>(), Occlusion.covered(s))
    }

    @Test
    fun sideBySideRowsHideNothing() {
        val s = listOf(Sibling(Bounds(0, 100, 1080, 300), 0, true), Sibling(Bounds(0, 300, 1080, 500), 0, true))
        assertEquals(emptySet<Int>(), Occlusion.covered(s))
    }
}
