package io.github.peteroomen.powerbubble

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Owns the three overlay windows: the bubble itself, the ✕ drop target shown while dragging,
 * and the full-screen menu. All coordinates are in screen pixels from the top-left corner.
 */
@SuppressLint("ClickableViewAccessibility")
class BubbleOverlay(private val service: BubbleService, private val prefs: Prefs) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val size = service.dp(52)
    private val margin = service.dp(8)
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private val bubbleIcon = ImageView(service).apply {
        setImageResource(R.drawable.ic_power)
        setColorFilter(if (prefs.look == Look.NEUBRUTAL) BRUTAL_INK else Palette.INK)
    }
    private val bubble = makeBubble()
    private val params = overlayParams(size, size)
    private var attached = false

    private val dismissSize = service.dp(64)
    private val dismiss = makeDismiss()
    private val dismissParams = overlayParams(dismissSize, dismissSize, touchable = false)
    private var dismissAttached = false
    private var dismissHot = false

    private var menu: MenuView? = null
    private var snapAnim: ValueAnimator? = null
    private var fadeAnim: ValueAnimator? = null
    private val fade = Runnable {
        fadeAnim?.cancel()
        fadeAnim = ValueAnimator.ofFloat(1f, IDLE_ALPHA).apply {
            duration = 350
            addUpdateListener { setBubbleAlpha(it.animatedValue as Float) }
            start()
        }
    }

    /** Fades the bubble's glass, blur and icon together (a real blur ignores the view's own alpha). */
    private fun setBubbleAlpha(alpha: Float) {
        bubble.background?.alpha = (alpha * 255).roundToInt()
        bubbleIcon.alpha = alpha
    }

    /** The look changed in settings. */
    fun applyLook() {
        closeMenu()
        bubble.setLook(prefs.look, null)
        dismiss.setLook(prefs.look, null)
        bubbleIcon.setColorFilter(if (prefs.look == Look.NEUBRUTAL) BRUTAL_INK else Palette.INK)
        (dismiss.getChildAt(0) as? ImageView)?.setColorFilter(if (prefs.look == Look.NEUBRUTAL) BRUTAL_INK else Palette.INK)
        dismissHot = false
        wake()
        scheduleFade()
    }

    fun show() {
        if (attached) return
        placeFromPrefs()
        setBubbleAlpha(1f)
        bubble.visibility = View.VISIBLE
        wm.addView(bubble, params)
        attached = true
        scheduleFade()
    }

    fun hide() {
        closeMenu()
        hideDismiss()
        snapAnim?.cancel()
        handler.removeCallbacks(fade)
        if (attached) {
            wm.removeView(bubble)
            attached = false
        }
    }

    /** Rotation or a display change: put the bubble back on its saved edge. */
    fun onScreenChanged() {
        if (!attached) return
        closeMenu()
        snapAnim?.cancel()
        placeFromPrefs()
        wm.updateViewLayout(bubble, params)
    }

    fun closeMenu() {
        val m = menu ?: return
        menu = null
        wm.removeView(m)
        bubble.visibility = View.VISIBLE
        wake()
    }

    private fun openMenu() {
        if (menu != null || !attached) return
        snapAnim?.end()
        handler.removeCallbacks(fade)
        val screen = screenSize()
        val center = PointF(params.x + size / 2f, params.y + size / 2f)
        val look = prefs.look
        val blurRadius = service.menuBlurRadius(look)
        val m = MenuView(
            service,
            look = look,
            blurred = blurRadius != null,
            center = center,
            onLeft = center.x < screen.x / 2f,
            screen = screen,
            confirmFirst = PowerActions.shizukuState() == ShizukuState.READY,
            listener = object : MenuView.Listener {
                override fun onDismiss() = closeMenu()
                override fun onScreenOff() {
                    closeMenu()
                    // Let the menu window disappear first so it doesn't flash on unlock.
                    handler.postDelayed({ service.lock() }, 120)
                }
                override fun onScreenshot() = takeScreenshot()
                override fun onRestart() {
                    closeMenu()
                    service.restart()
                }
                override fun onShutdown() {
                    closeMenu()
                    service.shutdown()
                }
            },
        )
        wm.addView(m, overlayParams(screen.x, screen.y).apply {
            // Blur everything behind the menu (Android 12+, where the phone supports it)
            if (blurRadius != null && Build.VERSION.SDK_INT >= 31) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = blurRadius
            }
        })
        menu = m
        bubble.visibility = View.INVISIBLE // the menu draws its own copy in the same spot
        m.animateIn()
    }

    /**
     * Takes a system screenshot without the bubble in it: the bubble is hidden first and comes back
     * once the screenshot has been captured.
     */
    fun takeScreenshot() {
        closeMenu()
        handler.removeCallbacks(fade)
        bubble.visibility = View.INVISIBLE
        handler.postDelayed({
            service.captureScreen()
            handler.postDelayed({
                bubble.visibility = View.VISIBLE
                wake()
                scheduleFade()
            }, SCREENSHOT_RESTORE_MS)
        }, SCREENSHOT_DELAY_MS)
    }

    private fun onTap() {
        wake()
        when {
            menu != null -> closeMenu()
            prefs.tapMode == TapMode.SCREEN_OFF -> service.lock()
            else -> openMenu()
        }
    }

    private inner class BubbleTouch : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longFired = false

        private val longPress = Runnable {
            if (!dragging && prefs.tapMode == TapMode.SCREEN_OFF) {
                longFired = true
                bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                openMenu()
            }
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnim?.cancel()
                    wake()
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longFired = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                        showDismiss()
                    }
                    if (dragging) drag(startX + dx.roundToInt(), startY + dy.roundToInt())
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) {
                        val drop = dismissHot
                        hideDismiss()
                        if (drop) {
                            service.setBubbleVisible(false)
                            Toast.makeText(service, R.string.bubble_hidden, Toast.LENGTH_LONG).show()
                        } else {
                            snapToEdge()
                        }
                    } else if (!longFired) {
                        v.performClick()
                    }
                    scheduleFade()
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) {
                        hideDismiss()
                        snapToEdge()
                    }
                    scheduleFade()
                }
            }
            return true
        }
    }

    private fun drag(x: Int, y: Int) {
        val screen = screenSize()
        params.x = x.coerceIn(0, screen.x - size)
        params.y = y.coerceIn(0, screen.y - size)

        val targetX = dismissParams.x + dismissSize / 2f
        val targetY = dismissParams.y + dismissSize / 2f
        val hot = hypot(params.x + size / 2f - targetX, params.y + size / 2f - targetY) < service.dp(72)
        setDismissHot(hot)
        if (hot) { // pull the bubble onto the target
            params.x = (targetX - size / 2f).roundToInt()
            params.y = (targetY - size / 2f).roundToInt()
        }
        if (attached) wm.updateViewLayout(bubble, params)
    }

    private fun snapToEdge() {
        val screen = screenSize()
        val onLeft = params.x + size / 2 < screen.x / 2
        val fromX = params.x
        val fromY = params.y
        val toX = if (onLeft) margin else screen.x - size - margin
        val toY = params.y.coerceIn(minY(), maxY(screen))
        prefs.onLeft = onLeft
        prefs.yFraction = toY.toFloat() / screen.y

        snapAnim?.cancel()
        snapAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                val f = it.animatedValue as Float
                params.x = (fromX + (toX - fromX) * f).roundToInt()
                params.y = (fromY + (toY - fromY) * f).roundToInt()
                if (attached) wm.updateViewLayout(bubble, params)
            }
            start()
        }
    }

    private fun placeFromPrefs() {
        val screen = screenSize()
        params.x = if (prefs.onLeft) margin else screen.x - size - margin
        params.y = (prefs.yFraction * screen.y).roundToInt().coerceIn(minY(), maxY(screen))
    }

    // Keep clear of the status bar and the navigation bar.
    private fun minY() = service.dp(56)
    private fun maxY(screen: Point) = maxOf(minY(), screen.y - size - service.dp(72))

    private fun wake() {
        handler.removeCallbacks(fade)
        fadeAnim?.cancel()
        setBubbleAlpha(1f)
    }

    private fun scheduleFade() {
        handler.removeCallbacks(fade)
        handler.postDelayed(fade, IDLE_DELAY_MS)
    }

    private fun showDismiss() {
        if (dismissAttached) return
        val screen = screenSize()
        dismissParams.x = (screen.x - dismissSize) / 2
        dismissParams.y = screen.y - dismissSize - service.dp(96)
        dismiss.alpha = 0f
        dismiss.scaleX = 0.6f
        dismiss.scaleY = 0.6f
        wm.addView(dismiss, dismissParams)
        dismissAttached = true
        dismiss.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    private fun hideDismiss() {
        setDismissHot(false)
        if (!dismissAttached) return
        wm.removeView(dismiss)
        dismissAttached = false
    }

    private fun setDismissHot(hot: Boolean) {
        if (hot == dismissHot) return
        dismissHot = hot
        if (hot) {
            dismiss.background = if (prefs.look == Look.NEUBRUTAL) service.neubrutalFor(BrutalFill.PINK.color)
                else circle(0x59FF8566, Palette.DANGER, service.dp(2))
        } else dismiss.setLook(prefs.look, null)
        val scale = if (hot) 1.2f else 1f
        dismiss.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
        if (hot) bubble.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun makeBubble() = FrameLayout(service).apply {
        setLook(prefs.look, null)
        contentDescription = service.getString(R.string.bubble_description)
        addView(
            bubbleIcon,
            FrameLayout.LayoutParams(service.dp(24), service.dp(24), Gravity.CENTER),
        )
        setOnTouchListener(BubbleTouch())
        setOnClickListener { onTap() } // reached via performClick() and TalkBack
    }

    private fun makeDismiss() = FrameLayout(service).apply {
        setLook(prefs.look, null)
        addView(
            ImageView(service).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(if (prefs.look == Look.NEUBRUTAL) BRUTAL_INK else Palette.INK)
            },
            FrameLayout.LayoutParams(service.dp(22), service.dp(22), Gravity.CENTER),
        )
    }

    private fun overlayParams(w: Int, h: Int, touchable: Boolean = true) = WindowManager.LayoutParams(
        w,
        h,
        TYPE_ACCESSIBILITY_OVERLAY,
        FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS or (if (touchable) 0 else FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    /** Full physical screen size in the current rotation. */
    private fun screenSize(): Point {
        val display = service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return Point().also {
            @Suppress("DEPRECATION")
            display.getRealSize(it)
        }
    }

    private companion object {
        const val IDLE_ALPHA = 0.45f
        const val IDLE_DELAY_MS = 2600L
        // Long enough for the menu window to disappear before the capture, and for the capture to finish after
        const val SCREENSHOT_DELAY_MS = 300L
        const val SCREENSHOT_RESTORE_MS = 1200L
    }
}
