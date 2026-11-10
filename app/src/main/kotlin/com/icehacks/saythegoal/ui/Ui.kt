package com.icehacks.saythegoal.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.icehacks.saythegoal.R
import kotlin.math.roundToInt

/**
 * Shared look for the app's own screens. Built for people who can't tap precisely: every button is
 * full width and at least 64 dp tall, text is 18 sp or larger, nothing needs a swipe or is timed.
 * Colours come from res/values(-night)/colors.xml, so light and dark mode both work.
 */

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.themeColor(id: Int): Int = ContextCompat.getColor(this, id)

internal fun Context.roundedBox(fill: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(2), strokeColor)
    }

enum class ButtonKind { PRIMARY, SECONDARY, DANGER }

/** A full-width button, at least 64 dp tall, with a 20 sp label. */
internal fun Context.bigButton(label: CharSequence, kind: ButtonKind = ButtonKind.PRIMARY, onClick: () -> Unit): AppCompatButton {
    val fill: Int
    val ink: Int
    var stroke: Int? = null
    when (kind) {
        ButtonKind.PRIMARY -> { fill = themeColor(R.color.stg_primary); ink = themeColor(R.color.stg_on_primary) }
        ButtonKind.DANGER -> { fill = themeColor(R.color.stg_danger); ink = themeColor(R.color.stg_on_danger) }
        ButtonKind.SECONDARY -> {
            fill = themeColor(R.color.stg_surface); ink = themeColor(R.color.stg_primary); stroke = themeColor(R.color.stg_primary)
        }
    }
    val shape: Drawable = roundedBox(fill, 16, stroke)
    val pressed = ColorStateList.valueOf(Color.argb(70, Color.red(ink), Color.green(ink), Color.blue(ink)))
    return AppCompatButton(this).apply {
        this.text = label
        setAllCaps(false)
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ink)
        background = RippleDrawable(pressed, shape, null)
        stateListAnimator = null
        minHeight = dp(64)
        minimumHeight = dp(64)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        setOnClickListener { onClick() }
    }
}

internal fun Context.titleText(text: CharSequence, sizeSp: Float = 30f): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(themeColor(R.color.stg_text))
    ViewCompat.setAccessibilityHeading(this, true)
}

internal fun Context.bodyText(text: CharSequence, sizeSp: Float = 18f, secondary: Boolean = true): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    setLineSpacing(0f, 1.15f)
    setTextColor(themeColor(if (secondary) R.color.stg_text_secondary else R.color.stg_text))
}

/** A card: surface colour, rounded corners, an outline so it stands out without relying on shadow. */
internal fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBox(themeColor(R.color.stg_surface), 20, themeColor(R.color.stg_outline))
    setPadding(dp(20), dp(20), dp(20), dp(20))
}

/** Adds [child] full width with [topDp] of space above it. */
internal fun LinearLayout.addSpaced(child: View, topDp: Int = 0) {
    val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
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
        setPadding(dp(20), dp(24), dp(20), dp(32))
    }
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(themeColor(R.color.stg_background))
        addView(column, MATCH_PARENT, WRAP_CONTENT)
    }
    ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
    return scroll to column
}
