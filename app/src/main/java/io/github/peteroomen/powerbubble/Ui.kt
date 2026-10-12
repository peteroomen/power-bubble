package io.github.peteroomen.powerbubble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import kotlin.math.roundToInt

object Palette {
    const val GLASS = 0xD918201D.toInt()
    /** Glass over other apps: dark enough that white glyphs read on anything */
    const val GLASS_TINT = 0x6B000000
    /** Glass over the blurred screen behind the menu */
    const val GLASS_TINT_LIGHT = 0x4D000000
    /** Behind the menu when the screen behind is blurred */
    const val SCRIM_LIGHT = 0x33000000
    const val STROKE = 0x24FFFFFF
    const val INK = 0xFFF3F5F4.toInt()
    const val MUTED = 0xA6F3F5F4.toInt()
    const val DARK = 0xFF0D1513.toInt()
    const val ACCENT = 0xFF5EE0B0.toInt()
    const val ACCENT_INK = 0xFF062619.toInt()
    const val DANGER = 0xFFFF8566.toInt()
    const val DANGER_INK = 0xFF2A0B02.toInt()
    const val SCRIM = 0x99050908.toInt()
    const val CARD = 0xFF1B2420.toInt()
    const val CARD_BUTTON = 0x14FFFFFF
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun circle(fill: Int, stroke: Int = 0, strokeWidth: Int = 0) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(fill)
    if (strokeWidth > 0) setStroke(strokeWidth, stroke)
}

fun rounded(fill: Int, radius: Float) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radius
}

/**
 * A glass surface for views floating over other apps, drawn over whatever real blur sits behind it
 * (see [Look]): a see-through [tint], an optional glow along the top ([glowAlpha], [glowDepth] deep),
 * an optional hairline rim catching light at the top-left and bottom-right ([rimWidth], brightened by
 * [rimBoost]), and an optional soft light just inside the edge ([edgeGlow]). A circle when
 * [cornerRadius] is null, otherwise a rounded rectangle.
 */
class GlassDrawable(
    private val cornerRadius: Float?,
    private val rimWidth: Float,
    private val tint: Int = Palette.GLASS_TINT,
    private val glowAlpha: Float = 0.10f,
    private val glowDepth: Float? = null,
    private val rimBoost: Float = 1f,
    private val edgeGlow: Float = 0f,
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidth
    }
    private val rect = RectF()
    private var alpha = 255

    override fun onBoundsChange(bounds: Rect) {
        val depth = glowDepth ?: (bounds.height() * 0.35f)
        glow.shader = LinearGradient(
            0f, bounds.top.toFloat(), 0f, bounds.top + depth,
            white(glowAlpha), white(0f), Shader.TileMode.CLAMP,
        )
        rim.shader = LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
            intArrayOf(white(0.55f * rimBoost), white(0.12f * rimBoost), white(0.06f * rimBoost), white(0.32f * rimBoost)),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        edge.strokeWidth = minOf(bounds.width(), bounds.height()) * 0.16f
        edge.color = white(edgeGlow)
        edge.maskFilter = if (edgeGlow > 0f) android.graphics.BlurMaskFilter(edge.strokeWidth / 2, android.graphics.BlurMaskFilter.Blur.NORMAL) else null
    }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val r = cornerRadius ?: (minOf(rect.width(), rect.height()) / 2)
        fill.color = tint
        fill.alpha = Color.alpha(tint) * alpha / 255
        glow.alpha = alpha
        rim.alpha = alpha
        edge.alpha = (255 * edgeGlow * alpha / 255).toInt()
        canvas.drawRoundRect(rect, r, r, fill)
        if (glowAlpha > 0f) canvas.drawRoundRect(rect, r, r, glow)
        if (edgeGlow > 0f) {
            canvas.save()
            val path = android.graphics.Path().apply { addRoundRect(rect, r, r, android.graphics.Path.Direction.CW) }
            canvas.clipPath(path)
            canvas.drawRoundRect(rect, r, r, edge)
            canvas.restore()
        }
        if (rimWidth > 0f) {
            val half = rimWidth / 2
            rect.inset(half, half)
            canvas.drawRoundRect(rect, r - half, r - half, rim)
        }
    }

    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private fun white(a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)
}
