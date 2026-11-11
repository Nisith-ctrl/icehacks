package com.icehacks.suus.device

import android.util.Log
import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.AllowedApps
import com.icehacks.suus.core.Approval
import com.icehacks.suus.core.Bounds
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.Fingerprinter
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.ScrollDirection
import com.icehacks.suus.core.signature
import kotlinx.coroutines.delay
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
    /** [fresh] = start the app at its main screen, closing whatever its task was showing. */
    suspend fun launch(packageName: String, fresh: Boolean = false): Boolean
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
    private val maxEmptyRetries: Int = 10,
    private val slowResponseRounds: Int = 4,
    private val slowResponsePauseMs: Long = 500,
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
        var after = backend.capture().snapshot
        // The screen can go quiet before the result is drawn: an app launch, or a new window that
        // has no readable tree yet. Settle again (bounded) until the expected state shows up.
        fun pending(s: ScreenSnapshot) = s.nodes.isEmpty() ||
            (action is DeviceAction.LaunchApp && s.packageName != action.packageName)
        // Cold app starts on the emulator can take several seconds (first-run screens). A tap on a
        // home-screen icon starts an app just like LaunchApp does.
        val opensApp = action is DeviceAction.LaunchApp || action is DeviceAction.Tap
        var relaunched = false
        repeat(if (opensApp) maxEmptyRetries * 3 else maxEmptyRetries) {
            if (!pending(after)) return@repeat
            // The app's task was showing another app's screen on top, e.g. a Chrome tab opened from a
            // Gmail link lives in Gmail's task. Start the app fresh once so its own main screen shows.
            if (action is DeviceAction.LaunchApp && !relaunched && after.nodes.isNotEmpty() && after.packageName != action.packageName) {
                relaunched = true
                backend.launch(action.packageName, fresh = true)
            }
            settler.awaitSettled()
            after = backend.capture().snapshot
        }
        val switchedApp = when (action) {
            is DeviceAction.LaunchApp -> after.packageName == action.packageName
            else -> after.nodes.isNotEmpty() && after.packageName != before.snapshot.packageName
        }
        if (switchedApp) {
            // A cold start (or a fresh relaunch) draws its frame first and its content later, e.g.
            // Gmail's search bar before the inbox. Wait (bounded) until the screen stops filling in.
            for (round in 1..maxEmptyRetries) {
                settler.awaitSettled()
                val next = backend.capture().snapshot
                val grew = next.nodes.size > after.nodes.size + 5
                after = next
                if (!grew) break
            }
        }
        if (action !is DeviceAction.LaunchApp && fingerprinter.of(after) == beforeFp) {
            // Slow UI responses: a link opening Chrome for the first time after boot can take a few
            // seconds before anything changes. Look again a few times before calling it "no change".
            for (round in 1..slowResponseRounds) {
                settler.awaitSettled()
                after = backend.capture().snapshot
                if (fingerprinter.of(after) != beforeFp) break
                if (action !is DeviceAction.Tap) break
                delay(slowResponsePauseMs)
            }
        }
        if (action is DeviceAction.LaunchApp && after.packageName != action.packageName) {
            return ActionResult.Failed("${action.packageName} did not come to the front")
        }
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

    companion object { const val TAG = "SUUS-Device" }
}

private fun com.icehacks.suus.core.UiNode.label() =
    text ?: contentDescription ?: hint ?: viewId ?: className

private fun ActionResult.summary() = when (this) {
    is ActionResult.Done -> "Done(changed=$screenChanged, pkg=${after.packageName}, nodes=${after.nodes.size})"
    else -> toString()
}
