package io.github.peteroomen.powerbubble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile that shows or hides the bubble. */
class BubbleTileService : TileService() {

    companion object {
        fun refresh(context: Context) {
            requestListeningState(context, ComponentName(context, BubbleTileService::class.java))
        }
    }

    override fun onStartListening() = update()

    override fun onClick() {
        val service = BubbleService.instance
        if (service == null) {
            openSettings()
            return
        }
        service.setBubbleVisible(!Prefs(this).visible)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val running = BubbleService.instance != null
        tile.state = if (running && Prefs(this).visible) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (running) null else getString(R.string.tile_service_off)
        tile.updateTile()
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated") // only reached below Android 14
            startActivityAndCollapse(intent)
        }
    }
}
