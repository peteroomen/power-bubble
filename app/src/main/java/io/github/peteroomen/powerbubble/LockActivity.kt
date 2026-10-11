package io.github.peteroomen.powerbubble

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible activity that does one thing and closes. Used by the home screen shortcut and by
 * launcher gestures:
 * - `io.github.peteroomen.powerbubble.action.SCREEN_OFF` turns the screen off (the default)
 * - `io.github.peteroomen.powerbubble.action.NOTIFICATIONS` opens the notification shade
 * - `io.github.peteroomen.powerbubble.action.SCREENSHOT` takes a screenshot without the bubble in it
 */
class LockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = BubbleService.instance
        if (service == null) {
            Toast.makeText(this, R.string.service_off_toast, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
        } else if (intent?.action == ACTION_NOTIFICATIONS) {
            service.openNotifications()
        } else if (intent?.action == ACTION_SCREENSHOT) {
            service.screenshot()
        } else {
            service.lock()
        }
        finish()
    }

    private companion object {
        const val ACTION_NOTIFICATIONS = "io.github.peteroomen.powerbubble.action.NOTIFICATIONS"
        const val ACTION_SCREENSHOT = "io.github.peteroomen.powerbubble.action.SCREENSHOT"
    }
}
