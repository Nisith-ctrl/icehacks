package com.icehacks.saythegoal

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.icehacks.saythegoal.ui.ButtonKind
import com.icehacks.saythegoal.ui.MemoryActivity
import com.icehacks.saythegoal.ui.addSpaced
import com.icehacks.saythegoal.ui.bigButton
import com.icehacks.saythegoal.ui.bodyText
import com.icehacks.saythegoal.ui.card
import com.icehacks.saythegoal.ui.dp
import com.icehacks.saythegoal.ui.page
import com.icehacks.saythegoal.ui.roundedBox
import com.icehacks.saythegoal.ui.themeColor
import com.icehacks.saythegoal.ui.titleText
import com.icehacks.saythegoal.voice.wakeSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-time setup, as a checklist. Each step is a card with a done / not done state and one big
 * button that opens the right system dialog or Settings page. The state is re-read every time the
 * screen comes back, so it always matches what Android reports.
 */
class SetupActivity : AppCompatActivity() {

    /** One row of the checklist and the views that show its state. */
    private class Step(
        val number: Int,
        val isDone: () -> Boolean,
        val actionLabel: Int,
        val doneActionLabel: Int? = null, // steps that can be repeated keep a (secondary) button when done
    ) {
        lateinit var box: LinearLayout
        lateinit var badge: TextView
        lateinit var state: TextView
        lateinit var detail: TextView
        lateinit var button: AppCompatButton
        lateinit var buttonAgain: AppCompatButton
        var note: TextView? = null
    }

    private val scope: CoroutineScope = MainScope()
    private lateinit var settings: SharedPreferences
    private lateinit var progress: TextView
    private lateinit var progressFill: View
    private lateinit var progressRest: View
    private lateinit var readyCard: View
    private val steps = mutableListOf<Step>()

    private var wakeJob: Job? = null
    private var wakeListening = false
    private var wakeNote: String? = null

    private val photosPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = getSharedPreferences("setup", Context.MODE_PRIVATE)
        val (root, column) = page()

        column.addSpaced(titleText(getString(R.string.app_name), 32f))
        column.addSpaced(bodyText(getString(R.string.setup_subtitle), 20f), 8)
        column.addSpaced(bodyText(getString(R.string.setup_privacy), 16f), 8)
        progress = bodyText("", 20f, secondary = false).apply {
            typeface = Typeface.DEFAULT_BOLD
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        column.addSpaced(progress, 20)
        column.addSpaced(progressBar(), 8)

        column.addSpaced(
            stepCard(
                R.string.step_service_title, R.string.step_service_detail,
                Step(1, ::serviceEnabled, R.string.step_service_action),
            ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            16,
        )
        column.addSpaced(
            stepCard(
                R.string.step_mic_title, R.string.step_mic_detail,
                Step(2, { granted(Manifest.permission.RECORD_AUDIO) }, R.string.step_mic_action),
            ) { askFor(Manifest.permission.RECORD_AUDIO) },
            16,
        )
        column.addSpaced(
            stepCard(
                R.string.step_calendar_title, R.string.step_calendar_detail,
                Step(3, { granted(Manifest.permission.READ_CALENDAR) }, R.string.step_calendar_action),
            ) { askFor(Manifest.permission.READ_CALENDAR) },
            16,
        )
        column.addSpaced(
            stepCard(
                R.string.step_photos_title, R.string.step_photos_detail,
                Step(4, { granted(photosPermission) }, R.string.step_photos_action),
            ) { askFor(photosPermission) },
            16,
        )
        column.addSpaced(
            stepCard(
                R.string.step_notifications_title, R.string.step_notifications_detail,
                Step(5, ::notificationsAllowed, R.string.step_notifications_action),
            ) { askFor(Manifest.permission.POST_NOTIFICATIONS) },
            16,
        )
        column.addSpaced(
            stepCard(
                R.string.step_wake_title, R.string.step_wake_detail,
                Step(6, { settings.getBoolean(KEY_WAKE_OK, false) }, R.string.step_wake_action, R.string.step_wake_again),
                withNote = true,
            ) { startWakeCheck() },
            16,
        )

        readyCard = card().apply {
            addSpaced(titleText(getString(R.string.setup_all_done_title), 24f))
            addSpaced(bodyText(getString(R.string.setup_all_done_body)), 8)
        }
        column.addSpaced(readyCard, 16)

        column.addSpaced(
            bigButton(getString(R.string.open_memory), ButtonKind.SECONDARY) {
                startActivity(Intent(this, MemoryActivity::class.java))
            },
            24,
        )
        if (BuildConfig.DEBUG) column.addSpaced(bodyText(getString(R.string.debug_note), 14f), 16)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onStop() {
        stopWakeCheck()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    // ---------- building ----------

    /** A thick bar under "3 of 6 steps done": two weighted views, no drawable work needed. */
    private fun progressBar(): View {
        progressFill = View(this).apply { background = roundedBox(themeColor(R.color.stg_ok), 6) }
        progressRest = View(this)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBox(themeColor(R.color.stg_outline), 6)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS // the text says it
            addView(progressFill, LinearLayout.LayoutParams(0, dp(12), 0f))
            addView(progressRest, LinearLayout.LayoutParams(0, dp(12), 1f))
        }
    }

    private fun stepCard(title: Int, detail: Int, step: Step, withNote: Boolean = false, onAction: () -> Unit): View {
        val box = card()
        step.box = box

        step.badge = TextView(this).apply {
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO // the state line says the same thing
        }
        val heading = titleText(getString(title), 22f)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(step.badge, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
        }
        box.addSpaced(header)

        step.state = bodyText("", 18f).apply { typeface = Typeface.DEFAULT_BOLD }
        box.addSpaced(step.state, 10)
        step.detail = bodyText(getString(detail))
        box.addSpaced(step.detail, 6)

        if (withNote) {
            step.note = bodyText("", 18f, secondary = false).apply {
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            box.addSpaced(step.note!!, 10)
        }

        step.button = bigButton(getString(step.actionLabel), ButtonKind.PRIMARY, onAction)
        box.addSpaced(step.button, 16)
        step.buttonAgain = bigButton(getString(step.doneActionLabel ?: step.actionLabel), ButtonKind.SECONDARY, onAction)
        box.addSpaced(step.buttonAgain, 16)

        steps += step
        return box
    }

    // ---------- state ----------

    private fun refresh() {
        val doneFlags = steps.map { it.isDone() }
        val doneCount = doneFlags.count { it }
        // The first step that isn't done is "next": it gets the only filled button and a highlighted
        // card, so there is one obvious thing to press. Later steps keep an outlined button, so a
        // step the user chooses to skip never blocks the rest.
        val next = doneFlags.indexOfFirst { !it }
        for ((i, step) in steps.withIndex()) {
            val done = doneFlags[i]
            val isNext = i == next
            step.badge.text = if (done) "✓" else step.number.toString()
            step.badge.setTextColor(themeColor(if (done) R.color.stg_on_ok else R.color.stg_text))
            step.badge.background =
                if (done) roundedBox(themeColor(R.color.stg_ok), 22)
                else roundedBox(themeColor(R.color.stg_surface), 22, themeColor(R.color.stg_outline))
            step.state.text = getString(
                when {
                    done -> R.string.step_done
                    isNext -> R.string.step_next
                    else -> R.string.step_not_done
                },
            )
            step.state.contentDescription = getString(
                when {
                    done -> R.string.step_number_done
                    isNext -> R.string.step_number_next
                    else -> R.string.step_number_not_done
                },
                step.number,
            )
            step.state.setTextColor(themeColor(if (done) R.color.stg_ok else R.color.stg_pending))
            // Done steps shrink to their title and state; repeatable ones keep their explanation.
            step.detail.visibility = if (done && step.doneActionLabel == null) View.GONE else View.VISIBLE
            step.box.background =
                if (isNext) roundedBox(themeColor(R.color.stg_surface), 20).apply { setStroke(dp(3), themeColor(R.color.stg_primary)) }
                else roundedBox(themeColor(R.color.stg_surface), 20, themeColor(R.color.stg_outline))
            step.button.visibility = if (isNext) View.VISIBLE else View.GONE
            step.buttonAgain.visibility = when {
                done -> if (step.doneActionLabel != null) View.VISIBLE else View.GONE
                isNext -> View.GONE
                else -> View.VISIBLE // a later step: outlined, so it doesn't compete with the next one
            }
        }
        (progressFill.layoutParams as LinearLayout.LayoutParams).weight = doneCount.toFloat()
        (progressRest.layoutParams as LinearLayout.LayoutParams).weight = (steps.size - doneCount).toFloat()
        progressFill.visibility = if (doneCount == 0) View.GONE else View.VISIBLE
        progressFill.requestLayout()
        // The wake check: say what it is doing, and don't let it start twice.
        steps.lastOrNull()?.let { wake ->
            val note = if (wakeListening) getString(R.string.wake_listening) else wakeNote
            wake.note?.text = note ?: ""
            wake.note?.visibility = if (note.isNullOrEmpty()) View.GONE else View.VISIBLE
            wake.button.isEnabled = !wakeListening
            wake.buttonAgain.isEnabled = !wakeListening
            wake.button.alpha = if (wakeListening) 0.5f else 1f
            wake.buttonAgain.alpha = if (wakeListening) 0.5f else 1f
        }
        progress.text = getString(R.string.setup_progress, doneCount, steps.size)
        readyCard.visibility = if (doneCount == steps.size) View.VISIBLE else View.GONE
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** Before Android 13 there is no notification permission to ask for. */
    private fun notificationsAllowed() =
        Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)

    private fun serviceEnabled(): Boolean {
        val me = ComponentName(this, SayTheGoalAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            .any { ComponentName.unflattenFromString(it) == me }
    }

    /**
     * Shows Android's permission dialog. If the user already said "Don't allow" twice, Android won't
     * show it again, so open this app's page in Settings instead (Permissions is one tap from there).
     */
    private fun askFor(permission: String) {
        val askedKey = "asked_$permission"
        val blocked = settings.getBoolean(askedKey, false) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        if (blocked) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            settings.edit().putBoolean(askedKey, true).apply()
            ActivityCompat.requestPermissions(this, arrayOf(permission), 1)
        }
    }

    // ---------- wake phrase check ----------

    /**
     * Waits up to 30 s for the running service to hear the wake word ("Suus", alone or before a
     * request). It uses the same recogniser and wake matching as a real task, so a pass means the
     * microphone, the recogniser and the wake word all work on this phone.
     */
    private fun startWakeCheck() {
        val service = SayTheGoalAccessibilityService.instance
        if (service == null || !granted(Manifest.permission.RECORD_AUDIO)) {
            wakeNote = getString(R.string.wake_needs_setup)
            refresh()
            return
        }
        wakeJob?.cancel()
        wakeListening = true
        wakeNote = null
        refresh()
        wakeJob = scope.launch {
            val heard = withTimeoutOrNull(WAKE_TIMEOUT_MS) { wakeSignals(service.voice).first() }
            wakeListening = false
            if (heard != null) {
                settings.edit().putBoolean(KEY_WAKE_OK, true).apply()
                wakeNote = getString(R.string.wake_heard)
            } else {
                wakeNote = getString(R.string.wake_not_heard)
            }
            refresh()
        }
    }

    private fun stopWakeCheck() {
        wakeJob?.cancel()
        wakeJob = null
        wakeListening = false
    }

    private companion object {
        const val KEY_WAKE_OK = "wake_ok"
        const val WAKE_TIMEOUT_MS = 30_000L
    }
}
