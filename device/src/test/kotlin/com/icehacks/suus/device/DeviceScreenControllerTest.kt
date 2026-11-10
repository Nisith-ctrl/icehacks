package com.icehacks.suus.device

import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.Approval
import com.icehacks.suus.core.Bounds
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.ScrollDirection
import com.icehacks.suus.core.UiNode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

fun node(
    id: String,
    text: String? = null,
    bounds: Bounds = Bounds(0, 0, 100, 100),
    editable: Boolean = false,
    className: String = if (editable) "android.widget.EditText" else "android.widget.Button",
    viewId: String? = null,
    checked: Boolean = false,
) = UiNode(
    id = id, parentId = null, className = className, text = text, contentDescription = null, hint = null,
    viewId = viewId, labeledById = null, bounds = bounds, clickable = !editable, editable = editable,
    scrollable = false, checkable = false, checked = checked, selected = false, focused = false, enabled = true,
)

fun snap(vararg nodes: UiNode, pkg: String = "com.android.chrome") =
    ScreenSnapshot(pkg, "t", 0L, nodes.toList())

/** Screens are a mutable list of nodes; actions mutate them like a real app would. */
class FakeBackend(var screen: ScreenSnapshot) : ScreenBackend<Int> {
    val log = mutableListOf<String>()
    var onClick: (Int) -> Unit = {}
    var applyText = true

    override suspend fun capture() = Captured(screen, screen.nodes.indices.toList())
    override suspend fun click(handle: Int, bounds: Bounds): Boolean {
        log += "click ${screen.nodes[handle].text}"
        onClick(handle)
        return true
    }
    override suspend fun setText(handle: Int, text: String): Boolean {
        log += "setText $text"
        if (applyText) {
            screen = screen.copy(nodes = screen.nodes.mapIndexed { i, n -> if (i == handle) n.copy(text = text) else n })
        }
        return true
    }
    override suspend fun scroll(handle: Int?, direction: ScrollDirection) = true.also { log += "scroll $direction" }
    override suspend fun back() = true.also { log += "back" }
    /** Set to make the first (non-fresh) launch show this package instead, like a Chrome tab in Gmail's task. */
    var taskShows: String? = null
    override suspend fun launch(packageName: String, fresh: Boolean) = true.also {
        log += if (fresh) "launch fresh $packageName" else "launch $packageName"
        screen = screen.copy(packageName = if (!fresh && taskShows != null) taskShows!! else packageName)
    }
}

class DeviceScreenControllerTest {
    private val fp = Sha256Fingerprinter()
    private fun controller(b: FakeBackend) = DeviceScreenController(b, fp, settler = {}, log = {})

    private val submit = node("n1", "Submit", Bounds(0, 500, 200, 600))
    private val name = node("n0", "", Bounds(0, 0, 400, 80), editable = true, viewId = "name")

    @Test
    fun commitTapRefusedWithoutApproval() = runTest {
        val b = FakeBackend(snap(name, submit))
        val r = controller(b).perform(DeviceAction.Tap("n1", isCommit = true), b.screen)
        assertTrue(r is ActionResult.NotAllowed)
        assertTrue(b.log.isEmpty())
    }

    @Test
    fun commitTapRefusedWhenScreenChangedAfterApproval() = runTest {
        val b = FakeBackend(snap(name, submit))
        val approval = Approval(fp.of(b.screen), 1L)
        b.screen = snap(name.copy(text = "edited"), submit) // draft changed after approval
        val r = controller(b).perform(DeviceAction.Tap("n1", isCommit = true), b.screen, approval)
        assertTrue(r is ActionResult.NotAllowed)
        assertTrue(b.log.isEmpty())
    }

    @Test
    fun commitTapAllowedWithMatchingApproval() = runTest {
        val b = FakeBackend(snap(name, submit))
        b.onClick = { b.screen = snap(node("n0", "Thanks, you're registered")) }
        val approval = Approval(fp.of(b.screen), 1L)
        val r = controller(b).perform(DeviceAction.Tap("n1", isCommit = true), b.screen, approval)
        assertTrue(r is ActionResult.Done && r.screenChanged)
        assertEquals(listOf("click Submit"), b.log)
    }

    @Test
    fun tapOnVanishedTargetIsTargetNotFound() = runTest {
        val old = snap(name, submit)
        val b = FakeBackend(snap(name))
        val r = controller(b).perform(DeviceAction.Tap("n1"), old)
        assertTrue(r is ActionResult.TargetNotFound)
    }

    @Test
    fun tapFindsTargetThatMovedSlightly() = runTest {
        val old = snap(name, submit)
        val b = FakeBackend(snap(name, submit.copy(id = "n9", bounds = Bounds(0, 510, 200, 610))))
        b.onClick = { b.screen = snap(node("x", "next")) }
        val r = controller(b).perform(DeviceAction.Tap("n1"), old)
        assertTrue(r is ActionResult.Done)
        assertEquals(listOf("click Submit"), b.log)
    }

    @Test
    fun notAllowlistedAppIsRefused() = runTest {
        val b = FakeBackend(snap(submit, pkg = "com.evil.bank"))
        assertTrue(controller(b).perform(DeviceAction.Tap("n1"), b.screen) is ActionResult.NotAllowed)
        assertTrue(controller(b).perform(DeviceAction.LaunchApp("com.evil.bank"), b.screen) is ActionResult.NotAllowed)
    }

    @Test
    fun launchAllowlistedApp() = runTest {
        val b = FakeBackend(snap(submit, pkg = "com.google.android.apps.nexuslauncher"))
        val r = controller(b).perform(DeviceAction.LaunchApp("com.google.android.gm"), b.screen)
        assertTrue(r is ActionResult.Done && r.after.packageName == "com.google.android.gm")
    }

    @Test
    fun launchStartsFreshWhenTheTaskShowsAnotherApp() = runTest {
        // A registration link opened from Gmail leaves a Chrome tab in Gmail's task.
        val b = FakeBackend(snap(submit, pkg = "com.google.android.apps.nexuslauncher")).apply { taskShows = "com.android.chrome" }
        val r = controller(b).perform(DeviceAction.LaunchApp("com.google.android.gm"), b.screen)
        assertTrue(r.toString(), r is ActionResult.Done && r.after.packageName == "com.google.android.gm")
        assertTrue(b.log.toString(), "launch fresh com.google.android.gm" in b.log)
    }

    @Test
    fun enterTextIsVerified() = runTest {
        val b = FakeBackend(snap(name, submit))
        val ok = controller(b).perform(DeviceAction.EnterText("n0", "Ada Lovelace"), b.screen)
        assertTrue(ok is ActionResult.Done)

        val b2 = FakeBackend(snap(name, submit)).apply { applyText = false }
        val bad = controller(b2).perform(DeviceAction.EnterText("n0", "Ada"), b2.screen)
        assertEquals(ActionResult.Failed("text not present after EnterText"), bad)
    }

    @Test
    fun enterTextOnNonEditableFails() = runTest {
        val b = FakeBackend(snap(name, submit))
        assertTrue(controller(b).perform(DeviceAction.EnterText("n1", "x"), b.screen) is ActionResult.Failed)
    }

    @Test
    fun noProgressAfterThreeUnchangedActions() = runTest {
        val b = FakeBackend(snap(name, submit))
        val c = controller(b)
        assertTrue(c.perform(DeviceAction.Tap("n1"), b.screen) is ActionResult.Done)
        assertTrue(c.perform(DeviceAction.Tap("n1"), b.screen) is ActionResult.Done)
        assertEquals(ActionResult.Failed("no progress"), c.perform(DeviceAction.Tap("n1"), b.screen))
    }

    @Test
    fun waitsWhileNewWindowIsStillEmpty() = runTest {
        val b = FakeBackend(snap(submit, pkg = "com.google.android.apps.nexuslauncher"))
        var settles = 0
        val c = DeviceScreenController(b, fp, settler = {
            settles++
            if (settles == 3) b.screen = snap(node("x", "Inbox"), pkg = "com.google.android.gm")
        }, log = {})
        b.onClick = { b.screen = ScreenSnapshot("", null, 0L, emptyList()) }
        val r = c.perform(DeviceAction.Tap("n1"), b.screen)
        assertTrue(r is ActionResult.Done && r.after.packageName == "com.google.android.gm")
    }

    @Test
    fun fingerprintChangesOnVisibleChangeOnly() {
        val a = snap(name, submit)
        assertEquals(fp.of(a), fp.of(a.copy(capturedAtMs = 99, title = "other")))
        assertNotEquals(fp.of(a), fp.of(snap(name, submit.copy(checked = true))))
        assertNotEquals(fp.of(a), fp.of(snap(submit, name)))
        assertNotEquals(fp.of(a), fp.of(a.copy(packageName = "com.google.android.gm")))
        assertEquals(64, fp.of(a).length)
    }
}
