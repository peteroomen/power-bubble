package io.github.peteroomen.powerbubble

import android.content.Context
import android.graphics.drawable.GradientDrawable
import kotlin.math.roundToInt

object Palette {
    const val GLASS = 0xD918201D.toInt()
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
