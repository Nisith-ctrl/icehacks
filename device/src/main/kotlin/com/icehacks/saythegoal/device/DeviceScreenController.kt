package com.icehacks.saythegoal.device

import android.util.Log
import com.icehacks.saythegoal.core.ActionResult
import com.icehacks.saythegoal.core.AllowedApps
import com.icehacks.saythegoal.core.Approval
import com.icehacks.saythegoal.core.Bounds
import com.icehacks.saythegoal.core.DeviceAction
import com.icehacks.saythegoal.core.Fingerprinter
import com.icehacks.saythegoal.core.ScreenController
import com.icehacks.saythegoal.core.ScreenSnapshot
import com.icehacks.saythegoal.core.ScrollDirection
import com.icehacks.saythegoal.core.signature
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A snapshot plus the platform handles for its nodes (same order). Never keep one across actions. */
class Captured<H>(val snapshot: ScreenSnapshot, val handles: List<H>)

/** The platform side: reading the screen and raw actions. No business logic, no validation. */
interface ScreenBackend<H> {
    suspend fun capture(): Captured<H>
    suspend fun click(handle: H, bounds: Bounds): Boolean
    suspend fun setText(handle: H, text: String): Boolean
    /** [handle] null = the main scrollable area of the screen. */
    suspend fun scroll(handle: H?, direction: ScrollDirection): Boolean
    suspend fun back(): Boolean
    suspend fun launch(packageName: String): Boolean
}

fun interface Settler {
    /** Returns once the screen has been quiet for a moment (max 5 s). */
    suspend fun awaitSettled()
}

/**
 * The ScreenController the agent uses. Validates every action before it runs:
 * the current app must be allowlisted, the target must still be on screen (re-found by signature),
 * and commit taps need an Approval whose fingerprint matches the fresh screen.
 * After acting it waits for the screen to settle, verifies the result, and detects no-progress loops.
 */
class DeviceScreenController<H>(
    private val backend: ScreenBackend<H>,
    private val fingerprinter: Fingerprinter,
    private val settler: Settler,
    private val allowedPackages: Set<String> = AllowedApps.packages,
    private val maxStalledActions: Int = 3,
    private val log: (String) -> Unit = { runCatching { Log.i(TAG, it) } },
) : ScreenController {

    private val mutex = Mutex()
    private var stalled = 0

    override suspend fun snapshot(): ScreenSnapshot = backend.capture().snapshot

    override suspend fun perform(action: DeviceAction, on: ScreenSnapshot, approval: Approval?): ActionResult =
        mutex.withLock {
            val result = runCatching { performLocked(action, on, approval) }
                .getOrElse { ActionResult.Failed("exception: ${it.message}") }
            log("perform $action -> ${result.summary()}")
            result
        }

    private suspend fun performLocked(action: DeviceAction, on: ScreenSnapshot, approval: Approval?): ActionResult {
        val before = backend.capture()
        val beforeFp = fingerprinter.of(before.snapshot)

        if (action !is DeviceAction.LaunchApp && before.snapshot.packageName !in allowedPackages) {
            return ActionResult.NotAllowed("current app '${before.snapshot.packageName}' is not allowlisted")
        }

        val ok: Boolean = when (action) {
            is DeviceAction.LaunchApp -> {
                if (action.packageName !in allowedPackages) {
                    return ActionResult.NotAllowed("'${action.packageName}' is not allowlisted")
                }
                backend.launch(action.packageName)
            }

            DeviceAction.Back -> backend.back()

            is DeviceAction.Tap -> {
                val (i, err) = resolve(action.nodeId, on, before)
                if (err != null) return err
                if (action.isCommit) {
                    if (approval == null) return ActionResult.NotAllowed("commit tap needs an approval")
                    if (approval.fingerprint != beforeFp) {
                        return ActionResult.NotAllowed("approval does not match the current screen")
                    }
                }
                backend.click(before.handles[i!!], before.snapshot.nodes[i].bounds)
            }

            is DeviceAction.EnterText -> {
                val (i, err) = resolve(action.nodeId, on, before)
                if (err != null) return err
                if (!before.snapshot.nodes[i!!].editable) return ActionResult.Failed("target ${action.nodeId} is not editable")
                backend.setText(before.handles[i], action.text)
            }

            is DeviceAction.Scroll -> {
                val nodeId = action.nodeId
                val handle = if (nodeId == null) null else {
                    val (i, err) = resolve(nodeId, on, before)
                    if (err != null) return err
                    before.handles[i!!]
                }
                backend.scroll(handle, action.direction)
            }
        }
        if (!ok) return ActionResult.Failed("${action::class.simpleName} could not be performed")

        settler.awaitSettled()
        val after = backend.capture().snapshot
        val changed = fingerprinter.of(after) != beforeFp

        if (action is DeviceAction.EnterText && !textPresent(action, on, after)) {
            return ActionResult.Failed("text not present after EnterText")
        }

        stalled = if (changed) 0 else stalled + 1
        if (stalled >= maxStalledActions) {
            stalled = 0
            return ActionResult.Failed("no progress")
        }
        return ActionResult.Done(after, changed)
    }

    /** Index of the target in the fresh capture, or an error result. */
    private fun resolve(nodeId: String, on: ScreenSnapshot, fresh: Captured<H>): Pair<Int?, ActionResult?> {
        val node = on.nodes.firstOrNull { it.id == nodeId }
            ?: return null to ActionResult.TargetNotFound("no node '$nodeId' in the given snapshot")
        val i = NodeMatcher.find(node.signature(), fresh.snapshot)
            ?: return null to ActionResult.TargetNotFound("'${node.label()}' is no longer on screen")
        return i to null
    }

    /** After EnterText the field's text changed, so look for an editable node near the old bounds holding it. */
    private fun textPresent(action: DeviceAction.EnterText, on: ScreenSnapshot, after: ScreenSnapshot): Boolean {
        val old = on.nodes.firstOrNull { it.id == action.nodeId } ?: return false
        return after.nodes.any { n ->
            n.editable && (n.text ?: "") == action.text &&
                (n.viewId != null && n.viewId == old.viewId || overlaps(n.bounds, old.bounds))
        }
    }

    private fun overlaps(a: Bounds, b: Bounds) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    companion object { const val TAG = "STG-Device" }
}

private fun com.icehacks.saythegoal.core.UiNode.label() =
    text ?: contentDescription ?: hint ?: viewId ?: className

private fun ActionResult.summary() = when (this) {
    is ActionResult.Done -> "Done(changed=$screenChanged, pkg=${after.packageName}, nodes=${after.nodes.size})"
    else -> toString()
}
