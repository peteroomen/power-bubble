package io.github.peteroomen.powerbubble

import android.content.Context
import android.graphics.Canvas
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
 * The launcher's liquid glass look, for views floating over other apps: a dark see-through tint, a
 * faint glow along the top, and a hairline rim that catches light at the top-left and bottom-right.
 * A circle when [cornerRadius] is null, otherwise a rounded rectangle.
 */
class GlassDrawable(
    private val cornerRadius: Float?,
    private val rimWidth: Float,
    tint: Int = Palette.GLASS_TINT,
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidth
    }
    private val rect = RectF()

    override fun onBoundsChange(bounds: Rect) {
        glow.shader = LinearGradient(
            0f, bounds.top.toFloat(), 0f, bounds.top + bounds.height() * 0.35f,
            0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP,
        )
        rim.shader = LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
            intArrayOf(0x8CFFFFFF.toInt(), 0x1FFFFFFF, 0x0FFFFFFF, 0x52FFFFFF),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val r = cornerRadius ?: (minOf(rect.width(), rect.height()) / 2)
        canvas.drawRoundRect(rect, r, r, fill)
        canvas.drawRoundRect(rect, r, r, glow)
        val half = rimWidth / 2
        rect.inset(half, half)
        canvas.drawRoundRect(rect, r - half, r - half, rim)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
