package io.github.peteroomen.powerbubble

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile that turns the screen off. */
class ScreenOffTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = if (BubbleService.instance != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.updateTile()
    }

    override fun onClick() {
        BubbleService.instance?.lock()
    }
}
