package com.voltavpn.app.vpn

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.voltavpn.app.MainActivity
import com.voltavpn.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Quick-settings tile. Connected: a tap disconnects. Otherwise it opens Volta and starts the
 * normal connect, so the volunteer-server notice and the daily ad still apply.
 */
class VpnTileService : TileService() {
    private var watch: Job? = null

    override fun onStartListening() {
        watch = CoroutineScope(Dispatchers.Main).launch {
            VpnController.state.collectLatest { render(it) }
        }
    }

    override fun onStopListening() {
        watch?.cancel()
        watch = null
    }

    override fun onClick() {
        if (VpnController.state.value == VpnState.CONNECTED) {
            VpnController.disconnect(this)
            return
        }
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_CONNECT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        tile.state = when (state) {
            VpnState.CONNECTED, VpnState.CONNECTING -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (state) {
                VpnState.CONNECTED -> getString(R.string.state_connected)
                VpnState.CONNECTING -> getString(R.string.state_connecting)
                else -> null
            }
        }
        tile.updateTile()
    }
}
