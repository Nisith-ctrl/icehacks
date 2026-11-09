package com.icehacks.saythegoal.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.icehacks.saythegoal.core.Bounds
import com.icehacks.saythegoal.core.CharacterMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * One full-screen, untouchable view that draws everything: highlight rings, the character, the
 * result mark and the caption. All coordinates are screen px; [originX]/[originY] convert to view px.
 * Mutate only on the main thread, then invalidate().
 */
internal class StageView(context: Context) : View(context) {
    private val dp = context.resources.displayMetrics.density

    val characterSize = 64 * dp
    val gap = 12 * dp

    var mode = CharacterMode.HIDDEN
    var charX = Float.NaN
    var charY = Float.NaN
    var charAlpha = 1f
    var pulse = 0f                     // 0..1, LISTENING ring animation
    var focus: Bounds? = null          // ring while pointing
    var focusAlpha = 0f
    var highlights: List<Bounds> = emptyList()
    var caption: String? = null
    var captionTop = false
    var result: Pair<Bounds, Boolean>? = null
    var screen = ScreenArea(1, 1, 0, 0)

    private var originX = 0f
    private var originY = 0f
    private val loc = IntArray(2)

    private val ringDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8 * dp; color = Color.argb(170, 0, 0, 0)
    }
    private val ringBright = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4 * dp }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * dp; color = Color.WHITE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 30 * dp; isFakeBoldText = true
    }
    private val captionText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 22 * dp; isFakeBoldText = true
    }
    private val captionBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 16, 16, 24) }
    private val captionEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * dp; color = Color.argb(255, 255, 193, 7)
    }
    private val markLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 7 * dp; color = Color.WHITE; strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        getLocationOnScreen(loc)
        originX = loc[0].toFloat()
        originY = loc[1].toFloat()
        canvas.translate(-originX, -originY)

        for (b in highlights) ring(canvas, b, HIGHLIGHT, 255)
        focus?.let { ring(canvas, it, FOCUS, (focusAlpha * 255).toInt()) }
        result?.let { (b, ok) -> mark(canvas, b, ok) }
        if (mode != CharacterMode.HIDDEN && !charX.isNaN()) character(canvas)
        caption?.takeIf { it.isNotBlank() && mode != CharacterMode.HIDDEN }?.let { captionBox(canvas, it) }
    }

    private fun ring(c: Canvas, b: Bounds, color: Int, alpha: Int) {
        val pad = 6 * dp
        val r = RectF(b.left - pad, b.top - pad, b.right + pad, b.bottom + pad)
        val radius = 12 * dp
        ringDark.alpha = (alpha * 0.67f).toInt()
        c.drawRoundRect(r, radius, radius, ringDark)
        ringBright.color = color
        ringBright.alpha = alpha
        c.drawRoundRect(r, radius, radius, ringBright)
    }

    private fun character(c: Canvas) {
        val r = characterSize / 2
        val color = modeColor(mode)
        val a = (charAlpha * 255).toInt()

        if (mode == CharacterMode.LISTENING) {
            fill.color = color
            fill.alpha = ((1f - pulse) * 120).toInt()
            c.drawCircle(charX, charY, r * (1f + 0.45f * pulse), fill)
        }

        // Pointer toward the focused target.
        val f = focus
        if (f != null && focusAlpha > 0f) {
            val ang = atan2((f.top + f.bottom) / 2f - charY, (f.left + f.right) / 2f - charX)
            val tip = r + 14 * dp
            val p = Path().apply {
                moveTo(charX + cos(ang) * tip, charY + sin(ang) * tip)
                lineTo(charX + cos(ang + 0.45f) * r * 0.9f, charY + sin(ang + 0.45f) * r * 0.9f)
                lineTo(charX + cos(ang - 0.45f) * r * 0.9f, charY + sin(ang - 0.45f) * r * 0.9f)
                close()
            }
            fill.color = color; fill.alpha = a
            c.drawPath(p, fill)
        }

        fill.color = color; fill.alpha = a
        c.drawCircle(charX, charY, r, fill)
        stroke.alpha = a
        c.drawCircle(charX, charY, r, stroke)

        when (mode) {
            CharacterMode.THINKING -> c.drawText("…", charX, charY + 6 * dp, glyph)
            CharacterMode.WAITING_APPROVAL -> c.drawText("?", charX, charY + 11 * dp, glyph)
            CharacterMode.PROBLEM -> c.drawText("!", charX, charY + 11 * dp, glyph)
            else -> eyes(c, r)
        }
    }

    /** Two eyes; pupils look at the focused target, which reads as "I'm pointing at this". */
    private fun eyes(c: Canvas, r: Float) {
        val f = focus
        val ang = if (f != null) atan2((f.top + f.bottom) / 2f - charY, (f.left + f.right) / 2f - charX) else Math.PI.toFloat() / 2
        val look = if (f != null) 4 * dp else 0f
        for (side in listOf(-1, 1)) {
            val ex = charX + side * r * 0.36f
            val ey = charY - r * 0.12f
            fill.color = Color.WHITE; fill.alpha = 255
            c.drawCircle(ex, ey, r * 0.22f, fill)
            fill.color = Color.rgb(20, 20, 30)
            c.drawCircle(ex + cos(ang) * look, ey + sin(ang) * look, r * 0.11f, fill)
        }
    }

    private fun mark(c: Canvas, b: Bounds, ok: Boolean) {
        val cx = (b.left + b.right) / 2f
        val cy = (b.top + b.bottom) / 2f
        val r = 26 * dp
        fill.color = if (ok) Color.rgb(46, 160, 67) else Color.rgb(207, 34, 46)
        fill.alpha = 240
        c.drawCircle(cx, cy, r, fill)
        val p = Path()
        if (ok) {
            p.moveTo(cx - r * 0.45f, cy); p.lineTo(cx - r * 0.1f, cy + r * 0.35f); p.lineTo(cx + r * 0.5f, cy - r * 0.35f)
        } else {
            p.moveTo(cx - r * 0.4f, cy - r * 0.4f); p.lineTo(cx + r * 0.4f, cy + r * 0.4f)
            p.moveTo(cx + r * 0.4f, cy - r * 0.4f); p.lineTo(cx - r * 0.4f, cy + r * 0.4f)
        }
        c.drawPath(p, markLine)
    }

    private fun captionBox(c: Canvas, text: String) {
        val margin = 16 * dp
        val pad = 14 * dp
        val width = (screen.width - 2 * margin - 2 * pad).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, captionText, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(3)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        val h = layout.height + 2 * pad
        val top = if (captionTop) screen.safeTop + margin else screen.safeBottom - margin - h
        val box = RectF(margin, top, screen.width - margin, top + h)
        c.drawRoundRect(box, 16 * dp, 16 * dp, captionBg)
        c.drawRoundRect(box, 16 * dp, 16 * dp, captionEdge)
        c.save()
        c.translate(box.left + pad, box.top + pad)
        layout.draw(c)
        c.restore()
    }

    companion object {
        val FOCUS = Color.rgb(255, 193, 7)        // amber: the control being used now
        val HIGHLIGHT = Color.rgb(0, 200, 255)    // cyan: read-back highlights

        fun modeColor(m: CharacterMode): Int = when (m) {
            CharacterMode.LISTENING -> Color.rgb(46, 160, 67)
            CharacterMode.THINKING -> Color.rgb(191, 135, 0)
            CharacterMode.ACTING -> Color.rgb(9, 105, 218)
            CharacterMode.WAITING_APPROVAL -> Color.rgb(130, 80, 223)
            CharacterMode.PROBLEM -> Color.rgb(207, 34, 46)
            CharacterMode.IDLE, CharacterMode.HIDDEN -> Color.rgb(87, 96, 106)
        }
    }
}
