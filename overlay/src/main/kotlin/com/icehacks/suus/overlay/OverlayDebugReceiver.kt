package com.icehacks.suus.overlay

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.icehacks.suus.core.Bounds
import com.icehacks.suus.core.CharacterMode
import com.icehacks.suus.core.DebugActions
import com.icehacks.suus.core.Overlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debug-build adb hook, one feature at a time:
 *
 *   DEBUG_OVERLAY --es demo point [--es text Gmail | --ei l 100 --ei t 900 --ei r 500 --ei b 1100]
 *   DEBUG_OVERLAY --es demo caption [--es text "Filling in your email"]
 *   DEBUG_OVERLAY --es demo highlight [--es text "Gmail,Chrome"]
 *   DEBUG_OVERLAY --es demo mode --es mode LISTENING
 *   DEBUG_OVERLAY --es demo tour        (every mode in turn)
 *   DEBUG_OVERLAY --es demo clear
 */
internal class OverlayDebugReceiver(
    private val service: AccessibilityService,
    private val overlay: Overlay,
) : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun register() {
        ContextCompat.registerReceiver(
            service, this, IntentFilter(DebugActions.OVERLAY), ContextCompat.RECEIVER_EXPORTED,
        )
        Log.i(TAG, "DEBUG_OVERLAY registered")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val demo = intent.getStringExtra("demo") ?: "point"
        val text = intent.getStringExtra("text")
        Log.i(TAG, "DEBUG_OVERLAY demo=$demo text=$text")
        scope.launch {
            when (demo) {
                "point" -> {
                    val target = boundsFromExtras(intent) ?: text?.let { findOnScreen(it) } ?: defaultTarget()
                    overlay.setMode(CharacterMode.ACTING)
                    overlay.pointAt(target, "Tapping ${text ?: "this"}")
                    delay(1_200)
                    overlay.showResult(target, ok = true)
                }
                "caption" -> {
                    overlay.setMode(CharacterMode.IDLE)
                    overlay.caption(text ?: "Filling in your email. Say \"stop\" any time.")
                }
                "highlight" -> {
                    val labels = text?.split(",")?.map { it.trim() }
                    val targets = labels?.mapNotNull { findOnScreen(it) } ?: listOf(defaultTarget())
                    overlay.setMode(CharacterMode.WAITING_APPROVAL)
                    overlay.highlight(targets)
                    overlay.caption("Check these, then say \"Submit\" or \"Cancel\".")
                }
                "mode" -> {
                    val m = runCatching { CharacterMode.valueOf(intent.getStringExtra("mode") ?: "IDLE") }
                        .getOrDefault(CharacterMode.IDLE)
                    overlay.setMode(m)
                    overlay.caption(m.name.lowercase().replace('_', ' '))
                }
                "tour" -> for (m in CharacterMode.entries.filter { it != CharacterMode.HIDDEN }) {
                    overlay.setMode(m)
                    overlay.caption(m.name.lowercase().replace('_', ' '))
                    delay(1_500)
                }
                "clear" -> {
                    overlay.highlight(emptyList())
                    overlay.caption(null)
                    overlay.setMode(CharacterMode.HIDDEN)
                }
            }
        }
    }

    private fun boundsFromExtras(i: Intent): Bounds? =
        if (i.hasExtra("l")) Bounds(i.getIntExtra("l", 0), i.getIntExtra("t", 0), i.getIntExtra("r", 0), i.getIntExtra("b", 0)) else null

    private fun defaultTarget(): Bounds {
        val m = service.resources.displayMetrics
        return Bounds(m.widthPixels / 4, m.heightPixels / 2, m.widthPixels * 3 / 4, m.heightPixels / 2 + m.heightPixels / 12)
    }

    /** First visible node whose text or description contains [label] (debug only). */
    private fun findOnScreen(label: String): Bounds? {
        val root = service.rootInActiveWindow ?: return null
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val r = Rect()
        while (stack.isNotEmpty()) {
            val n = stack.removeFirst()
            val hit = n.text?.toString()?.equals(label, true) == true ||
                n.contentDescription?.toString()?.equals(label, true) == true
            if (hit && n.isVisibleToUser) {
                n.getBoundsInScreen(r)
                return Bounds(r.left, r.top, r.right, r.bottom)
            }
            for (k in 0 until n.childCount) n.getChild(k)?.let { stack.add(it) }
        }
        return null
    }

    private companion object { const val TAG = "SUUS-Overlay" }
}
