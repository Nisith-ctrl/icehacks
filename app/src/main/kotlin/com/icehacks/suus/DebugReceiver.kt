package com.icehacks.suus

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.DebugActions
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.ScrollDirection
import com.icehacks.suus.core.SuusJson
import com.icehacks.suus.agent.data.handleRunLogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

/**
 * Debug-build adb hooks for the device layer (T01). See device/README.md for examples.
 *
 *   DEBUG_SNAPSHOT [--es name home-screen]  -> JSON in /sdcard/Download/suus/ and Logcat (SUUS-Device)
 *   DEBUG_ACTION --es json '{"type":"Tap","nodeId":"n5"}'
 *   DEBUG_ACTION --es tap n5 | --es tapText Gmail | --es text "hi" --es node n7
 *                | --es scroll down|up [--es node n3] | --es back 1 | --es launch com.google.android.gm
 *
 * Node ids refer to the last snapshot taken by these hooks (or a fresh one if there is none).
 */
class DebugReceiver(
    private val service: SuusAccessibilityService,
    private val scope: CoroutineScope,
) : BroadcastReceiver() {

    @Volatile private var last: ScreenSnapshot? = null

    fun register() {
        val filter = IntentFilter().apply {
            addAction(DebugActions.SNAPSHOT)
            addAction(DebugActions.ACTION)
            addAction(DebugActions.RUNLOG)
        }
        ContextCompat.registerReceiver(service, this, filter, ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "debug hooks registered")
    }

    fun unregister() = runCatching { service.unregisterReceiver(this) }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DebugActions.SNAPSHOT -> scope.launch { dumpSnapshot(intent.getStringExtra("name")) }
            DebugActions.ACTION -> scope.launch { runAction(intent) }
            DebugActions.RUNLOG -> scope.launch(Dispatchers.IO) {
                Log.i(TAG, "DEBUG_RUNLOG " + handleRunLogDebug(context, intent.getStringExtra("op")))
            }
        }
    }

    private suspend fun dumpSnapshot(name: String?) {
        val snap = service.screen.snapshot()
        last = snap
        val json = SuusJson.encodeToString(snap)
        val file = (name ?: "${snap.packageName}-${snap.capturedAtMs}") + ".json"
        val saved = runCatching { writeDownload(file, json) }.getOrElse { "failed: ${it.message}" }
        Log.i(TAG, "snapshot ${snap.packageName} '${snap.title}' nodes=${snap.nodes.size} -> $saved")
        logChunked(json)
    }

    private suspend fun runAction(intent: Intent) {
        val on = last ?: service.screen.snapshot()
        val action = runCatching { parseAction(intent, on) }.getOrElse {
            Log.w(TAG, "bad DEBUG_ACTION: ${it.message}")
            return
        }
        val result = service.screen.perform(action, on)
        if (result is ActionResult.Done) last = result.after
        val summary = when (result) {
            is ActionResult.Done -> "Done(screenChanged=${result.screenChanged}, now=${result.after.packageName}, nodes=${result.after.nodes.size})"
            else -> SuusJson.encodeToString(result)
        }
        Log.i(TAG, "DEBUG_ACTION ${SuusJson.encodeToString(action)} -> $summary")
    }

    private fun parseAction(i: Intent, on: ScreenSnapshot): DeviceAction {
        i.getStringExtra("json")?.let { return SuusJson.decodeFromString<DeviceAction>(it) }
        i.getStringExtra("tap")?.let { return DeviceAction.Tap(it) }
        i.getStringExtra("tapText")?.let { label ->
            val node = on.nodes.firstOrNull { it.text.equals(label, true) || it.contentDescription.equals(label, true) }
                ?: on.nodes.firstOrNull {
                    it.text?.contains(label, true) == true || it.contentDescription?.contains(label, true) == true
                }
                ?: error("no node labelled '$label'")
            return DeviceAction.Tap(node.id)
        }
        i.getStringExtra("text")?.let { return DeviceAction.EnterText(i.getStringExtra("node") ?: error("text needs --es node"), it) }
        i.getStringExtra("scroll")?.let {
            return DeviceAction.Scroll(i.getStringExtra("node"), if (it.equals("up", true)) ScrollDirection.UP else ScrollDirection.DOWN)
        }
        if (i.hasExtra("back")) return DeviceAction.Back
        i.getStringExtra("launch")?.let { return DeviceAction.LaunchApp(it) }
        error("no action extra (json|tap|tapText|text|scroll|back|launch)")
    }

    private fun writeDownload(fileName: String, content: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/suus")
        }
        val resolver = service.contentResolver
        // Replace an earlier snapshot with the same name. Otherwise MediaStore saves "name (1).json"
        // and `adb pull .../name.json` silently returns the old file.
        val existing = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf(fileName, Environment.DIRECTORY_DOWNLOADS + "/suus%"), null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        val uri = existing?.let { android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, it) }
            ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
        resolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) } ?: error("open failed")
        return "/sdcard/Download/suus/$fileName"
    }

    private fun logChunked(s: String) {
        s.chunked(3_000).forEachIndexed { i, part -> Log.d(TAG, "json[$i] $part") }
    }

    private companion object { const val TAG = "SUUS-Device" }
}
