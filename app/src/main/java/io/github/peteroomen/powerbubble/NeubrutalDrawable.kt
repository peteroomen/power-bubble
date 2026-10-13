package io.github.peteroomen.powerbubble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

const val BRUTAL_INK: Int = 0xFF171714.toInt()

fun Context.neubrutalFor(fill: Int? = null): Drawable {
    val prefs = Prefs(this)
    return NeubrutalDrawable(fill ?: prefs.brutalFill.color,
        if (prefs.brutalRounded) dp(12).toFloat() else 0f, dp(2).toFloat(), dp(4).toFloat())
}

/** Flat fill, ink outline and hard offset shadow within the existing overlay bounds. */
class NeubrutalDrawable(
    private val fillColor: Int, private val radius: Float, private val strokeWidth: Float, private val offset: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var opacity = 255
    override fun draw(canvas: Canvas) {
        val half = strokeWidth / 2f
        rect.set(bounds.left + half, bounds.top + half, bounds.right - offset - half, bounds.bottom - offset - half)
        paint.style = Paint.Style.FILL; paint.color = BRUTAL_INK; paint.alpha = opacity
        rect.offset(offset, offset); canvas.drawRoundRect(rect, radius, radius, paint); rect.offset(-offset, -offset)
        paint.color = fillColor; paint.alpha = Color.alpha(fillColor) * opacity / 255
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = strokeWidth; paint.color = BRUTAL_INK; paint.alpha = opacity
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
