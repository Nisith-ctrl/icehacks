package com.icehacks.suus.agent

import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.Approval
import com.icehacks.suus.core.ApprovalGate
import com.icehacks.suus.core.ApprovalOutcome
import com.icehacks.suus.core.ApprovalResult
import com.icehacks.suus.core.Bounds
import com.icehacks.suus.core.CharacterMode
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.Overlay
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.UiNode
import com.icehacks.suus.core.UserCommand
import com.icehacks.suus.core.VoiceIO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.ZoneId
import java.time.ZonedDateTime

fun n(
    id: String, text: String? = null, cls: String = "android.widget.TextView", desc: String? = null, hint: String? = null,
    clickable: Boolean = false, editable: Boolean = false, checkable: Boolean = false, checked: Boolean = false,
    viewId: String? = null, bounds: Bounds = Bounds(0, id.filter { it.isDigit() }.toInt() * 100, 1000, id.filter { it.isDigit() }.toInt() * 100 + 90),
) = UiNode(id, null, cls, text, desc, hint, viewId, null, bounds, clickable, editable, false, checkable, checked, false, false, true)

fun screenOf(pkg: String, vararg nodes: UiNode) = ScreenSnapshot(pkg, null, 0L, nodes.toList())

/** A scripted phone: [onAction] returns the next screen for an action, or null for "no change". */
class FakeScreen(var current: ScreenSnapshot, val onAction: (DeviceAction, ScreenSnapshot) -> ScreenSnapshot? = { _, _ -> null }) : ScreenController {
    val performed = mutableListOf<Pair<DeviceAction, Approval?>>()
    override suspend fun snapshot() = current
    override suspend fun perform(action: DeviceAction, on: ScreenSnapshot, approval: Approval?): ActionResult {
        performed += action to approval
        if (action is DeviceAction.Tap && action.isCommit && approval == null) return ActionResult.NotAllowed("no approval")
        val next = onAction(action, current) ?: return ActionResult.Done(current, false)
        current = next
        return ActionResult.Done(next, true)
    }
}

class TestVoice(answers: List<String?> = emptyList()) : VoiceIO {
    private val q = ArrayDeque(answers)
    val said = mutableListOf<String>()
    val asked = mutableListOf<String>()
    override val commands: Flow<UserCommand> = emptyFlow()
    override suspend fun say(text: String) { said += text }
    override suspend fun ask(question: String, choices: List<String>): String? { asked += question; return q.removeFirstOrNull() }
}

class ScriptedGate(private val outcome: ApprovalOutcome) : ApprovalGate {
    var readback: String? = null
    override suspend fun request(readback: String, snapshot: ScreenSnapshot, commitWord: String): ApprovalResult {
        this.readback = readback
        return ApprovalResult(outcome, if (outcome == ApprovalOutcome.APPROVED) Approval("fp", 1L) else null)
    }
}

class RecordingOverlay : Overlay {
    val calls = mutableListOf<String>()
    override fun setMode(mode: CharacterMode) { calls += "mode $mode" }
    override fun caption(text: String?) { calls += "caption $text" }
    override suspend fun pointAt(target: Bounds, caption: String) { calls += "point $caption" }
    override suspend fun showResult(target: Bounds, ok: Boolean) { calls += "result $ok" }
    override fun highlight(targets: List<Bounds>) { calls += "highlight ${targets.size}" }
}

class FakeCalendar(private val events: List<CalendarEvent>) : CalendarSource {
    override fun events(startMs: Long, endMs: Long) = events
}

fun loop(
    screen: FakeScreen,
    planner: Planner,
    voice: TestVoice = TestVoice(),
    gate: ApprovalGate = ScriptedGate(ApprovalOutcome.NO_ANSWER),
    overlay: RecordingOverlay = RecordingOverlay(),
    prefs: PreferenceStore = InMemoryPreferenceStore(),
    ledger: DraftLedger = DraftLedger(),
    calendar: CalendarSource? = null,
) = AgentLoop(
    screen, voice, gate, overlay, planner, prefs, ledger, calendar, null,
    now = { ZonedDateTime.of(2026, 11, 9, 9, 0, 0, 0, ZoneId.of("UTC")) }, log = {},
)
