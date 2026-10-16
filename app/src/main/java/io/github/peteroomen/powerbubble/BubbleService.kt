package io.github.peteroomen.powerbubble

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent

/**
 * The accessibility service is what lets the bubble float above every app and turn the screen
 * off. It doesn't read screen content; it ignores every accessibility event.
 */
class BubbleService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BubbleService? = null
            private set
    }

    private lateinit var prefs: Prefs
    private var overlay: BubbleOverlay? = null

    // The accessibility shortcut (button, gesture or volume keys) shows and hides the bubble or turns
    // the screen off, rather than switching the whole service off
    private val shortcut = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) {
            when (prefs.shortcutAction) {
                ShortcutAction.TOGGLE_BUBBLE -> toggleBubble()
                ShortcutAction.SCREEN_OFF -> {
                    overlay?.closeMenu()
                    lock()
                }
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        prefs = Prefs(this)
        overlay = BubbleOverlay(this, prefs).also { if (prefs.visible) it.show() }
        accessibilityButtonController.registerAccessibilityButtonCallback(shortcut)
        BubbleTileService.refresh(this)
    }

    fun toggleBubble() = setBubbleVisible(!prefs.visible)

    override fun onUnbind(intent: Intent?): Boolean {
        tearDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private fun tearDown() {
        if (instance != null) accessibilityButtonController.unregisterAccessibilityButtonCallback(shortcut)
        overlay?.hide()
        overlay = null
        instance = null
        BubbleTileService.refresh(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onScreenChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun setBubbleVisible(visible: Boolean) {
        prefs.visible = visible
        if (visible) overlay?.show() else overlay?.hide()
        BubbleTileService.refresh(this)
    }

    fun onSettingsChanged() {
        overlay?.closeMenu()
    }

    fun onLookChanged() {
        overlay?.applyLook()
    }

    /** Same as pressing the power button: biometric unlock keeps working. */
    fun lock(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    /** Screenshot with the bubble hidden. */
    fun screenshot() {
        overlay?.takeScreenshot() ?: captureScreen()
    }

    fun captureScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    fun restart() {
        if (!PowerActions.reboot()) performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }

    fun shutdown() {
        if (!PowerActions.shutdown()) performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
}
