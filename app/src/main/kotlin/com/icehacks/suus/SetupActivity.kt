package com.icehacks.suus

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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
import com.icehacks.suus.agent.createSettingsStore
import com.icehacks.suus.ui.CustomizeActivity
import com.icehacks.suus.ui.MemoryActivity
import com.icehacks.suus.ui.OnboardingActivity
import com.icehacks.suus.ui.ProfileActivity
import com.icehacks.suus.ui.ListRow
import com.icehacks.suus.ui.addHero
import com.icehacks.suus.ui.addSpaced
import com.icehacks.suus.ui.listGroup
import com.icehacks.suus.ui.promiseLine
import com.icehacks.suus.ui.PIXEL_INK
import com.icehacks.suus.ui.robotSays
import com.icehacks.suus.ui.SPRING_OUT
import com.icehacks.suus.ui.SuusFonts
import com.icehacks.suus.ui.addWrapped
import com.icehacks.suus.ui.animateLayoutChanges
import com.icehacks.suus.ui.applySavedTheme
import com.icehacks.suus.ui.bigButton
import com.icehacks.suus.ui.bodyText
import com.icehacks.suus.ui.card
import com.icehacks.suus.ui.chip
import com.icehacks.suus.ui.dp
import com.icehacks.suus.ui.heroHeader
import com.icehacks.suus.ui.liftTo
import com.icehacks.suus.ui.motionAllowed
import com.icehacks.suus.ui.page
import com.icehacks.suus.ui.revealChildren
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
    private lateinit var statusPill: TextView
    private lateinit var progressTrack: View
    private lateinit var progressFill: View
    private lateinit var setupLabel: View
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
        // First launch: the step-by-step setup (permissions, About you, how SuuS talks). Someone who
        // already did all of it before setup existed goes straight to this home screen.
        val store = createSettingsStore(this)
        if (!store.load().onboarded) {
            if (alreadySetUp()) {
                store.save(store.load().copy(onboarded = true))
            } else {
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
                return
            }
        }
        val (root, column) = page()

        val first = createProfileStore(this).load().fullName.trim().substringBefore(' ')
        val greeting = if (first.isBlank()) getString(R.string.home_greeting_anon) else getString(R.string.home_greeting, first)
        column.addHero(heroHeader(greeting, getString(R.string.home_say), below = statusBlock()))

        setupLabel = sectionLabel(getString(R.string.home_finish_setup))
        column.addSpaced(setupLabel, 28)
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

        // The robot suggests what to say, in its own pixel speech bubble (the overlay's style).
        column.addSpaced(robotSays(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addSpaced(TextView(context).apply {
                text = getString(R.string.try_title) + ":"
                textSize = 20f
                letterSpacing = 0.03f
                typeface = SuusFonts.heading(context, 500)
                setTextColor(PIXEL_INK)
                ViewCompat.setAccessibilityHeading(this, true)
            })
            for (line in listOf(R.string.try_1, R.string.try_2, R.string.try_3)) {
                addSpaced(TextView(context).apply {
                    text = "› " + getString(line).trim('"')
                    textSize = 18f
                    setLineSpacing(0f, 1.15f)
                    typeface = SuusFonts.body(context)
                    setTextColor(PIXEL_INK)
                    contentDescription = getString(line)
                }, 10)
            }
        }), 32)

        column.addSpaced(sectionLabel(getString(R.string.section_yours)), 28)
        column.addSpaced(listGroup(listOf(
            ListRow(getString(R.string.custom_title), getString(R.string.row_custom_body)) {
                startActivity(Intent(this, CustomizeActivity::class.java))
            },
            ListRow(getString(R.string.about_card_title), getString(R.string.about_card_body)) {
                startActivity(Intent(this, ProfileActivity::class.java))
            },
            ListRow(getString(R.string.open_memory), getString(R.string.open_memory_body)) {
                startActivity(Intent(this, MemoryActivity::class.java))
            },
            ListRow(getString(R.string.onb_again), getString(R.string.row_setup_body)) {
                startActivity(Intent(this, OnboardingActivity::class.java))
            },
        )), 12)
        column.addSpaced(promiseLine(getString(R.string.setup_privacy)), 28)

        setContentView(root)
        column.animateLayoutChanges()
        if (savedInstanceState == null) column.revealChildren()
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

    /**
     * Inside the header: a status pill ("Ready when you are" / "2 steps left"), one line on how to use
     * SuuS, and, while setup isn't finished, a thin bar in the ribbon colours that grows to the new
     * value (scaleX from the left, so nothing re-lays out). The pill is a live region for TalkBack.
     */
    private fun statusBlock(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        statusPill = TextView(context).apply {
            textSize = 17f
            typeface = SuusFonts.bodyBold(context)
            setPadding(dp(16), dp(8), dp(18), dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addWrapped(statusPill)
        addSpaced(bodyText(getString(R.string.home_hint), 18f), 12)
        progressFill = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(themeColor(R.color.suus_ribbon_1), themeColor(R.color.suus_ribbon_3), themeColor(R.color.suus_ribbon_4)),
            ).apply { cornerRadius = dp(5).toFloat() }
            pivotX = 0f
            scaleX = 0f
        }
        progressTrack = FrameLayout(context).apply {
            background = roundedBox(themeColor(R.color.suus_surface_alt), 5)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS // the pill says it
            addView(progressFill, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(10)))
        }
        addSpaced(progressTrack, 16)
    }

    private fun addStep(column: LinearLayout, title: Int, detail: Int, step: Step, withNote: Boolean = false, onAction: () -> Unit) {
        val box = card()
        step.box = box

        step.badge = TextView(this).apply {
            textSize = 20f
            typeface = SuusFonts.bodyBold(context)
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
                typeface = SuusFonts.bodyBold(context)
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
            // Home shows only what still needs attention; finished steps live in "Run setup again".
            step.box.visibility = if (done) View.GONE else View.VISIBLE
            step.box.background =
                if (isNext) roundedBox(themeColor(R.color.suus_surface), 24, themeColor(R.color.suus_primary), 2)
                else roundedBox(themeColor(R.color.suus_surface), 24, themeColor(R.color.suus_outline), 1)
            step.box.liftTo(if (isNext) 3 else 0) // the next step sits slightly above the rest
            step.button.visibility = if (isNext) View.VISIBLE else View.GONE
            step.buttonAgain.visibility = when {
                done -> if (step.doneActionLabel != null) View.VISIBLE else View.GONE
                isNext -> View.GONE
                else -> View.VISIBLE // a later step: outlined, so it doesn't compete with the next one
            }
        }
        val fraction = doneCount.toFloat() / steps.size
        if (motionAllowed()) {
            progressFill.animate().scaleX(fraction).setStartDelay(200).setDuration(600).setInterpolator(SPRING_OUT).start()
        } else {
            progressFill.scaleX = fraction
        }
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
        val left = steps.size - doneCount
        val status = if (left == 0) getString(R.string.home_status_ready)
            else resources.getQuantityString(R.plurals.home_steps_left, left, left)
        statusPill.text = "●  $status"
        statusPill.contentDescription = status
        statusPill.setTextColor(themeColor(if (left == 0) R.color.suus_ok else R.color.suus_pending))
        statusPill.background = roundedBox(themeColor(if (left == 0) R.color.suus_ok_soft else R.color.suus_pending_soft), 20)
        progressTrack.visibility = if (left == 0) View.GONE else View.VISIBLE
        setupLabel.visibility = if (doneCount == steps.size) View.GONE else View.VISIBLE
    }

    /** Every permission and the profile already in place (set up before first-run setup existed). */
    private fun alreadySetUp(): Boolean =
        serviceEnabled() && granted(Manifest.permission.RECORD_AUDIO) && granted(Manifest.permission.READ_CALENDAR) &&
            granted(photosPermission) && notificationsAllowed() && profileComplete()

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
