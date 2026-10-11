package io.github.peteroomen.powerbubble

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile that takes a screenshot once the shade has closed. */
class ScreenshotTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = if (BubbleService.instance != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.updateTile()
    }

    override fun onClick() {
        val service = BubbleService.instance ?: return
        // Close the shade first so it isn't in the picture
        if (Build.VERSION.SDK_INT >= 31) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        Handler(Looper.getMainLooper()).postDelayed({ service.screenshot() }, 450)
    }
}
