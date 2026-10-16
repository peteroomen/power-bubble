package io.github.peteroomen.powerbubble

import android.content.Context

enum class TapMode { MENU, SCREEN_OFF }

/** What the accessibility shortcut (button, gesture or volume keys) does. */
enum class ShortcutAction { TOGGLE_BUBBLE, SCREEN_OFF }

/** Flat Loud Taxi fills, kept separately from existing glass preferences. */
enum class BrutalFill(val color: Int) {
    YELLOW(0xFFFFE600.toInt()), CYAN(0xFF00CBE5.toInt()), PINK(0xFFFF6395.toInt()), PAPER(0xFFFFFDF6.toInt()),
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("power_bubble", Context.MODE_PRIVATE)

    var visible: Boolean
        get() = sp.getBoolean("visible", true)
        set(value) = sp.edit().putBoolean("visible", value).apply()

    var tapMode: TapMode
        get() = TapMode.entries.getOrElse(sp.getInt("tap_mode", 0)) { TapMode.MENU }
        set(value) = sp.edit().putInt("tap_mode", value.ordinal).apply()

    var shortcutAction: ShortcutAction
        get() = ShortcutAction.entries.getOrElse(sp.getInt("shortcut_action", 0)) { ShortcutAction.TOGGLE_BUBBLE }
        set(value) = sp.edit().putInt("shortcut_action", value.ordinal).apply()

    var look: Look
        get() = Look.entries.getOrElse(sp.getInt("look", Look.BLUR.ordinal)) { Look.BLUR }
        set(value) = sp.edit().putInt("look", value.ordinal).apply()

    var brutalFill: BrutalFill
        get() = BrutalFill.entries.getOrElse(sp.getInt("brutal_fill", 0)) { BrutalFill.YELLOW }
        set(value) = sp.edit().putInt("brutal_fill", value.ordinal).apply()

    var brutalRounded: Boolean
        get() = sp.getBoolean("brutal_rounded", true)
        set(value) = sp.edit().putBoolean("brutal_rounded", value).apply()

    /** Which screen edge the bubble is snapped to. */
    var onLeft: Boolean
        get() = sp.getBoolean("on_left", false)
        set(value) = sp.edit().putBoolean("on_left", value).apply()

    /** Vertical position as a fraction of screen height, so it survives rotation. */
    var yFraction: Float
        get() = sp.getFloat("y_fraction", 0.35f)
        set(value) = sp.edit().putFloat("y_fraction", value).apply()
}
