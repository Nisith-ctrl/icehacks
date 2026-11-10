package com.icehacks.suus.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.icehacks.suus.R
import kotlin.math.roundToInt

/**
 * Shared look for the app's own screens. Built for people who can't tap precisely: every button is
 * full width and at least 64 dp tall, text is 16 sp or larger, nothing needs a swipe or is timed.
 * Colours come from res/values(-night)/colors.xml, so light and dark mode both work.
 */

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.themeColor(id: Int): Int = ContextCompat.getColor(this, id)

internal fun Context.roundedBox(fill: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 2): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(strokeDp), strokeColor)
    }

// ---------- appearance (light / dark) ----------

/** The user's appearance choice. Stored in the app, so it works whatever the phone is set to. */
enum class ThemeChoice(val mode: Int, val label: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, R.string.theme_system),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO, R.string.theme_light),
    DARK(AppCompatDelegate.MODE_NIGHT_YES, R.string.theme_dark),
}

private const val UI_PREFS = "ui"
private const val KEY_THEME = "theme"

internal fun Context.savedTheme(): ThemeChoice =
    runCatching { ThemeChoice.valueOf(getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, null)!!) }
        .getOrDefault(ThemeChoice.SYSTEM)

/** Call before super.onCreate() in every activity. */
internal fun Context.applySavedTheme() {
    val mode = savedTheme().mode
    if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
}

/** Saves the choice and applies it; AppCompat recreates open screens in the new colours. */
internal fun Context.chooseTheme(choice: ThemeChoice) {
    getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, choice.name).apply()
    AppCompatDelegate.setDefaultNightMode(choice.mode)
}

// ---------- building blocks ----------

enum class ButtonKind { PRIMARY, TONAL, SECONDARY, DANGER }

/** A full-width pill button, at least 64 dp tall, with a 20 sp bold label. */
internal fun Context.bigButton(label: CharSequence, kind: ButtonKind = ButtonKind.PRIMARY, onClick: () -> Unit): AppCompatButton {
    val fill: Int
    val ink: Int
    var stroke: Int? = null
    when (kind) {
        ButtonKind.PRIMARY -> { fill = themeColor(R.color.suus_primary); ink = themeColor(R.color.suus_on_primary) }
        ButtonKind.TONAL -> { fill = themeColor(R.color.suus_primary_soft); ink = themeColor(R.color.suus_on_primary_soft) }
        ButtonKind.DANGER -> { fill = themeColor(R.color.suus_danger); ink = themeColor(R.color.suus_on_danger) }
        ButtonKind.SECONDARY -> {
            fill = themeColor(R.color.suus_surface); ink = themeColor(R.color.suus_primary); stroke = themeColor(R.color.suus_primary)
        }
    }
    val pressed = ColorStateList.valueOf(Color.argb(60, Color.red(ink), Color.green(ink), Color.blue(ink)))
    return AppCompatButton(this).apply {
        text = label
        setAllCaps(false)
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ink)
        background = RippleDrawable(pressed, roundedBox(fill, 32, stroke), null)
        stateListAnimator = null
        minHeight = dp(64)
        minimumHeight = dp(64)
        setPadding(dp(24), dp(12), dp(24), dp(12))
        setOnClickListener { onClick() }
    }
}

internal fun Context.titleText(text: CharSequence, sizeSp: Float = 30f, color: Int = R.color.suus_text): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(themeColor(color))
    ViewCompat.setAccessibilityHeading(this, true)
}

internal fun Context.bodyText(text: CharSequence, sizeSp: Float = 18f, secondary: Boolean = true): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    setLineSpacing(0f, 1.18f)
    setTextColor(themeColor(if (secondary) R.color.suus_text_secondary else R.color.suus_text))
}

/** Small uppercase heading above a group of cards ("SETUP", "APPEARANCE"). */
internal fun Context.sectionLabel(text: CharSequence): TextView = TextView(this).apply {
    this.text = text.toString().uppercase()
    textSize = 15f
    letterSpacing = 0.08f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(themeColor(R.color.suus_text_secondary))
    setPadding(dp(4), 0, 0, 0)
    ViewCompat.setAccessibilityHeading(this, true)
}

enum class ChipKind { DONE, NEXT, WAITING, INFO }

/** A rounded status label ("Done", "Do this next"). Colour is never the only signal: the words say it. */
internal fun Context.chip(): TextView = TextView(this).apply {
    textSize = 16f
    typeface = Typeface.DEFAULT_BOLD
    setPadding(dp(14), dp(6), dp(14), dp(6))
}

internal fun TextView.setChip(text: CharSequence, kind: ChipKind) {
    this.text = text
    val (bg, fg) = when (kind) {
        ChipKind.DONE -> R.color.suus_ok_soft to R.color.suus_ok
        ChipKind.NEXT -> R.color.suus_primary_soft to R.color.suus_on_primary_soft
        ChipKind.WAITING -> R.color.suus_pending_soft to R.color.suus_pending
        ChipKind.INFO -> R.color.suus_surface_alt to R.color.suus_text_secondary
    }
    background = context.roundedBox(context.themeColor(bg), 20)
    setTextColor(context.themeColor(fg))
}

/** A card: surface colour, large rounded corners, a soft outline so it stands out without shadow. */
internal fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBox(themeColor(R.color.suus_surface), 24, themeColor(R.color.suus_outline), 1)
    setPadding(dp(20), dp(20), dp(20), dp(20))
}

/**
 * The coloured header at the top of a screen: the character, a big title and a line under it.
 * White text on a blue-violet gradient (contrast above 7:1 at both ends).
 */
internal fun Context.heroHeader(title: CharSequence, subtitle: CharSequence): View {
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(themeColor(R.color.suus_hero_start), themeColor(R.color.suus_hero_end)),
        ).apply { cornerRadius = dp(28).toFloat() }
        setPadding(dp(24), dp(24), dp(24), dp(26))
    }
    box.addView(CharacterBadgeView(this), LinearLayout.LayoutParams(dp(72), dp(72)))
    box.addSpaced(titleText(title, 36f, R.color.suus_on_hero), 14)
    box.addSpaced(
        TextView(this).apply {
            text = subtitle
            textSize = 19f
            setLineSpacing(0f, 1.15f)
            setTextColor(themeColor(R.color.suus_on_hero))
        },
        6,
    )
    return box
}

/** The overlay character as a still picture: a round face with eyes, pointing right. Decorative. */
internal class CharacterBadgeView(context: Context) : View(context) {
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.suus_hero_start) }
    private val pointer = Path()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = s * 0.44f
        val cy = s / 2
        val r = s * 0.36f
        pointer.reset()
        pointer.moveTo(s * 0.98f, cy)
        pointer.lineTo(cx + r * 0.7f, cy - r * 0.55f)
        pointer.lineTo(cx + r * 0.7f, cy + r * 0.55f)
        pointer.close()
        canvas.drawPath(pointer, face)
        canvas.drawCircle(cx, cy, r, face)
        canvas.drawCircle(cx - r * 0.36f, cy - r * 0.1f, r * 0.15f, eye)
        canvas.drawCircle(cx + r * 0.36f, cy - r * 0.1f, r * 0.15f, eye)
    }
}

/** Adds [child] full width with [topDp] of space above it. */
internal fun LinearLayout.addSpaced(child: View, topDp: Int = 0) {
    val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    lp.topMargin = context.dp(topDp)
    addView(child, lp)
}

/** Adds [child] at its own width (chips) with [topDp] of space above it. */
internal fun LinearLayout.addWrapped(child: View, topDp: Int = 0) {
    val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    lp.topMargin = context.dp(topDp)
    addView(child, lp)
}

/**
 * A scrolling page with one column. Android 15 draws edge to edge, so the system bars are added
 * to the padding. Returns the scroll view (set it as the content view) and the column to fill.
 */
internal fun Context.page(): Pair<ScrollView, LinearLayout> {
    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(16), dp(16), dp(32))
    }
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(themeColor(R.color.suus_background))
        addView(column, MATCH_PARENT, WRAP_CONTENT)
    }
    ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
    return scroll to column
}

/** The appearance card: three full-width choices, the current one filled and marked. */
internal fun Context.appearanceCard(): View = card().apply {
    addSpaced(titleText(getString(R.string.theme_title), 22f))
    addSpaced(bodyText(getString(R.string.theme_body)), 6)
    val current = savedTheme()
    for ((i, choice) in ThemeChoice.entries.withIndex()) {
        val selected = choice == current
        val label = (if (selected) "✓  " else "") + getString(choice.label)
        val button = bigButton(label, if (selected) ButtonKind.PRIMARY else ButtonKind.SECONDARY) {
            if (!selected) chooseTheme(choice)
        }
        button.contentDescription = getString(if (selected) R.string.theme_selected else R.string.theme_choose, getString(choice.label))
        addSpaced(button, if (i == 0) 16 else 12)
    }
}
