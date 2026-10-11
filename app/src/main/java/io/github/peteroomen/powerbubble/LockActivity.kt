package io.github.peteroomen.powerbubble

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible activity that turns the screen off and closes. Used by the home screen shortcut,
 * and by a launcher's double-tap gesture:
 * `Intent("io.github.peteroomen.powerbubble.action.SCREEN_OFF")`.
 */
class LockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = BubbleService.instance
        if (service != null) {
            service.lock()
        } else {
            Toast.makeText(this, R.string.service_off_toast, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
