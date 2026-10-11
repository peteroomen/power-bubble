package io.github.peteroomen.powerbubble

import android.content.Context

enum class TapMode { MENU, SCREEN_OFF }

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("power_bubble", Context.MODE_PRIVATE)

    var visible: Boolean
        get() = sp.getBoolean("visible", true)
        set(value) = sp.edit().putBoolean("visible", value).apply()

    var tapMode: TapMode
        get() = TapMode.entries.getOrElse(sp.getInt("tap_mode", 0)) { TapMode.MENU }
        set(value) = sp.edit().putInt("tap_mode", value.ordinal).apply()

    /** Which screen edge the bubble is snapped to. */
    var onLeft: Boolean
        get() = sp.getBoolean("on_left", false)
        set(value) = sp.edit().putBoolean("on_left", value).apply()

    /** Vertical position as a fraction of screen height, so it survives rotation. */
    var yFraction: Float
        get() = sp.getFloat("y_fraction", 0.35f)
        set(value) = sp.edit().putFloat("y_fraction", value).apply()
}
