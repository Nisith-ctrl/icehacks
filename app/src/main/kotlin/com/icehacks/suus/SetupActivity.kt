package com.icehacks.suus

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
import com.icehacks.suus.ui.ButtonKind
import com.icehacks.suus.ui.ChipKind
import com.icehacks.suus.agent.createProfileStore
import com.icehacks.suus.ui.MemoryActivity
import com.icehacks.suus.ui.ProfileActivity
import com.icehacks.suus.ui.addSpaced
import com.icehacks.suus.ui.addWrapped
import com.icehacks.suus.ui.appearanceCard
import com.icehacks.suus.ui.applySavedTheme
import com.icehacks.suus.ui.bigButton
import com.icehacks.suus.ui.bodyText
import com.icehacks.suus.ui.card
import com.icehacks.suus.ui.chip
import com.icehacks.suus.ui.dp
import com.icehacks.suus.ui.heroHeader
import com.icehacks.suus.ui.page
import com.icehacks.suus.ui.roundedBox
import com.icehacks.suus.ui.sectionLabel
import com.icehacks.suus.ui.setChip
import com.icehacks.suus.ui.themeColor
import com.icehacks.suus.ui.titleText
import com.icehacks.suus.voice.wakeSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-time setup, as a checklist. Each step is a card with a status chip and one big button that
 * opens the right system dialog or Settings page. The first step that isn't done is highlighted and
 * gets the only filled button, so there is always one obvious thing to press. State is re-read
 * every time the screen comes back, so it always matches what Android reports.
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
        applySavedTheme()
        super.onCreate(savedInstanceState)
        settings = getSharedPreferences("setup", Context.MODE_PRIVATE)
        val (root, column) = page()

        column.addSpaced(heroHeader(getString(R.string.app_name), getString(R.string.setup_subtitle)))

        // Ready card sits right under the header once everything is done: it's what the user needs then.
        readyCard = card().apply {
            background = roundedBox(themeColor(R.color.suus_ok_soft), 24)
            addSpaced(titleText(getString(R.string.setup_all_done_title), 24f, R.color.suus_ok))
            addSpaced(bodyText(getString(R.string.setup_all_done_body), 18f, secondary = false), 8)
        }
        column.addSpaced(readyCard, 16)

        column.addSpaced(progressCard(), 16)

        column.addSpaced(sectionLabel(getString(R.string.section_setup)), 28)
        addStep(column, R.string.step_service_title, R.string.step_service_detail,
            Step(1, ::serviceEnabled, R.string.step_service_action)) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        addStep(column, R.string.step_mic_title, R.string.step_mic_detail,
            Step(2, { granted(Manifest.permission.RECORD_AUDIO) }, R.string.step_mic_action)) { askFor(Manifest.permission.RECORD_AUDIO) }
        addStep(column, R.string.step_calendar_title, R.string.step_calendar_detail,
            Step(3, { granted(Manifest.permission.READ_CALENDAR) }, R.string.step_calendar_action)) { askFor(Manifest.permission.READ_CALENDAR) }
        addStep(column, R.string.step_photos_title, R.string.step_photos_detail,
            Step(4, { granted(photosPermission) }, R.string.step_photos_action)) { askFor(photosPermission) }
        addStep(column, R.string.step_notifications_title, R.string.step_notifications_detail,
            Step(5, ::notificationsAllowed, R.string.step_notifications_action)) { askFor(Manifest.permission.POST_NOTIFICATIONS) }
        addStep(column, R.string.step_profile_title, R.string.step_profile_detail,
            Step(6, ::profileComplete, R.string.step_profile_action, R.string.step_profile_again)) {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        addStep(column, R.string.step_wake_title, R.string.step_wake_detail,
            Step(7, { settings.getBoolean(KEY_WAKE_OK, false) }, R.string.step_wake_action, R.string.step_wake_again),
            withNote = true) { startWakeCheck() }

        column.addSpaced(sectionLabel(getString(R.string.section_more)), 28)
        column.addSpaced(
            card().apply {
                addSpaced(titleText(getString(R.string.open_memory), 22f))
                addSpaced(bodyText(getString(R.string.open_memory_body)), 6)
                addSpaced(
                    bigButton(getString(R.string.open_memory_action), ButtonKind.TONAL) {
                        startActivity(Intent(this@SetupActivity, MemoryActivity::class.java))
                    },
                    16,
                )
            },
            12,
        )
        column.addSpaced(appearanceCard(), 16)
        column.addSpaced(bodyText(getString(R.string.setup_privacy), 16f).apply { gravity = Gravity.CENTER }, 24)
        if (BuildConfig.DEBUG) column.addSpaced(bodyText(getString(R.string.debug_note), 14f).apply { gravity = Gravity.CENTER }, 12)

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

    /** "4 of 6 steps done" and a thick bar. Two weighted views, so no drawable work is needed. */
    private fun progressCard(): View = card().apply {
        progress = titleText("", 22f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            ViewCompat.setAccessibilityHeading(this, false) // a status line, not a heading
        }
        addSpaced(progress)
        progressFill = View(context).apply { background = roundedBox(themeColor(R.color.suus_ok), 8) }
        progressRest = View(context)
        addSpaced(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundedBox(themeColor(R.color.suus_surface_alt), 8)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS // the text says it
                addView(progressFill, LinearLayout.LayoutParams(0, dp(14), 0f))
                addView(progressRest, LinearLayout.LayoutParams(0, dp(14), 1f))
            },
            14,
        )
    }

    private fun addStep(column: LinearLayout, title: Int, detail: Int, step: Step, withNote: Boolean = false, onAction: () -> Unit) {
        val box = card()
        step.box = box

        step.badge = TextView(this).apply {
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO // the chip says the same thing
        }
        val heading = titleText(getString(title), 22f)
        box.addSpaced(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(step.badge, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
            },
        )

        step.state = chip()
        box.addWrapped(step.state, 12)
        step.detail = bodyText(getString(detail))
        box.addSpaced(step.detail, 10)

        if (withNote) {
            step.note = bodyText("", 18f, secondary = false).apply {
                typeface = Typeface.DEFAULT_BOLD
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            box.addSpaced(step.note!!, 12)
        }

        step.button = bigButton(getString(step.actionLabel), ButtonKind.PRIMARY, onAction)
        box.addSpaced(step.button, 16)
        step.buttonAgain = bigButton(getString(step.doneActionLabel ?: step.actionLabel), ButtonKind.SECONDARY, onAction)
        box.addSpaced(step.buttonAgain, 16)

        steps += step
        column.addSpaced(box, 12)
    }

    // ---------- state ----------

    private fun refresh() {
        val doneFlags = steps.map { it.isDone() }
        val doneCount = doneFlags.count { it }
        // The first step that isn't done is "next": it gets the only filled button and a highlighted
        // card. Later steps keep an outlined button, so a step the user skips never blocks the rest.
        val next = doneFlags.indexOfFirst { !it }
        for ((i, step) in steps.withIndex()) {
            val done = doneFlags[i]
            val isNext = i == next
            step.badge.text = if (done) "✓" else step.number.toString()
            step.badge.setTextColor(themeColor(
                when {
                    done -> R.color.suus_on_ok
                    isNext -> R.color.suus_on_primary
                    else -> R.color.suus_text_secondary
                },
            ))
            step.badge.background = when {
                done -> roundedBox(themeColor(R.color.suus_ok), 24)
                isNext -> roundedBox(themeColor(R.color.suus_primary), 24)
                else -> roundedBox(themeColor(R.color.suus_surface_alt), 24)
            }
            when {
                done -> step.state.setChip(getString(R.string.step_done), ChipKind.DONE)
                isNext -> step.state.setChip(getString(R.string.step_next), ChipKind.NEXT)
                else -> step.state.setChip(getString(R.string.step_not_done), ChipKind.WAITING)
            }
            step.state.contentDescription = getString(
                when {
                    done -> R.string.step_number_done
                    isNext -> R.string.step_number_next
                    else -> R.string.step_number_not_done
                },
                step.number,
            )
            // Done steps shrink to their title and chip; repeatable ones keep their explanation.
            step.detail.visibility = if (done && step.doneActionLabel == null) View.GONE else View.VISIBLE
            step.box.background =
                if (isNext) roundedBox(themeColor(R.color.suus_surface), 24, themeColor(R.color.suus_primary), 3)
                else roundedBox(themeColor(R.color.suus_surface), 24, themeColor(R.color.suus_outline), 1)
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
        steps.firstOrNull { it.note != null }?.let { wake ->
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

    /** Name, email and phone saved on the "About you" screen. */
    private fun profileComplete(): Boolean = createProfileStore(this).load().let {
        it.fullName.isNotBlank() && it.email.isNotBlank() && it.phone.isNotBlank()
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** Before Android 13 there is no notification permission to ask for. */
    private fun notificationsAllowed() =
        Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)

    private fun serviceEnabled(): Boolean {
        val me = ComponentName(this, SuusAccessibilityService::class.java)
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

    // ---------- wake word check ----------

    /**
     * Waits up to 30 s for the running service to hear the wake word ("SuuS", alone or before a
     * request). It uses the same recogniser and wake matching as a real task, so a pass means the
     * microphone, the recogniser and the wake word all work on this phone.
     */
    private fun startWakeCheck() {
        val service = SuusAccessibilityService.instance
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
