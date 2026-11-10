package com.icehacks.suus.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.icehacks.suus.core.Bounds
import com.icehacks.suus.core.CharacterMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * One full-screen, untouchable view that draws everything: highlight rings, the SuuS robot, its
 * mode badge, the result mark and the pixel speech-bubble caption. All coordinates are screen px;
 * [originX]/[originY] convert to view px. Mutate only on the main thread, then invalidate().
 */
internal class StageView(context: Context) : View(context) {
    private val dp = context.resources.displayMetrics.density

    /** The user's font size setting, capped so a 3-line caption still fits on a phone. */
    private val fontScale = context.resources.configuration.fontScale.coerceIn(1f, 1.3f)

    /** Height of the robot. Placement treats it as a square of this size, so it never covers the target. */
    val characterSize = 88 * dp
    val gap = 12 * dp

    var mode = CharacterMode.HIDDEN
    var charX = Float.NaN
    var charY = Float.NaN
    var charAlpha = 1f
    var pulse = 0f                     // 0..1, loops while LISTENING or THINKING
    var frame = 0                      // walking frame, 0..7; 0 is the standing pose
    var popScale = 1f                  // < 1 while the robot pops in (overshoots past 1)
    var facingLeft = false             // mirror the robot when it walks left
    var focus: Bounds? = null          // ring while pointing
    var focusAlpha = 0f
    var highlights: List<Bounds> = emptyList()
    var caption: String? = null
    var heard: String? = null          // live "you said…" text from the microphone
    var heardAlpha = 1f
    var captionTop = false
    var result: Pair<Bounds, Boolean>? = null
    var screen = ScreenArea(1, 1, 0, 0)

    private var originX = 0f
    private var originY = 0f
    private val loc = IntArray(2)

    /** guru's SuuS sprite, 8 walking frames (res/drawable-nodpi/suus_walk_*.png). */
    private val frames: List<Bitmap> = FRAME_IDS.map { id ->
        BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })
    }
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val spriteRect = RectF()

    private val ringDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8 * dp; color = Color.argb(170, 0, 0, 0)
    }
    private val ringBright = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4 * dp }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * dp; color = Color.WHITE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 20 * dp; isFakeBoldText = true
    }
    private val captionText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BUBBLE_INK; textSize = 22 * dp * fontScale; isFakeBoldText = true
    }
    private val bubblePaint = Paint() // no anti-aliasing: keeps the pixel edges crisp
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
        val captionBox = caption?.takeIf { it.isNotBlank() && mode != CharacterMode.HIDDEN }?.let { captionBubble(canvas, it) }
        heard?.takeIf { it.isNotBlank() && mode != CharacterMode.HIDDEN }?.let { heardBubble(canvas, it, captionBox) }
    }

    private val heardText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BUBBLE_INK; textSize = 20 * dp * fontScale
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC)
    }

    /**
     * What the user is saying, live, in the same pixel-bubble style but mint, right-aligned, with
     * a speaker mark: the user's side of the conversation. Sits next to the caption bubble.
     */
    private fun heardBubble(c: Canvas, text: String, captionBox: RectF?) {
        val s = (3 * dp).toInt().coerceAtLeast(2).toFloat()
        val margin = 16 * dp
        val pad = 12 * dp + 2 * s
        val maxW = (screen.width * 0.8f - 2 * pad).toInt().coerceAtLeast(1)
        val shown = "\u201C" + text.trim() + "\u201D"
        val layout = StaticLayout.Builder.obtain(shown, 0, shown.length, heardText, maxW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.START) // keep the newest words
            .build()
        var w = 0f
        for (i in 0 until layout.lineCount) w = maxOf(w, layout.getLineWidth(i))
        val bw = w + 2 * pad
        val bh = layout.height + 2 * pad
        val gapY = 10 * dp
        val top = when {
            captionBox == null -> screen.safeBottom - margin - bh - 90 * dp
            captionTop -> captionBox.bottom + gapY + 4 * s
            else -> captionBox.top - gapY - bh
        }
        val box = RectF(screen.width - margin - bw, top, screen.width - margin, top + bh)
        val a = (heardAlpha * 255).toInt()
        bubblePaint.alpha = 255
        bubblePaint.color = BUBBLE_OUTLINE; bubblePaint.alpha = a
        c.drawPath(steppedBubble(box, s, 0f, Float.NaN, 0f), bubblePaint)
        bubblePaint.color = BUBBLE_FILL; bubblePaint.alpha = a
        c.drawPath(steppedBubble(box, s, s, Float.NaN, 0f), bubblePaint)
        bubblePaint.color = BUBBLE_MINT; bubblePaint.alpha = a
        c.drawPath(steppedBubble(box, s, 2 * s, Float.NaN, 0f), bubblePaint)
        bubblePaint.alpha = 255
        heardText.alpha = a
        c.save()
        c.translate(box.left + pad, box.top + pad)
        layout.draw(c)
        c.restore()
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

    // ---------- the robot ----------

    private fun character(c: Canvas) {
        val h = characterSize
        val bmp = frames[frame.mod(frames.size)]
        val w = h * bmp.width / bmp.height
        val a = (charAlpha * 255).toInt()
        // Listening: a soft pulse behind the robot. Thinking: a gentle bob.
        val bob = if (mode == CharacterMode.THINKING) sin(pulse * 2 * Math.PI.toFloat()) * 3 * dp else 0f
        val cx = charX
        val cy = charY + bob
        c.save()
        c.scale(popScale, popScale, cx, cy + h / 2) // grow up from its feet

        if (mode == CharacterMode.LISTENING) {
            fill.color = modeColor(mode)
            fill.alpha = ((1f - pulse) * 110).toInt()
            c.drawCircle(cx, cy, h * 0.5f * (0.9f + 0.4f * pulse), fill)
        }

        // Pointer toward the focused target, in the robot's colours.
        val f = focus
        if (f != null && focusAlpha > 0f) {
            val ang = atan2((f.top + f.bottom) / 2f - cy, (f.left + f.right) / 2f - cx)
            val base = w * 0.5f
            val tip = base + 18 * dp
            val p = Path().apply {
                moveTo(cx + cos(ang) * tip, cy + sin(ang) * tip)
                lineTo(cx + cos(ang + 0.32f) * base, cy + sin(ang + 0.32f) * base)
                lineTo(cx + cos(ang - 0.32f) * base, cy + sin(ang - 0.32f) * base)
                close()
            }
            fill.color = BUBBLE_OUTLINE; fill.alpha = (a * focusAlpha).toInt()
            c.drawPath(p, fill)
        }

        spriteRect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        spritePaint.alpha = a
        if (facingLeft) {
            c.save()
            c.scale(-1f, 1f, cx, cy)
            c.drawBitmap(bmp, null, spriteRect, spritePaint)
            c.restore()
        } else {
            c.drawBitmap(bmp, null, spriteRect, spritePaint)
        }
        badge(c, cx + w * 0.42f, cy - h * 0.30f, a)
        c.restore()
    }

    /**
     * A small round badge by the robot's head: its colour and its symbol both say the mode, so the
     * mode reads at a glance and without colour vision. Idle shows no badge.
     */
    private fun badge(c: Canvas, x: Float, y: Float, a: Int) {
        if (mode == CharacterMode.IDLE || mode == CharacterMode.HIDDEN) return
        val r = 15 * dp
        fill.color = modeColor(mode); fill.alpha = a
        c.drawCircle(x, y, r, fill)
        badgeEdge.alpha = a
        c.drawCircle(x, y, r, badgeEdge)
        fill.color = Color.WHITE; fill.alpha = a
        when (mode) {
            CharacterMode.LISTENING -> { // three bars rising and falling like a sound meter
                val bw = r * 0.22f
                for (i in -1..1) {
                    val bh = r * (0.32f + 0.2f * sin(pulse * 2 * Math.PI.toFloat() + i * 1.3f))
                    val bx = x + i * r * 0.42f
                    c.drawRoundRect(bx - bw / 2, y - bh, bx + bw / 2, y + bh, bw / 2, bw / 2, fill)
                }
            }
            CharacterMode.THINKING -> { // three dots, each lifting in turn
                val active = (pulse * 3).toInt().coerceIn(0, 2)
                for (i in 0..2) {
                    val up = i == active
                    c.drawCircle(x + (i - 1) * r * 0.45f, y - (if (up) r * 0.16f else 0f), r * (if (up) 0.17f else 0.13f), fill)
                }
            }
            CharacterMode.ACTING -> c.drawCircle(x, y, r * 0.32f, fill) // a "tap" dot
            CharacterMode.WAITING_APPROVAL -> c.drawText("?", x, y + 7 * dp, glyph)
            CharacterMode.PROBLEM -> c.drawText("!", x, y + 7 * dp, glyph)
            else -> {}
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

    // ---------- the caption: guru's pixel speech bubble ----------

    /**
     * Cream bubble, mint inner line, dark green outline, with stepped "pixel" corners and a small
     * stepped tail pointing toward the robot. Dark green text on cream is far above 7:1 contrast.
     */
    private fun captionBubble(c: Canvas, text: String): RectF {
        val s = (3 * dp).toInt().coerceAtLeast(2).toFloat() // one bubble "pixel"
        val margin = 16 * dp
        val pad = 14 * dp + 2 * s
        val width = (screen.width - 2 * margin - 2 * pad).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, captionText, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(if (text.contains('\n')) 6 else 3) // questions list their choices on extra lines
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        val h = layout.height + 2 * pad
        val tail = 4 * s
        val top = if (captionTop) screen.safeTop + margin else screen.safeBottom - margin - h
        val box = RectF(margin, top, screen.width - margin, top + h)

        // Tail toward the robot, if it's on screen; it sits on the bubble edge that faces it.
        val tailX = if (!charX.isNaN() && mode != CharacterMode.HIDDEN) {
            charX.coerceIn(box.left + 6 * s, box.right - 10 * s)
        } else Float.NaN

        bubblePaint.color = BUBBLE_OUTLINE
        c.drawPath(steppedBubble(box, s, 0f, tailX, tail), bubblePaint)
        bubblePaint.color = BUBBLE_MINT
        c.drawPath(steppedBubble(box, s, s, tailX, tail), bubblePaint)
        bubblePaint.color = BUBBLE_FILL
        c.drawPath(steppedBubble(box, s, 2 * s, tailX, tail), bubblePaint)

        c.save()
        c.translate(box.left + pad, box.top + pad)
        layout.draw(c)
        c.restore()
        return box
    }

    /**
     * A rectangle inset by [inset] with stepped corners (3 steps of [s]), plus a stepped tail of
     * [tail] px at [tailX] on the bottom edge (caption at top) or top edge (caption at bottom).
     */
    private fun steppedBubble(box: RectF, s: Float, inset: Float, tailX: Float, tail: Float): Path {
        val l = box.left + inset; val t = box.top + inset
        val r = box.right - inset; val b = box.bottom - inset
        val k = 3 * s
        val p = Path()
        p.moveTo(l + k, t)
        if (!tailX.isNaN() && !captionTop) tailEdge(p, tailX, t, -1f, s, tail, inset) // tail up from the top edge
        p.lineTo(r - k, t)
        p.lineTo(r - k, t + s); p.lineTo(r - 2 * s, t + s); p.lineTo(r - 2 * s, t + 2 * s)
        p.lineTo(r - s, t + 2 * s); p.lineTo(r - s, t + k); p.lineTo(r, t + k)
        p.lineTo(r, b - k)
        p.lineTo(r - s, b - k); p.lineTo(r - s, b - 2 * s); p.lineTo(r - 2 * s, b - 2 * s)
        p.lineTo(r - 2 * s, b - s); p.lineTo(r - k, b - s); p.lineTo(r - k, b)
        if (!tailX.isNaN() && captionTop) tailEdgeBackward(p, tailX, b, 1f, s, tail, inset) // tail down from the bottom edge
        p.lineTo(l + k, b)
        p.lineTo(l + k, b - s); p.lineTo(l + 2 * s, b - s); p.lineTo(l + 2 * s, b - 2 * s)
        p.lineTo(l + s, b - 2 * s); p.lineTo(l + s, b - k); p.lineTo(l, b - k)
        p.lineTo(l, t + k)
        p.lineTo(l + s, t + k); p.lineTo(l + s, t + 2 * s); p.lineTo(l + 2 * s, t + 2 * s)
        p.lineTo(l + 2 * s, t + s); p.lineTo(l + k, t + s)
        p.close()
        return p
    }

    /** Tail on the top edge, drawn left to right: a staircase out to a point and back. */
    private fun tailEdge(p: Path, x: Float, edge: Float, dir: Float, s: Float, tail: Float, inset: Float) {
        val len = tail - inset
        if (len <= 0) return
        val w = 3 * s - inset // inner layers are narrower, so the outline shows all round the tail
        p.lineTo(x - w, edge)
        p.lineTo(x - w, edge + dir * len * 0.5f); p.lineTo(x - w / 2, edge + dir * len * 0.5f)
        p.lineTo(x - w / 2, edge + dir * len); p.lineTo(x + w / 2, edge + dir * len)
        p.lineTo(x + w / 2, edge + dir * len * 0.5f); p.lineTo(x + w, edge + dir * len * 0.5f)
        p.lineTo(x + w, edge)
    }

    /** Tail on the bottom edge, drawn right to left (the path runs backwards there). */
    private fun tailEdgeBackward(p: Path, x: Float, edge: Float, dir: Float, s: Float, tail: Float, inset: Float) {
        val len = tail - inset
        if (len <= 0) return
        val w = 3 * s - inset // inner layers are narrower, so the outline shows all round the tail
        p.lineTo(x + w, edge)
        p.lineTo(x + w, edge + dir * len * 0.5f); p.lineTo(x + w / 2, edge + dir * len * 0.5f)
        p.lineTo(x + w / 2, edge + dir * len); p.lineTo(x - w / 2, edge + dir * len)
        p.lineTo(x - w / 2, edge + dir * len * 0.5f); p.lineTo(x - w, edge + dir * len * 0.5f)
        p.lineTo(x - w, edge)
    }

    companion object {
        val FOCUS = Color.rgb(255, 193, 7)        // amber: the control being used now
        val HIGHLIGHT = Color.rgb(0, 200, 255)    // cyan: read-back highlights

        // Sampled from guru's speech-bubble design.
        val BUBBLE_OUTLINE = Color.rgb(0x0A, 0x29, 0x13)
        val BUBBLE_MINT = Color.rgb(0x95, 0xCB, 0xA7)
        val BUBBLE_FILL = Color.rgb(0xFD, 0xF6, 0xED)
        val BUBBLE_INK = Color.rgb(0x0A, 0x29, 0x13)

        private val FRAME_IDS = listOf(
            R.drawable.suus_walk_0, R.drawable.suus_walk_1, R.drawable.suus_walk_2, R.drawable.suus_walk_3,
            R.drawable.suus_walk_4, R.drawable.suus_walk_5, R.drawable.suus_walk_6, R.drawable.suus_walk_7,
        )
        const val FRAME_COUNT = 8

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
