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
        val s = listOf(Sibling(pane, 0, pane), Sibling(pane, 0, Bounds(0, 146, 1080, 2151)))
        assertEquals(setOf(0), Occlusion.covered(s))
    }

    @Test
    fun drawingOrderBeatsChildOrder() {
        val s = listOf(Sibling(pane, 2, pane), Sibling(pane, 1, pane))
        assertEquals(setOf(1), Occlusion.covered(s))
    }

    @Test
    fun anEmptyOverlayHidesNothing() {
        val s = listOf(Sibling(pane, 0, pane), Sibling(pane, 0, null))
        assertEquals(emptySet<Int>(), Occlusion.covered(s))
    }

    @Test
    fun aSmallerViewOnTopHidesNothing() {
        val fab = Bounds(660, 2020, 1020, 2170)
        val s = listOf(Sibling(pane, 0, pane), Sibling(fab, 1, fab))
        assertEquals(emptySet<Int>(), Occlusion.covered(s))
    }

    @Test
    fun sideBySideRowsHideNothing() {
        val s = listOf(Sibling(Bounds(0, 100, 1080, 300), 0, Bounds(0, 100, 1080, 300)), Sibling(Bounds(0, 300, 1080, 500), 0, Bounds(0, 300, 1080, 500)))
        assertEquals(emptySet<Int>(), Occlusion.covered(s))
    }

    @Test
    fun aFullScreenLayerHoldingOnlyAButtonHidesNothing() {
        // Google Calendar: main_content_without_overlays, then coordinator_layout (full screen, only the "+" FAB)
        val screen = Bounds(0, 0, 1080, 2400)
        val s = listOf(Sibling(screen, 0, Bounds(0, 146, 1080, 2400)), Sibling(screen, 0, Bounds(891, 2148, 1038, 2295)))
        assertEquals(emptySet<Int>(), Occlusion.covered(s))
    }
}
