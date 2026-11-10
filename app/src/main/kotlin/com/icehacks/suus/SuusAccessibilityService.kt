package com.icehacks.suus

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.icehacks.suus.agent.createAgent
import com.icehacks.suus.core.AgentRunner
import com.icehacks.suus.core.Fingerprinter
import com.icehacks.suus.core.Overlay
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.UserCommand
import com.icehacks.suus.core.VoiceIO
import com.icehacks.suus.device.AccessibilityBackend
import com.icehacks.suus.device.DeviceScreenController
import com.icehacks.suus.device.EventSettler
import com.icehacks.suus.device.Sha256Fingerprinter
import com.icehacks.suus.overlay.createOverlay
import com.icehacks.suus.voice.createApprovalGate
import com.icehacks.suus.voice.createVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Hosts everything: builds the device controller and each module through its factory, then routes
 * voice commands to the agent one at a time. UserCommand.Stop cancels the running command and
 * drops anything queued.
 */
class SuusAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settler = EventSettler()

    lateinit var screen: ScreenController
        private set
    lateinit var fingerprinter: Fingerprinter
        private set
    lateinit var overlay: Overlay
        private set
    lateinit var voice: VoiceIO
        private set
    private lateinit var agent: AgentRunner

    private val queue = Channel<UserCommand>(Channel.UNLIMITED)
    @Volatile private var current: Job? = null
    private var debug: DebugReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        fingerprinter = Sha256Fingerprinter()
        screen = DeviceScreenController(AccessibilityBackend(this), fingerprinter, settler)
        overlay = createOverlay(this)
        voice = createVoice(this, scope)
        val approval = createApprovalGate(voice, fingerprinter)
        agent = createAgent(screen, voice, approval, overlay, this)

        scope.launch {
            voice.commands.collect { cmd ->
                if (cmd is UserCommand.Stop) {
                    while (queue.tryReceive().isSuccess) { /* drop queued commands */ }
                    current?.cancel()
                    Log.i(TAG, "Stop: cancelled agent job")
                }
                // Stop is also passed on so the agent can say it stopped and reset its state.
                queue.send(cmd)
            }
        }
        scope.launch {
            for (cmd in queue) {
                val job = scope.launch {
                    runCatching { agent.handle(cmd) }.onFailure { Log.w(TAG, "agent.handle failed", it) }
                }
                current = job
                job.join()
            }
        }

        if (BuildConfig.DEBUG) debug = DebugReceiver(this, scope).also { it.register() }
        instance = this
        Log.i(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Ignore our own windows (overlay animations must not keep the screen "unsettled").
        if (event.packageName?.toString() == packageName) return
        settler.onEvent()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        debug?.unregister()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TAG = "SUUS-Device"

        /** The running service, or null. For debug hooks and the setup screen only. */
        @Volatile var instance: SuusAccessibilityService? = null
            private set
    }
}
