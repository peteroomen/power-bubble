package io.github.peteroomen.powerbubble

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.Display
import android.annotation.TargetApi
import android.graphics.drawable.Drawable

/**
 * True lens for the Glass look: the launcher's clear glass, bending a screenshot of what's under the
 * bubble. The bubble can't see behind itself, so the accessibility service captures the screen
 * (Android 13+ for the shader) and keeps only a small patch around the bubble, in memory.
 */
object Lens {
    fun supported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * A copy of the whole screen as it was with the bubble hidden, at half size (like the launcher's
     * lightly softened wallpaper copy). The whole screen, so the lens can follow the bubble as it
     * moves without capturing again.
     */
    class Patch(val bitmap: Bitmap, val area: Rect) {
        val scale get() = area.width().toFloat() / bitmap.width
    }

    /**
     * Captures the screen and returns it to [done] on the main thread, or null
     * if the phone refused: a secure app on screen, or [tooSoon] after the last capture (Android
     * allows about three a second).
     */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    fun capture(service: AccessibilityService, done: (patch: Patch?, tooSoon: Boolean) -> Unit) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val patch = try {
                        val shot = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        val full = shot?.copy(Bitmap.Config.ARGB_8888, false)
                        shot?.recycle()
                        full?.let { halve(it).also { full.recycle() } }
                    } catch (e: RuntimeException) {
                        Log.w("PowerBubble", "Lens capture unreadable", e)
                        null
                    } finally {
                        buffer.close()
                    }
                    done(patch, false)
                }

                override fun onFailure(errorCode: Int) =
                    done(null, errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT)
            },
        )
    }

    private fun halve(full: Bitmap): Patch? {
        if (full.width < 2 || full.height < 2) return null
        return Patch(Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true), Rect(0, 0, full.width, full.height))
    }
}

/**
 * The bubble as a lens over [patch]: the launcher's clear glass shader with its settings (a bevel of
 * up to 16dp bending light in by up to 9dp), under its 20% tint, as on its music card. [origin] gives the bubble's
 * top-left corner on screen each frame.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
class LensDrawable(
    private val patch: Lens.Patch,
    private val bevelMax: Float,
    private val strengthMax: Float,
    private val origin: () -> Pair<Int, Int>,
) : Drawable() {

    private val shader = RuntimeShader(CLEAR_AGSL).apply {
        setInputShader(
            "image",
            BitmapShader(patch.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            },
        )
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@LensDrawable.shader }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val behindIcon = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var alpha = 255

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val shortest = minOf(rect.width(), rect.height())
        if (shortest <= 0f) return
        val r = shortest / 2
        val (x, y) = origin()
        shader.setFloatUniform("extend", 0f, 0f, 0f, 0f)
        shader.setFloatUniform("size", rect.width(), rect.height())
        shader.setFloatUniform("radius", r)
        shader.setFloatUniform("bevel", minOf(shortest * 0.25f, bevelMax))
        shader.setFloatUniform("strength", minOf(shortest * 0.16f, strengthMax))
        shader.setFloatUniform("zoom", 1f)
        shader.setFloatUniform("panelOrigin", (x + bounds.left).toFloat(), (y + bounds.top).toFloat())
        shader.setFloatUniform("imageOrigin", patch.area.left.toFloat(), patch.area.top.toFloat())
        shader.setFloatUniform("imageScale", patch.scale)
        canvas.save()
        canvas.translate(rect.left, rect.top)
        rect.offsetTo(0f, 0f)
        paint.alpha = alpha
        canvas.drawRoundRect(rect, r, r, paint)
        // The launcher's light tint over clear glass, so the icon stays readable
        tint.color = 0x33000000
        tint.alpha = 0x33 * alpha / 255
        canvas.drawRoundRect(rect, r, r, tint)
        behindIcon.alpha = alpha
        canvas.drawRoundRect(rect, r, r, behindIcon)
        canvas.restore()
    }

    override fun onBoundsChange(bounds: Rect) {
        behindIcon.shader = iconShadeFor(Rect(0, 0, bounds.width(), bounds.height()), ICON_SHADE)
    }

    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        // The niagara-like launcher's clear glass shader (GlassEdge.kt), unchanged; keep the two in step
        const val CLEAR_AGSL = """
            uniform shader image;
            uniform float2 size;
            uniform float radius;
            uniform float bevel;
            uniform float strength;
            uniform float zoom;
            uniform float2 panelOrigin;
            uniform float2 imageOrigin;
            uniform float imageScale;
            uniform float4 extend; // left, top, right, bottom: edges pushed out of sight

            float edgeDistance(float2 p, float2 halfSize, float r) {
                float2 q = abs(p) - halfSize + r;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            half3 wallpaperAt(float2 local) {
                return image.eval((panelOrigin + local - imageOrigin) / imageScale).rgb;
            }

            half4 main(float2 coord) {
                float2 shift = float2(extend.x, extend.y);
                float2 halfSize = (size + float2(extend.x + extend.z, extend.y + extend.w)) * 0.5;
                float2 p = coord + shift - halfSize;
                float inside = max(-edgeDistance(p, halfSize, radius), 0.0);
                float2 n = float2(
                    edgeDistance(p + float2(1.0, 0.0), halfSize, radius) - edgeDistance(p - float2(1.0, 0.0), halfSize, radius),
                    edgeDistance(p + float2(0.0, 1.0), halfSize, radius) - edgeDistance(p - float2(0.0, 1.0), halfSize, radius));
                n = n / max(length(n), 0.0001);

                float s = 1.0 - clamp(inside / bevel, 0.0, 1.0);
                float bend = strength * s * s;
                float2 base = halfSize + p / zoom - shift;

                half3 c;
                c.r = wallpaperAt(base - n * bend).r;
                c.g = wallpaperAt(base - n * bend * 1.1).g;
                c.b = wallpaperAt(base - n * bend * 1.2).b;

                half lum = dot(c, half3(0.2126, 0.7152, 0.0722));
                c = mix(half3(lum), c, 1.2);

                float facing = abs(dot(n, float2(-0.7071, -0.7071)));
                float rim = (1.0 - smoothstep(0.0, 4.5, inside)) * (0.35 + 0.65 * facing);
                float glow = (1.0 - smoothstep(0.0, bevel * 0.8, inside)) * 0.12;
                c = c * 0.95 + 0.05 + half(rim * 0.8 + glow);
                return half4(clamp(c, 0.0, 1.0), 1.0);
            }
        """
    }
}
