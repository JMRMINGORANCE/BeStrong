package com.bestrong.wear

import android.app.Application
import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.bestrong.wear.complication.WeeklyComplicationService
import com.bestrong.wear.data.PhoneSync
import com.bestrong.wear.data.WearStore
import com.bestrong.wear.session.WorkoutSession
import com.bestrong.wear.tile.BeStrongTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class WearContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = WearStore(app)
    val phoneSync = PhoneSync(app, store)
    val session = WorkoutSession(app, store, phoneSync, scope)

    /** Pide a la tile y a la complicación que se redibujen con los datos nuevos. */
    fun refreshSurfaces() {
        runCatching { TileService.getUpdater(app).requestUpdate(BeStrongTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(app, ComponentName(app, WeeklyComplicationService::class.java))
                .requestUpdateAll()
        }
    }

    init {
        scope.launch { store.history.drop(1).collect { refreshSurfaces() } }
    }
}

class BeStrongWearApp : Application() {
    lateinit var container: WearContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = WearContainer(this)
    }
}
