package io.github.peteroomen.powerbubble

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * The three looks, matching the launcher's Panels setting. Over other apps the bubble can't see
 * what's behind it, so Glass can't bend it like the launcher's lens; instead both glass looks use
 * the system's own blur behind the bubble (Android 12+, where the phone allows it):
 * - [GLASS]: barely blurred, a light tint and a brighter lit rim, so it reads as clear glass
 * - [BLUR]: blurred under a light tint, with a faint glow along the top and a hairline rim
 * - [FLAT]: a plain see-through dark circle
 * Without blur, the glass looks fall back to the darker tint so the icons stay readable.
 */
// Append new values: existing users store the first three ordinals.
enum class Look { GLASS, BLUR, FLAT, NEUBRUTAL }

/** True if the phone will draw blurs behind windows right now (it's off in battery saver, for one). */
fun Context.blurAvailable(): Boolean =
    Build.VERSION.SDK_INT >= 31 && getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled

/** The look's surface for a button or the bubble. [blurred] says whether something real is blurred behind it. */
fun Context.glassFor(look: Look, cornerRadius: Float?, blurred: Boolean, holdsText: Boolean = false): Drawable {
    val rim = dp(1).toFloat()
    // Text needs more behind it than an icon does
    val heavy = if (holdsText) 0xB3101412.toInt() else Palette.GLASS_TINT
    return when {
        look == Look.NEUBRUTAL -> neubrutalFor()
        look == Look.FLAT -> GlassDrawable(cornerRadius, 0f, heavy, glowAlpha = 0f)
        !blurred -> GlassDrawable(cornerRadius, rim, heavy)
        look == Look.BLUR -> GlassDrawable(
            cornerRadius, rim,
            tint = if (holdsText) 0x73101412 else 0x1F000000, // 12%, as tuned in the launcher
            glowAlpha = 0.15f,
            glowDepth = dp(24).toFloat(),
        )
        else -> GlassDrawable(
            cornerRadius, rim,
            tint = if (holdsText) 0x80101412.toInt() else 0x33000000,
            glowAlpha = 0f,
            rimBoost = 1.3f,
            edgeGlow = 0.08f,
        )
    }
}

/** How much the screen behind the menu is blurred in each look, or null for none. */
fun Context.menuBlurRadius(look: Look): Int? = when {
    !blurAvailable() -> null
    look == Look.BLUR -> dp(28)
    look == Look.GLASS -> dp(10)
    else -> null
}

/**
 * Gives [view] the look's background, with a real blur of what's behind it under the glass. The blur
 * needs the view's window, so it's added once the view is attached; until then (or if the phone
 * can't blur) the view gets the unblurred fallback.
 */
fun View.setLook(look: Look, cornerRadius: Float?) {
    // Unattached drag targets can retain a stale deferred blur listener after a settings change.
    (getTag(R.id.look_attach_listener) as? View.OnAttachStateChangeListener)?.let { removeOnAttachStateChangeListener(it) }
    setTag(R.id.look_attach_listener, null)
    background = context.glassFor(look, cornerRadius, blurred = false)
    val radius = when (look) {
        Look.BLUR -> context.dp(8)
        Look.GLASS -> context.dp(2)
        Look.FLAT, Look.NEUBRUTAL -> return
    }
    val apply = {
        val corner = cornerRadius ?: (minOf(width, height).takeIf { it > 0 } ?: layoutParams?.width ?: 0) / 2f
        BackgroundBlur.create(this, radius, corner)?.let { blur ->
            background = LayerDrawable(arrayOf(blur, context.glassFor(look, cornerRadius, blurred = true)))
        }
    }
    if (isAttachedToWindow) {
        apply()
    } else {
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                v.setTag(R.id.look_attach_listener, null)
                apply()
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        setTag(R.id.look_attach_listener, listener)
        addOnAttachStateChangeListener(listener)
    }
}

/**
 * A blur of whatever is behind one view, the way the system's own UI does it. It's a hidden platform
 * API, reached the same way as the power controls; null if the phone can't or won't.
 */
object BackgroundBlur {
    fun create(view: View, radius: Int, cornerRadius: Float): Drawable? {
        if (!view.context.blurAvailable()) return null
        return try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/view/", "Lcom/android/internal/graphics/drawable/")
            val root = HiddenApiBypass.invoke(View::class.java, view, "getViewRootImpl") ?: return null
            val blur = HiddenApiBypass.invoke(root.javaClass, root, "createBackgroundBlurDrawable") as Drawable
            HiddenApiBypass.invoke(blur.javaClass, blur, "setBlurRadius", radius)
            HiddenApiBypass.invoke(blur.javaClass, blur, "setCornerRadius", cornerRadius)
            blur
        } catch (e: Throwable) {
            Log.w("PowerBubble", "Background blur unavailable", e)
            null
        }
    }
}
