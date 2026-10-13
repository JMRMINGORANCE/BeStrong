package com.bestrong.mobile.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bestrong.mobile.MainActivity
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.shared.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Mosaico de ajustes rápidos: con un toque empieza un entreno vacío o abre el que está en curso. */
@AndroidEntryPoint
class QuickStartTileService : TileService() {
    @Inject lateinit var repository: BeStrongRepository

    private val scope = MainScope()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // Lectura de la base de datos: en arranque en frío activeWorkout aún vale null
    override fun onStartListening() {
        super.onStartListening()
        scope.launch { update(repository.activeOnce() != null) }
    }

    private fun update(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (active) getString(R.string.qs_tile_active) else null
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch { open(repository.activeOnce() != null) }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open(active: Boolean) {
        val action = if (active) MainActivity.ACTION_OPEN_WORKOUT else MainActivity.ACTION_START_EMPTY
        val intent = Intent(this, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
