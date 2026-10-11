package io.github.peteroomen.powerbubble

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

    override fun onServiceConnected() {
        instance = this
        prefs = Prefs(this)
        overlay = BubbleOverlay(this, prefs).also { if (prefs.visible) it.show() }
        BubbleTileService.refresh(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        tearDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private fun tearDown() {
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

    /** Same as pressing the power button: biometric unlock keeps working. */
    fun lock(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun restart() {
        if (!PowerActions.reboot()) performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }

    fun shutdown() {
        if (!PowerActions.shutdown()) performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
}
