// Shared contracts, copied verbatim from vault/team-memory/project-brief.md (Contracts), plus
// kotlinx.serialization annotations. Do not change without a decision note in the vault.
package com.icehacks.suus.core

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Screen model (produced by T01, consumed by T03/T04) ----------

@Serializable
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

@Serializable
data class UiNode(
    val id: String,                  // unique within ONE snapshot only, e.g. "n17"
    val parentId: String?,
    val className: String,           // e.g. "android.widget.EditText"
    val text: String?,
    val contentDescription: String?,
    val hint: String?,
    val viewId: String?,             // viewIdResourceName
    val labeledById: String?,        // id of the node that labels this one, if exposed
    val bounds: Bounds,              // screen coordinates
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val selected: Boolean,
    val focused: Boolean,
    val enabled: Boolean,
)

@Serializable
data class ScreenSnapshot(
    val packageName: String,         // package of the active app window
    val title: String?,              // window title / Chrome page title if available
    val capturedAtMs: Long,
    val nodes: List<UiNode>,         // pre-order; our own overlay and the IME are excluded
)

/** Node ids are not stable between snapshots. Signatures are used to re-find a node. */
@Serializable
data class NodeSignature(
    val className: String,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val hint: String?,
    val bounds: Bounds,
)

fun UiNode.signature() = NodeSignature(className, viewId, text, contentDescription, hint, bounds)

// ---------- Actions (executed by T01, chosen by T03) ----------

@Serializable
enum class ScrollDirection { UP, DOWN }

@Serializable
sealed interface DeviceAction {
    @Serializable @SerialName("Tap")
    data class Tap(val nodeId: String, val isCommit: Boolean = false) : DeviceAction // isCommit = Submit/Send
    @Serializable @SerialName("EnterText")
    data class EnterText(val nodeId: String, val text: String) : DeviceAction        // replaces field text
    @Serializable @SerialName("Scroll")
    data class Scroll(val nodeId: String?, val direction: ScrollDirection) : DeviceAction
    @Serializable @SerialName("Back")
    data object Back : DeviceAction
    @Serializable @SerialName("LaunchApp")
    data class LaunchApp(val packageName: String) : DeviceAction                     // allowlisted only
}

@Serializable
sealed interface ActionResult {
    @Serializable @SerialName("Done")
    data class Done(val after: ScreenSnapshot, val screenChanged: Boolean) : ActionResult
    @Serializable @SerialName("TargetNotFound")
    data class TargetNotFound(val detail: String) : ActionResult
    @Serializable @SerialName("NotAllowed")
    data class NotAllowed(val reason: String) : ActionResult // not allowlisted, or commit without valid approval
    @Serializable @SerialName("Failed")
    data class Failed(val reason: String) : ActionResult
}

interface ScreenController {
    suspend fun snapshot(): ScreenSnapshot

    /**
     * Re-validates the target against `on` (same signature still present in a fresh snapshot),
     * performs the action, waits for the screen to settle (max 5 s), returns the new snapshot.
     * A Tap with isCommit = true is refused unless `approval` matches StateFingerprint.of(fresh snapshot).
     */
    suspend fun perform(action: DeviceAction, on: ScreenSnapshot, approval: Approval? = null): ActionResult
}

// ---------- Approval (gate by T02, fingerprint by T01, enforced in T01's perform) ----------

@Serializable
data class Approval(val fingerprint: String, val grantedAtMs: Long)

/** SHA-256 hex of packageName + every node's className|viewId|text|contentDescription|checked|selected,
 *  in snapshot order. Any visible change (including a scroll) invalidates an approval. */
interface Fingerprinter { fun of(snapshot: ScreenSnapshot): String }

enum class ApprovalOutcome { APPROVED, CANCELLED, NO_ANSWER }

data class ApprovalResult(val outcome: ApprovalOutcome, val approval: Approval?) // approval != null only if APPROVED

interface ApprovalGate {
    /** Speaks `readback`, then accepts only `commitWord` ("Submit"/"Send") or "Cancel". */
    suspend fun request(readback: String, snapshot: ScreenSnapshot, commitWord: String): ApprovalResult
}

// ---------- Voice (T02) ----------

sealed interface UserCommand {
    data class Goal(val text: String, val takeOver: Boolean) : UserCommand // takeOver = keep current screen/draft
    data class Undo(val text: String) : UserCommand                        // "undo the photo"
    data class Memory(val text: String) : UserCommand                      // "what do you remember", "forget ..."
    data object Stop : UserCommand                                         // "stop"/"cancel" outside a prompt
}

interface VoiceIO {
    val commands: Flow<UserCommand>                       // utterances while a session is active
    suspend fun say(text: String)                         // returns after TTS finishes; mic paused meanwhile
    suspend fun ask(question: String, choices: List<String> = emptyList()): String? // transcript, null after 2 silences
}

// ---------- Overlay (T04) ----------

enum class CharacterMode { HIDDEN, IDLE, LISTENING, THINKING, ACTING, WAITING_APPROVAL, PROBLEM }

interface Overlay {
    fun setMode(mode: CharacterMode)
    fun caption(text: String?)                              // null hides the caption
    suspend fun pointAt(target: Bounds, caption: String)    // move beside target + highlight; returns when settled (<= 700 ms)
    suspend fun showResult(target: Bounds, ok: Boolean)     // brief tick/cross on the target (<= 400 ms)
    fun highlight(targets: List<Bounds>)                    // persistent highlights for read-back; empty list clears
}

// ---------- Agent (T03) ----------

interface AgentRunner {
    suspend fun handle(command: UserCommand) // app cancels the running coroutine on UserCommand.Stop
}

// ---------- Shared constants ----------

object AllowedApps {
    // T01 verifies the real package names on the emulator image and the S21 and updates this list.
    val packages = setOf(
        "com.icehacks.suus",
        "com.google.android.gm",                   // Gmail
        "com.google.android.calendar",             // Calendar
        "com.android.chrome",                      // Chrome
        "com.google.android.apps.messaging",       // Messages
        "com.google.android.apps.nexuslauncher",   // Pixel launcher (emulator)
        "com.sec.android.app.launcher",            // Samsung launcher (S21)
        "com.google.android.providers.media.module", // system photo picker
        "com.android.providers.media.module",
        "com.google.android.documentsui",
    )
}

object DebugActions { // broadcast actions, registered in debug builds only
    const val SNAPSHOT = "com.icehacks.suus.DEBUG_SNAPSHOT"   // T01: dump snapshot JSON
    const val ACTION = "com.icehacks.suus.DEBUG_ACTION"       // T01: --es json '<DeviceAction JSON>'
    const val UTTERANCE = "com.icehacks.suus.DEBUG_UTTERANCE" // T02: --es text "..." as if spoken
    const val OVERLAY = "com.icehacks.suus.DEBUG_OVERLAY"     // T04: --es demo point|caption|highlight
}
