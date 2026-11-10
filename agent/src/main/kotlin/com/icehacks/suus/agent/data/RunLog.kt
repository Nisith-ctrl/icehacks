package com.icehacks.suus.agent.data

import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.AgentRunner
import com.icehacks.suus.core.Approval
import com.icehacks.suus.core.ApprovalGate
import com.icehacks.suus.core.ApprovalResult
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.UiNode
import com.icehacks.suus.core.UserCommand
import com.icehacks.suus.core.VoiceIO
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hooks the run log into the agent without touching the agent loop: wrap the objects `createAgent`
 * hands to the agent and every action, question, approval and spoken line is logged.
 *
 * ```
 * val runLog = createRunLog(context)
 * val screen = runLog.screen(screen); val voice = runLog.voice(voice); val approval = runLog.approval(approval)
 * ...
 * return runLog.agent(SuusAgent(...))
 * ```
 *
 * Wrap only the agent's copies. The approval gate keeps the raw VoiceIO, so its own "Submit or
 * Cancel?" prompt is logged once, as the approval, not again as a question.
 */
class RunLog(
    val logger: RunLogger,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val acting = AtomicInteger(0)
    @Volatile private var lastActionEndMs = Long.MIN_VALUE / 2
    @Volatile private var lastSaid: String? = null

    fun screen(inner: ScreenController): ScreenController = object : ScreenController {
        override suspend fun snapshot() = inner.snapshot()

        override suspend fun perform(action: DeviceAction, on: ScreenSnapshot, approval: Approval?): ActionResult {
            acting.incrementAndGet()
            try {
                val r = inner.perform(action, on, approval)
                logger.actionPerformed(actionName(action), target(action, on), r is ActionResult.Done, detail(action, r))
                return r
            } finally {
                lastActionEndMs = clock()
                acting.decrementAndGet()
            }
        }
    }

    fun voice(inner: VoiceIO): VoiceIO = object : VoiceIO {
        override val commands get() = inner.commands

        override suspend fun say(text: String) {
            lastSaid = text
            logger.agentSaid(text)
            inner.say(text)
        }

        override suspend fun ask(question: String, choices: List<String>): String? {
            val answer = inner.ask(question, choices)
            logger.questionAsked(question, answer)
            return answer
        }
    }

    fun approval(inner: ApprovalGate): ApprovalGate = object : ApprovalGate {
        override suspend fun request(readback: String, snapshot: ScreenSnapshot, commitWord: String): ApprovalResult {
            val r = inner.request(readback, snapshot, commitWord)
            logger.approvalRequested(readback, commitWord, r.outcome.name)
            return r
        }
    }

    /** One session per goal / undo / memory command. "Stop" is logged on the session it stopped. */
    fun agent(inner: AgentRunner): AgentRunner = object : AgentRunner {
        override suspend fun handle(command: UserCommand) {
            when (command) {
                UserCommand.Stop -> {
                    logger.stopRequested()
                    inner.handle(command)
                    return
                }
                is UserCommand.Goal -> logger.sessionStarted(SessionKind.GOAL, command.text, command.takeOver)
                is UserCommand.Undo -> logger.sessionStarted(SessionKind.UNDO, command.text)
                is UserCommand.Memory -> logger.sessionStarted(SessionKind.MEMORY, command.text)
            }
            lastSaid = null
            try {
                inner.handle(command)
            } catch (e: CancellationException) {
                logger.sessionEnded("stopped_by_user", "the user said stop")
                throw e
            } catch (e: Throwable) {
                logger.sessionEnded("error", e.toString())
                throw e
            }
            logger.sessionEnded("finished", lastSaid)
        }
    }

    /**
     * Call from the AccessibilityService's `onAccessibilityEvent` for events from other apps.
     * A click while the agent isn't acting (and not just after) counts as a user touch.
     */
    fun onUiEvent(eventType: Int, packageName: CharSequence?) {
        if (eventType != TYPE_VIEW_CLICKED && eventType != TYPE_VIEW_LONG_CLICKED) return
        if (packageName?.toString() == OWN_PACKAGE) return
        if (acting.get() > 0 || clock() - lastActionEndMs < QUIET_AFTER_ACTION_MS) return
        logger.userTouch((if (eventType == TYPE_VIEW_LONG_CLICKED) "long press in " else "tap in ") + (packageName ?: "unknown app"))
    }

    companion object {
        /** Clicks this soon after an agent action are the action's own echo, not the user. */
        const val QUIET_AFTER_ACTION_MS = 1_500L
        private const val OWN_PACKAGE = "com.icehacks.suus"
        // android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED / TYPE_VIEW_LONG_CLICKED
        private const val TYPE_VIEW_CLICKED = 0x00000001
        private const val TYPE_VIEW_LONG_CLICKED = 0x00000002

        fun actionName(a: DeviceAction) = when (a) {
            is DeviceAction.Tap -> if (a.isCommit) "submit" else "tap"
            is DeviceAction.EnterText -> "type"
            is DeviceAction.Scroll -> "scroll"
            DeviceAction.Back -> "back"
            is DeviceAction.LaunchApp -> "launch"
        }

        /** A short name for what was acted on. Never a field's contents: typed values aren't logged. */
        fun target(a: DeviceAction, on: ScreenSnapshot): String? {
            fun node(id: String?) = id?.let { i -> on.nodes.firstOrNull { it.id == i } }
            return when (a) {
                is DeviceAction.Tap -> node(a.nodeId)?.let { label(it, on) } ?: a.nodeId
                is DeviceAction.EnterText -> node(a.nodeId)?.let { label(it, on) } ?: a.nodeId
                is DeviceAction.Scroll -> node(a.nodeId)?.let { label(it, on) }
                DeviceAction.Back -> null
                is DeviceAction.LaunchApp -> a.packageName
            }
        }

        fun label(n: UiNode, on: ScreenSnapshot): String {
            val labelNode = n.labeledById?.let { id -> on.nodes.firstOrNull { it.id == id } }
            val words = if (n.editable) {
                listOf(labelNode?.text, labelNode?.contentDescription, n.hint, n.contentDescription)
            } else {
                listOf(n.text, n.contentDescription, n.hint, labelNode?.text)
            }
            return (words.firstOrNull { !it.isNullOrBlank() }
                ?: n.viewId?.substringAfterLast('/')
                ?: n.className.substringAfterLast('.')).trim().take(80)
        }

        private fun detail(a: DeviceAction, r: ActionResult): String? {
            val what = when (a) {
                is DeviceAction.EnterText -> "${a.text.length} chars"
                is DeviceAction.Scroll -> a.direction.name.lowercase()
                else -> null
            }
            val result = when (r) {
                is ActionResult.Done -> if (r.screenChanged) null else "screen didn't change"
                is ActionResult.TargetNotFound -> "target not found: ${r.detail}"
                is ActionResult.NotAllowed -> "not allowed: ${r.reason}"
                is ActionResult.Failed -> "failed: ${r.reason}"
            }
            return listOfNotNull(what, result).joinToString("; ").ifEmpty { null }
        }
    }
}
