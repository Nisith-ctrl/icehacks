package com.icehacks.suus.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.icehacks.suus.R
import com.icehacks.suus.SuusAccessibilityService
import com.icehacks.suus.agent.Detail
import com.icehacks.suus.agent.Profile
import com.icehacks.suus.agent.ProfileStore
import com.icehacks.suus.agent.RobotSize
import com.icehacks.suus.agent.SuusSettings
import com.icehacks.suus.agent.SuusVoiceLines
import com.icehacks.suus.agent.Tone
import com.icehacks.suus.agent.VoiceSpeed
import com.icehacks.suus.agent.createSettingsStore
import com.icehacks.suus.overlay.OverlayStyle
import com.icehacks.suus.voice.VoiceStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shared pieces of first-run setup and "Make SuuS yours": the settings choices, the "About you"
 * form, and applying the choices to the running voice and robot.
 */

/** Pushes the saved voice speed and robot size to the voice and overlay (same process as the service). */
internal fun Context.applySuusStyle(settings: SuusSettings = createSettingsStore(this).load()) {
    VoiceStyle.speechRate = VoiceStyle.DEFAULT_RATE * settings.speed.rate
    OverlayStyle.robotScale = settings.robotSize.scale
    OverlayStyle.captionTypeface = SuusFonts.bodyBold(this) // captions are read mid-task: legibility first
    OverlayStyle.heardTypeface = Typeface.create(SuusFonts.body(this), Typeface.ITALIC)
}

/** One choice in an option group: a label, an optional line under it, and its value. */
internal class Option<T>(val value: T, val label: Int, val example: Int? = null)

/**
 * A titled group of big, full-width choices (one selected), each at least 72 dp tall. TalkBack reads
 * each row as a radio button ("Calm and gentle, selected"). Choosing one calls [onPick] at once.
 */
internal fun <T> Context.optionGroup(title: Int, body: Int?, options: List<Option<T>>, selected: T, onPick: (T) -> Unit): LinearLayout =
    card().apply {
        addSpaced(titleText(getString(title), 22f))
        body?.let { addSpaced(bodyText(getString(it)), 6) }
        val rows = mutableListOf<Pair<Option<T>, LinearLayout>>()
        fun paint(current: T) {
            for ((opt, row) in rows) {
                val on = opt.value == current
                row.background = roundedBox(themeColor(if (on) R.color.suus_primary_soft else R.color.suus_surface_alt), 20)
                (row.getChildAt(0) as TextView).apply {
                    text = if (on) "✓" else ""
                    background = roundedBox(themeColor(if (on) R.color.suus_primary else R.color.suus_surface), 16,
                        themeColor(if (on) R.color.suus_primary else R.color.suus_outline), 2)
                }
                row.contentDescription = getString(if (on) R.string.option_selected else R.string.option_choose, getString(opt.label))
            }
        }
        for ((i, opt) in options.withIndex()) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(72)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                isClickable = true
                isFocusable = true
                stateListAnimator = pressSpring()
                addView(TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 18f
                    typeface = SuusFonts.bodyBold(context)
                    setTextColor(themeColor(R.color.suus_on_primary))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(32), dp(32)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    addSpaced(titleText(getString(opt.label), 20f).apply { ViewCompatHeading.off(this) })
                    opt.example?.let { addSpaced(bodyText(getString(it), 16f), 2) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
                accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = RadioButton::class.java.name
                        info.isCheckable = true
                        info.isChecked = host.getTag(R.id.suus_option_checked) == true
                    }
                }
                setOnClickListener {
                    paint(opt.value)
                    rows.forEach { (o, r) -> r.setTag(R.id.suus_option_checked, o.value == opt.value) }
                    onPick(opt.value)
                }
                setTag(R.id.suus_option_checked, opt.value == selected)
            }
            rows += opt to row
            addSpaced(row, if (i == 0) 16 else 8)
        }
        paint(selected)
    }

/** Titles inside option rows aren't headings (the group title is). */
private object ViewCompatHeading {
    fun off(v: View) = androidx.core.view.ViewCompat.setAccessibilityHeading(v, false)
}

/**
 * The "Make SuuS yours" choices: tone, how much to explain, voice speed, robot size, colours.
 * Each change is saved and applied immediately; [onSaved] lets the screen confirm it.
 */
internal fun Context.customizeSections(column: LinearLayout, scope: CoroutineScope, onSaved: () -> Unit) {
    val store = createSettingsStore(this)
    fun update(change: (SuusSettings) -> SuusSettings) {
        val next = change(store.load())
        store.save(next)
        applySuusStyle(next)
        onSaved()
    }
    val s = store.load()
    column.addSpaced(optionGroup(R.string.tone_title, null, listOf(
        Option(Tone.FRIENDLY, R.string.tone_friendly, R.string.tone_friendly_ex),
        Option(Tone.CALM, R.string.tone_calm, R.string.tone_calm_ex),
        Option(Tone.BRIEF, R.string.tone_brief, R.string.tone_brief_ex),
    ), s.tone) { t -> update { it.copy(tone = t) } }, 16)
    column.addSpaced(optionGroup(R.string.detail_title, null, listOf(
        Option(Detail.QUIET, R.string.detail_quiet, R.string.detail_quiet_ex),
        Option(Detail.MAIN_STEPS, R.string.detail_main, R.string.detail_main_ex),
        Option(Detail.EVERY_STEP, R.string.detail_every, R.string.detail_every_ex),
    ), s.detail) { d -> update { it.copy(detail = d) } }, 16)
    column.addSpaced(optionGroup(R.string.speed_title, null, listOf(
        Option(VoiceSpeed.SLOWER, R.string.speed_slower),
        Option(VoiceSpeed.NORMAL, R.string.speed_normal),
        Option(VoiceSpeed.FASTER, R.string.speed_faster),
    ), s.speed) { v -> update { it.copy(speed = v) } }, 16)
    // Hear the chosen tone at the chosen speed, through the running service's voice.
    column.addSpaced(bigButton(getString(R.string.custom_try), ButtonKind.TONAL) {
        val voice = SuusAccessibilityService.instance?.voice
        if (voice == null) {
            column.announceForAccessibility(getString(R.string.custom_try_unavailable))
            android.widget.Toast.makeText(this, R.string.custom_try_unavailable, android.widget.Toast.LENGTH_LONG).show()
        } else {
            val now = store.load()
            scope.launch { voice.say(SuusVoiceLines.acks(now.tone).first() + " " + sampleLine(now.tone)) }
        }
    }, 12)
    column.addSpaced(optionGroup(R.string.robot_title, R.string.robot_body, listOf(
        Option(RobotSize.SMALL, R.string.robot_small),
        Option(RobotSize.MEDIUM, R.string.robot_medium),
        Option(RobotSize.LARGE, R.string.robot_large),
    ), s.robotSize) { r -> update { it.copy(robotSize = r) } }, 16)
    column.addSpaced(appearanceCard(), 16)
}

private fun sampleLine(tone: Tone) = when (tone) {
    Tone.FRIENDLY -> "All done! You're signed up for Friday afternoon."
    Tone.CALM -> "Done. You're signed up for Friday afternoon."
    Tone.BRIEF -> "Done. Friday, 2 PM."
}

/** The "About you" fields (name, email, phone) with autofill hints; [save] validates and stores them. */
internal class ProfileForm(private val context: Context, private val store: ProfileStore) {
    lateinit var name: EditText
    lateinit var email: EditText
    lateinit var phone: EditText

    fun build(): LinearLayout = context.card().apply {
        val p = store.load()
        name = field(this, R.string.profile_name, p.fullName,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            View.AUTOFILL_HINT_NAME, first = true)
        email = field(this, R.string.profile_email, p.email,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, View.AUTOFILL_HINT_EMAIL_ADDRESS)
        phone = field(this, R.string.profile_phone, p.phone, InputType.TYPE_CLASS_PHONE, View.AUTOFILL_HINT_PHONE)
        phone.imeOptions = EditorInfo.IME_ACTION_DONE
    }

    /** Null when saved; otherwise the message to show (and the bad field gets focus). */
    fun save(): String? {
        val emailText = email.text.toString().trim()
        if (emailText.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(emailText).matches()) {
            email.requestFocus()
            return context.getString(R.string.profile_bad_email)
        }
        store.save(Profile(name.text.toString().trim(), emailText, phone.text.toString().trim()))
        return null
    }

    private fun field(parent: LinearLayout, label: Int, value: String, type: Int, hint: String, first: Boolean = false): EditText =
        with(context) {
            val title = titleText(getString(label), 20f)
            val box = EditText(this).apply {
                setText(value)
                typeface = SuusFonts.body(this@with)
                inputType = type
                textSize = 22f
                minHeight = dp(64)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setTextColor(themeColor(R.color.suus_text))
                setHintTextColor(themeColor(R.color.suus_text_secondary))
                this.hint = getString(R.string.profile_not_set)
                background = roundedBox(themeColor(R.color.suus_surface_alt), 16, themeColor(R.color.suus_outline), 2)
                setAutofillHints(hint)
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                imeOptions = EditorInfo.IME_ACTION_NEXT
            }
            title.labelFor = View.generateViewId().also { box.id = it }
            parent.addSpaced(title, if (first) 0 else 20)
            parent.addSpaced(box, 8)
            box
        }
}
