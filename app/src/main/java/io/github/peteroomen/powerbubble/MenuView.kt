package io.github.peteroomen.powerbubble

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen menu: a dimmed scrim, a copy of the bubble, and Screenshot / Screen off / Restart / Shut down
 * fanned out toward the middle of the screen. Restart and Shut down ask first when they'll act
 * immediately (Shizuku); otherwise the system power menu does the asking.
 */
@SuppressLint("ViewConstructor")
class MenuView(
    context: Context,
    private val look: Look,
    /** The screen behind is blurred, so the glass can be lighter and the scrim fainter. */
    private val blurred: Boolean,
    private val center: PointF,
    private val onLeft: Boolean,
    private val screen: Point,
    private val confirmFirst: Boolean,
    private val listener: Listener,
) : FrameLayout(context) {

    interface Listener {
        fun onDismiss()
        fun onScreenOff()
        fun onScreenshot()
        fun onRestart()
        fun onShutdown()
    }

    private enum class Style { NORMAL, PRIMARY, DANGER }

    private class Item(val view: View, val circleSize: Int, val target: PointF)

    private val itemWidth = context.dp(88)
    private val radius = context.dp(108)
    private val items = mutableListOf<Item>()
    private var card: View? = null
    private val ink: Int get() = if (look == Look.NEUBRUTAL) BRUTAL_INK else Palette.INK

    init {
        setBackgroundColor(if (blurred) Palette.SCRIM_LIGHT else Palette.SCRIM)
        setOnClickListener { if (card != null) hideCard() else listener.onDismiss() }
        addView(closeButton())

        val targets = targets()
        addItem(R.drawable.ic_screenshot, R.string.screenshot, Style.NORMAL, targets[0]) { listener.onScreenshot() }
        addItem(R.drawable.ic_moon, R.string.screen_off, Style.PRIMARY, targets[1]) { listener.onScreenOff() }
        addItem(R.drawable.ic_restart, R.string.restart, Style.NORMAL, targets[2]) {
            if (confirmFirst) {
                showCard(R.string.restart_title, R.string.restart_body, R.string.restart, danger = false) { listener.onRestart() }
            } else {
                listener.onRestart()
            }
        }
        addItem(R.drawable.ic_power, R.string.shut_down, Style.DANGER, targets[3]) {
            if (confirmFirst) {
                showCard(R.string.shutdown_title, R.string.shutdown_body, R.string.shutdown_go, danger = true) { listener.onShutdown() }
            } else {
                listener.onShutdown()
            }
        }
    }

    fun animateIn() {
        alpha = 0f
        animate().alpha(1f).setDuration(150).start()
        items.forEachIndexed { i, item ->
            item.view.apply {
                translationX = center.x - itemWidth / 2f
                translationY = center.y - item.circleSize / 2f
                scaleX = 0.3f
                scaleY = 0.3f
                alpha = 0f
                animate()
                    .translationX(item.target.x - itemWidth / 2f)
                    .translationY(item.target.y - item.circleSize / 2f)
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setStartDelay(i * 35L)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
            }
        }
    }

    /** Angles fan away from the bubble's edge, turning so nothing lands off screen. */
    private fun targets(): List<PointF> {
        val nearTop = center.y < context.dp(170)
        val nearBottom = center.y > screen.y - context.dp(170)
        var degrees = when {
            nearTop -> listOf(180.0, 210.0, 240.0, 270.0)
            nearBottom -> listOf(90.0, 120.0, 150.0, 180.0)
            else -> listOf(120.0, 160.0, 200.0, 240.0)
        }
        if (onLeft) degrees = degrees.map { 180.0 - it }
        return degrees.map { d ->
            val r = Math.toRadians(d)
            PointF(
                (center.x + radius * cos(r)).toFloat().coerceIn(itemWidth / 2f, screen.x - itemWidth / 2f),
                (center.y - radius * sin(r)).toFloat().coerceIn(context.dp(60).toFloat(), screen.y - context.dp(110).toFloat()),
            )
        }
    }

    private fun addItem(icon: Int, label: Int, style: Style, target: PointF, onClick: () -> Unit) {
        val circleSize = context.dp(if (style == Style.PRIMARY) 56 else 46)
        val button = FrameLayout(context).apply {
            background = context.glassFor(look, null, blurred)
            contentDescription = context.getString(label)
            tooltipText = context.getString(label)
            isFocusable = true
            setOnClickListener { onClick() }
            addView(
                ImageView(context).apply {
                    setImageResource(icon)
                    setColorFilter(
                        if (look == Look.NEUBRUTAL) BRUTAL_INK else when (style) {
                            Style.PRIMARY -> Palette.ACCENT
                            Style.DANGER -> Palette.DANGER
                            Style.NORMAL -> Palette.INK
                        },
                    )
                },
                LayoutParams(context.dp(22), context.dp(22), Gravity.CENTER),
            )
        }
        // Icons only; the name is read out by screen readers and shown on long press
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(button, LinearLayout.LayoutParams(circleSize, circleSize))
        }
        addView(column, LayoutParams(itemWidth, LayoutParams.WRAP_CONTENT))
        items += Item(column, circleSize, target)
    }

    private fun closeButton(): View {
        val size = context.dp(52)
        return FrameLayout(context).apply {
            background = context.glassFor(look, null, blurred)
            contentDescription = context.getString(R.string.close_menu)
            translationX = center.x - size / 2f
            translationY = center.y - size / 2f
            setOnClickListener { listener.onDismiss() }
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_power)
                    setColorFilter(ink)
                    rotation = 90f
                },
                LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER),
            )
            layoutParams = LayoutParams(size, size)
        }
    }

    private fun showCard(title: Int, body: Int, go: Int, danger: Boolean, action: () -> Unit) {
        items.forEach { it.view.visibility = INVISIBLE }
        val pad = context.dp(20)
        val c = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // The card holds text, so it gets a darker tint than the buttons
            background = context.glassFor(look, context.dp(26).toFloat(), blurred, holdsText = true)
            setPadding(pad, pad, pad, context.dp(16))
            isClickable = true // keep taps on the card from reaching the scrim

            addView(TextView(context).apply {
                setText(title)
                setTextColor(ink)
                textSize = 20f
            })
            addView(
                TextView(context).apply {
                    setText(body)
                    setTextColor(if (look == Look.NEUBRUTAL) BRUTAL_INK else if (danger) Palette.DANGER else Palette.MUTED)
                    textSize = 14f
                },
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = context.dp(6)
                    bottomMargin = context.dp(16)
                },
            )
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(cardButton(R.string.cancel, Palette.CARD_BUTTON, Palette.INK) { hideCard() }, buttonParams(end = context.dp(4)))
                addView(
                    cardButton(
                        go,
                        if (danger) Palette.DANGER else Palette.ACCENT,
                        if (danger) Palette.DANGER_INK else Palette.ACCENT_INK,
                        action,
                    ),
                    buttonParams(start = context.dp(4)),
                )
            })
        }
        val side = context.dp(12)
        addView(c, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(side, 0, side, context.dp(56))
        })
        c.translationY = context.dp(240).toFloat()
        c.animate().translationY(0f).setDuration(260).start()
        card = c
    }

    private fun hideCard() {
        card?.let { removeView(it) }
        card = null
        items.forEach { it.view.visibility = VISIBLE }
    }

    private fun cardButton(text: Int, fill: Int, ink: Int, onClick: () -> Unit) = TextView(context).apply {
        setText(text)
        setTextColor(if (look == Look.NEUBRUTAL) BRUTAL_INK else ink)
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = if (look == Look.NEUBRUTAL) context.neubrutalFor(if (fill == Palette.DANGER) BrutalFill.PINK.color else null)
            else rounded(fill, context.dp(14).toFloat())
        minHeight = context.dp(48)
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun buttonParams(start: Int = 0, end: Int = 0) =
        LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = start
            marginEnd = end
        }
}
