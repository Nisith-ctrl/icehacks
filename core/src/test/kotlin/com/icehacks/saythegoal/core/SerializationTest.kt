package com.icehacks.saythegoal.core

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class SerializationTest {
    private val node = UiNode(
        id = "n0", parentId = null, className = "android.widget.EditText", text = "Ada",
        contentDescription = null, hint = "Name", viewId = "name", labeledById = null,
        bounds = Bounds(0, 0, 100, 50), clickable = true, editable = true, scrollable = false,
        checkable = false, checked = false, selected = false, focused = true, enabled = true,
    )

    @Test
    fun snapshotRoundTrips() {
        val snap = ScreenSnapshot("com.android.chrome", "Form", 123L, listOf(node))
        val json = StgJson.encodeToString(snap)
        assertEquals(snap, StgJson.decodeFromString<ScreenSnapshot>(json))
    }

    @Test
    fun actionsRoundTripWithShortDiscriminator() {
        val actions = listOf(
            DeviceAction.Tap("n3", isCommit = true),
            DeviceAction.EnterText("n4", "hello"),
            DeviceAction.Scroll(null, ScrollDirection.DOWN),
            DeviceAction.Back,
            DeviceAction.LaunchApp("com.google.android.gm"),
        )
        for (a in actions) {
            val json = StgJson.encodeToString<DeviceAction>(a)
            assertEquals(a, StgJson.decodeFromString<DeviceAction>(json))
        }
        assertEquals(DeviceAction.Tap("n1"), StgJson.decodeFromString<DeviceAction>("""{"type":"Tap","nodeId":"n1"}"""))
        assertEquals(DeviceAction.Back, StgJson.decodeFromString<DeviceAction>("""{"type":"Back"}"""))
    }

    @Test
    fun resultRoundTrips() {
        val r: ActionResult = ActionResult.NotAllowed("no approval")
        assertEquals(r, StgJson.decodeFromString<ActionResult>(StgJson.encodeToString(r)))
    }
}
