package com.icehacks.suus.ui

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
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

/**
 * The app's two typefaces, bundled under assets/fonts (SIL Open Font License, licences alongside):
 * - **Atkinson Hyperlegible** for reading: drawn by the Braille Institute for low-vision readers, with
 *   letters that are easy to tell apart (I l 1, O 0, b d). Body text, fields, labels, chips.
 * - **Lexend** for headings and buttons: a warm, open sans designed to make reading easier. Variable,
 *   so each weight is cut from one file.
 */
internal object SuusFonts {
    private var body: Typeface? = null
    private var bodyBold: Typeface? = null
    private val headings = HashMap<Int, Typeface>()

    fun body(ctx: Context): Typeface =
        body ?: load { Typeface.createFromAsset(ctx.assets, "fonts/AtkinsonHyperlegible-Regular.ttf") }.also { body = it }

    fun bodyBold(ctx: Context): Typeface =
        bodyBold ?: load { Typeface.createFromAsset(ctx.assets, "fonts/AtkinsonHyperlegible-Bold.ttf") }.also { bodyBold = it }

    /** Lexend at [weight] (400 regular … 700 bold). */
    fun heading(ctx: Context, weight: Int = 600): Typeface = headings.getOrPut(weight) {
        load { Typeface.Builder(ctx.assets, "fonts/Lexend-Variable.ttf").setFontVariationSettings("'wght' $weight").build() }
    }

    private fun load(make: () -> Typeface?): Typeface = runCatching(make).getOrNull() ?: Typeface.DEFAULT
}

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
        // Primary actions end in an arrow, like a "Get started →" button: it says "this moves you on".
        // TalkBack reads the label without it.
        text = if (kind == ButtonKind.PRIMARY && !label.endsWith("→")) "$label  →" else label
        contentDescription = label
        setAllCaps(false)
        textSize = 20f
        typeface = SuusFonts.heading(this@bigButton, 600)
        letterSpacing = 0f
        setTextColor(ink)
        background = RippleDrawable(pressed, roundedBox(fill, 32, stroke), null)
        stateListAnimator = pressSpring()
        minHeight = dp(64)
        minimumHeight = dp(64)
        setPadding(dp(24), dp(12), dp(24), dp(12))
        setOnClickListener { onClick() }
    }
}

// ---------- motion ----------
// Framer Motion's ideas, done with Android's own animators: a spring-like ease for things arriving,
// a quick press-down with a small bounce back, items arriving one after another, layout changes that
// animate instead of jumping. Everything respects "Remove animations" (animator scale 0) in Settings.

/** Spring-like ease-out (fast start, soft landing), as in `type: "spring"` with no overshoot. */
internal val SPRING_OUT: Interpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)

/** True unless the user turned animations off (Settings > Accessibility > Remove animations). */
internal fun motionAllowed(): Boolean = ValueAnimator.areAnimatorsEnabled()

/** Press feedback: shrink to 97 % in 90 ms while held, spring back with a small overshoot on release. */
internal fun pressSpring(): StateListAnimator = StateListAnimator().apply {
    fun scale(to: Float, ms: Long, interp: Interpolator) = ObjectAnimator.ofPropertyValuesHolder(
        null as View?,
        PropertyValuesHolder.ofFloat(View.SCALE_X, to),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, to),
    ).apply { duration = ms; interpolator = interp }
    addState(intArrayOf(android.R.attr.state_pressed), scale(0.97f, 90, DecelerateInterpolator()))
    addState(intArrayOf(), scale(1f, 260, OvershootInterpolator(3f)))
}

/**
 * Children arrive one after another: each fades in and rises 16 dp, 45 ms after the previous one,
 * 320 ms each (like a staggered `initial={{opacity:0, y:16}} animate={{opacity:1, y:0}}`).
 */
internal fun ViewGroup.revealChildren(staggerMs: Long = 45) {
    if (!motionAllowed()) return
    val rise = context.dp(16).toFloat()
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        child.alpha = 0f
        child.translationY = rise
        child.animate().alpha(1f).translationY(0f)
            .setStartDelay(60 + i * staggerMs).setDuration(320).setInterpolator(SPRING_OUT).start()
    }
}

/** Show/hide and size changes inside [this] animate instead of jumping (like Framer's `layout`). */
internal fun ViewGroup.animateLayoutChanges() {
    if (!motionAllowed()) return
    layoutTransition = LayoutTransition().apply {
        enableTransitionType(LayoutTransition.CHANGING)
        setDuration(240)
        setInterpolator(LayoutTransition.CHANGING, SPRING_OUT)
        setInterpolator(LayoutTransition.CHANGE_APPEARING, SPRING_OUT)
        setInterpolator(LayoutTransition.CHANGE_DISAPPEARING, SPRING_OUT)
    }
}

internal fun Context.titleText(text: CharSequence, sizeSp: Float = 30f, color: Int = R.color.suus_text): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    typeface = SuusFonts.heading(this@titleText, 600)
    setTextColor(themeColor(color))
    ViewCompat.setAccessibilityHeading(this, true)
}

internal fun Context.bodyText(text: CharSequence, sizeSp: Float = 18f, secondary: Boolean = true): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    typeface = SuusFonts.body(this@bodyText)
    setLineSpacing(0f, 1.18f)
    setTextColor(themeColor(if (secondary) R.color.suus_text_secondary else R.color.suus_text))
}

/** Small uppercase heading above a group of cards ("SETUP", "APPEARANCE"). */
internal fun Context.sectionLabel(text: CharSequence): TextView = TextView(this).apply {
    this.text = text.toString().uppercase()
    textSize = 15f
    letterSpacing = 0.08f
    typeface = SuusFonts.bodyBold(this@sectionLabel)
    setTextColor(themeColor(R.color.suus_text_secondary))
    setPadding(dp(4), 0, 0, 0)
    ViewCompat.setAccessibilityHeading(this, true)
}

enum class ChipKind { DONE, NEXT, WAITING, INFO }

/** A rounded status label ("Done", "Do this next"). Colour is never the only signal: the words say it. */
internal fun Context.chip(): TextView = TextView(this).apply {
    textSize = 16f
    typeface = SuusFonts.bodyBold(this@chip)
    setPadding(dp(14), dp(6), dp(14), dp(6))
}

internal fun TextView.setChip(text: CharSequence, kind: ChipKind) {
    this.text = text
    val (bg, fg) = when (kind) {
        ChipKind.DONE -> R.color.suus_ok_soft to R.color.suus_ok
        ChipKind.NEXT -> R.color.suus_pending_soft to R.color.suus_pending // the robot's antenna yellow
        ChipKind.WAITING, ChipKind.INFO -> R.color.suus_surface_alt to R.color.suus_text_secondary
    }
    background = context.roundedBox(context.themeColor(bg), 20)
    setTextColor(context.themeColor(fg))
}

/**
 * A soft surface: large rounded corners, no outline, no shadow. Sections read as one flowing page
 * instead of a stack of boxes; the surface colour alone sets a group apart from the background.
 */
internal fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBox(themeColor(R.color.suus_surface), 28)
    setPadding(dp(22), dp(22), dp(22), dp(22))
}

/** Elevation with a tinted shadow. [liftDp] 2 for resting cards, 8 for the one that needs attention. */
internal fun View.softShadow(liftDp: Int) {
    elevation = context.dp(liftDp).toFloat()
    outlineProvider = ViewOutlineProvider.BACKGROUND
    val tint = context.themeColor(R.color.suus_shadow)
    outlineSpotShadowColor = tint
    outlineAmbientShadowColor = tint
}

/** Animates a card's lift (its z), e.g. when it becomes the next step. */
internal fun View.liftTo(liftDp: Int) {
    val z = context.dp(liftDp).toFloat()
    if (!motionAllowed()) { elevation = z; return }
    animate().z(z).setDuration(280).setInterpolator(SPRING_OUT).start()
}

/**
 * The header at the top of a screen, after Stripe's hero: a small eyebrow line, then one big two-tone
 * sentence ("SuuS." in ink, the subtitle in green), with a flowing colour ribbon behind the robot on
 * the right. The ribbon fades out before the text column, so all text sits on the plain surface
 * (ink 15:1, green 6.1:1 in light mode). The whole header reads as one sentence in TalkBack.
 */
internal fun Context.heroHeader(
    title: CharSequence,
    subtitle: CharSequence,
    eyebrow: CharSequence? = null,
    onBack: (() -> Unit)? = null,
    below: View? = null,
): View {
    val ink = themeColor(R.color.suus_text)
    val accent = themeColor(R.color.suus_headline_accent)
    val headline = SpannableStringBuilder().apply {
        append(title.toString().trimEnd('.', ' ') + ".", ForegroundColorSpan(ink), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(" ")
        append(subtitle, ForegroundColorSpan(accent), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val text = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (onBack != null) {
            // Back lives in the header: a pill at its own width, still 56 dp tall.
            addWrapped(bigButton(getString(R.string.back_arrow), ButtonKind.TONAL) { onBack() }.apply {
                contentDescription = getString(R.string.back)
                minHeight = dp(56); minimumHeight = dp(56)
                textSize = 18f
            })
            addSpaced(View(context), 18)
        }
        if (eyebrow != null) {
            addSpaced(TextView(context).apply {
                this.text = eyebrow.toString().uppercase()
                textSize = 14f
                letterSpacing = 0.1f
                typeface = SuusFonts.bodyBold(this@heroHeader)
                setTextColor(themeColor(R.color.suus_text_secondary))
            })
        }
        addSpaced(
            TextView(context).apply {
                this.text = headline
                textSize = 30f
                typeface = SuusFonts.heading(this@heroHeader, 700)
                letterSpacing = -0.02f
                setLineSpacing(0f, 1.06f)
                ViewCompat.setAccessibilityHeading(this, true)
            },
            if (eyebrow != null) 10 else 0,
        )
    }
    val top = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(text, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(suusSprite(), LinearLayout.LayoutParams(dp(84), dp(116)).apply { marginStart = dp(8) })
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = RibbonDrawable(this@heroHeader)
        setPadding(dp(28), dp(8), dp(16), dp(36))
        addSpaced(top)
        below?.let { addSpaced(it, 18) }
    }
}

/**
 * Adds a [heroHeader] edge to edge: it cancels the page's side padding (and the top padding when it is
 * the first thing on the page), so the ribbon runs to the screen edges and fades into the page.
 */
internal fun LinearLayout.addHero(hero: View, first: Boolean = true) {
    val gutter = context.dp(16)
    // An explicit width: LinearLayout doesn't widen MATCH_PARENT children for negative side margins.
    val lp = LinearLayout.LayoutParams(context.resources.displayMetrics.widthPixels, WRAP_CONTENT)
    lp.gravity = Gravity.START
    lp.leftMargin = -gutter
    lp.topMargin = if (first) -gutter else context.dp(8)
    clipToPadding = false // otherwise the page's padding clips the header's edges
    addView(hero, lp)
}

/**
 * Something the user could say, as their own speech bubble: right-aligned, mint, with one squarer
 * corner on the speaker's side, at most 86 % of the width so it reads as a message, not a box.
 */
internal fun Context.speechBubble(text: CharSequence): View {
    val r = dp(24).toFloat(); val tail = dp(6).toFloat()
    val bubble = TextView(this).apply {
        this.text = text
        textSize = 19f
        setLineSpacing(0f, 1.15f)
        typeface = SuusFonts.body(this@speechBubble)
        setTextColor(themeColor(R.color.suus_on_primary_soft))
        setPadding(dp(20), dp(14), dp(20), dp(15))
        maxWidth = (resources.displayMetrics.widthPixels * 0.86f).toInt()
        background = GradientDrawable().apply {
            setColor(themeColor(R.color.suus_primary_soft))
            // top-left, top-right, bottom-right (the speaker's corner), bottom-left
            cornerRadii = floatArrayOf(r, r, r, r, tail, tail, r, r)
        }
    }
    return LinearLayout(this).apply {
        gravity = Gravity.END
        addView(bubble, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    }
}

/** A quiet closing promise: one short centred line, with a small green check in front. */
internal fun Context.promiseLine(text: CharSequence): View = TextView(this).apply {
    val check = "✓  "
    this.text = SpannableStringBuilder().apply {
        append(check, ForegroundColorSpan(themeColor(R.color.suus_ok)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(text)
    }
    contentDescription = text
    textSize = 15f
    gravity = Gravity.CENTER
    setTextColor(themeColor(R.color.suus_text_secondary))
}

/** One row in a [listGroup]: a title, a line under it, and what tapping it does. */
internal class ListRow(val title: CharSequence, val body: CharSequence?, val onClick: () -> Unit)

/**
 * Several destinations in one rounded surface, separated by thin dividers, like a settings list.
 * Each row is tappable anywhere (at least 80 dp tall) and ends in a chevron; TalkBack reads it as a
 * button with its title and line.
 */
internal fun Context.listGroup(rows: List<ListRow>): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBox(themeColor(R.color.suus_surface), 28)
    clipToOutline = true
    outlineProvider = ViewOutlineProvider.BACKGROUND
    val ink = themeColor(R.color.suus_text)
    for ((i, row) in rows.withIndex()) {
        if (i > 0) addView(View(context).apply { setBackgroundColor(themeColor(R.color.suus_outline)) },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { marginStart = dp(22); marginEnd = dp(22) })
        val ripple = ColorStateList.valueOf(Color.argb(40, Color.red(ink), Color.green(ink), Color.blue(ink)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(80)
            setPadding(dp(22), dp(16), dp(18), dp(16))
            background = RippleDrawable(ripple, null, android.graphics.drawable.ColorDrawable(Color.WHITE))
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(row.title, row.body).joinToString(". ")
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.Button::class.java.name
                }
            }
            setOnClickListener { row.onClick() }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addSpaced(titleText(row.title, 21f).apply { ViewCompat.setAccessibilityHeading(this, false) })
                row.body?.let { addSpaced(bodyText(it, 16f), 3) }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "›"
                textSize = 30f
                setTextColor(themeColor(R.color.suus_text_secondary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(dp(12), 0, 0, dp(4))
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
}

/**
 * A rounded surface with four soft diagonal colour bands flowing up to the top-right corner (the
 * Stripe hero idea, in SuuS's colours). A left-to-right fade from the plain surface keeps the left
 * 55 % clean for text. Purely decorative.
 */
private class RibbonDrawable(ctx: Context) : Drawable() {
    private val radius = 0f
    private val surface = ctx.themeColor(R.color.suus_background)
    private val outline = ctx.themeColor(R.color.suus_outline)
    private val bands = intArrayOf(
        ctx.themeColor(R.color.suus_ribbon_1), ctx.themeColor(R.color.suus_ribbon_2),
        ctx.themeColor(R.color.suus_ribbon_3), ctx.themeColor(R.color.suus_ribbon_4),
    )
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dp(1).toFloat() }
    private val clip = Path()
    private val box = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        box.set(b)
        clip.reset(); clip.addRoundRect(box, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        fill.shader = null; fill.color = surface
        canvas.drawRect(box, fill)
        // Bands: each a wide stripe from the bottom edge up past the top-right corner.
        val w = b.width().toFloat(); val h = b.height().toFloat()
        val bandW = w * 0.16f
        for ((i, c) in bands.withIndex()) {
            val x0 = b.left + w * (0.50f + i * 0.12f)
            val p = Path().apply {
                moveTo(x0, b.bottom.toFloat())
                cubicTo(x0 + w * 0.10f, b.top + h * 0.55f, x0 + w * 0.22f, b.top + h * 0.25f, x0 + w * 0.40f, b.top.toFloat() - 1)
                lineTo(x0 + w * 0.40f + bandW, b.top.toFloat() - 1)
                cubicTo(x0 + w * 0.22f + bandW, b.top + h * 0.25f, x0 + w * 0.10f + bandW, b.top + h * 0.55f, x0 + bandW, b.bottom.toFloat())
                close()
            }
            val next = bands[(i + 1) % bands.size]
            fill.shader = LinearGradient(x0, b.bottom.toFloat(), x0 + w * 0.4f, b.top.toFloat(), c, next, Shader.TileMode.CLAMP)
            fill.alpha = 230
            canvas.drawPath(p, fill)
        }
        fill.alpha = 255
        // Fade from the plain surface: solid up to 58 %, gone by 80 %, so the text column never sits on colour.
        fill.shader = LinearGradient(
            b.left.toFloat(), 0f, b.right.toFloat(), 0f,
            intArrayOf(surface, surface, Color.argb(0, Color.red(surface), Color.green(surface), Color.blue(surface))),
            floatArrayOf(0f, 0.58f, 0.80f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(box, fill)
        // And from the bottom: the ribbon melts into the page instead of ending on an edge.
        fill.shader = LinearGradient(
            0f, b.top + h * 0.55f, 0f, b.bottom.toFloat(),
            intArrayOf(Color.argb(0, Color.red(surface), Color.green(surface), Color.blue(surface)), surface),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(box, fill)
        fill.shader = null
        canvas.restore()
    }

    override fun getOutline(outline: Outline) { outline.setRoundRect(bounds, radius) }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** guru's SuuS robot (the overlay's standing frame) as a still picture. Decorative. */
internal fun Context.suusSprite(): ImageView = ImageView(this).apply {
    setImageResource(com.icehacks.suus.overlay.R.drawable.suus_walk_0)
    scaleType = ImageView.ScaleType.FIT_CENTER
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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
internal fun Context.appearanceCard(): View =
    optionGroup(R.string.theme_title, R.string.theme_body, ThemeChoice.entries.map { Option(it, it.label) }, savedTheme()) { choice ->
        if (choice != savedTheme()) chooseTheme(choice) // recreates the screen in the new colours
    }
