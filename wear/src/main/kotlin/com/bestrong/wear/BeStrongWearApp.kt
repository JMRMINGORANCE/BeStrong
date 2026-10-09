package com.bestrong.wear

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.bestrong.wear.complication.WeeklyComplicationService
import com.bestrong.wear.data.PhoneSync
import com.bestrong.wear.data.WearStore
import com.bestrong.wear.session.CardioSession
import com.bestrong.wear.session.WorkoutSession
import com.bestrong.wear.tile.BeStrongTileService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object WearModule {
    /** Ámbito de la app en el hilo principal: el estado del entreno lo leen la UI y los servicios. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

/** Punto de acceso único a las dependencias del reloj para la UI, la tile y la complicación. */
@Singleton
class WearContainer @Inject constructor(
    @ApplicationContext private val app: Context,
    @ApplicationScope val scope: CoroutineScope,
    val store: WearStore,
    val phoneSync: PhoneSync,
    val session: WorkoutSession,
    val cardio: CardioSession,
) {
    /** Pide a la tile y a la complicación que se redibujen con los datos nuevos. */
    fun refreshSurfaces() {
        runCatching { TileService.getUpdater(app).requestUpdate(BeStrongTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(app, ComponentName(app, WeeklyComplicationService::class.java))
                .requestUpdateAll()
        }
    }

    fun start() {
        scope.launch { store.history.drop(1).collect { refreshSurfaces() } }
    }
}

@HiltAndroidApp
class BeStrongWearApp : Application() {
    @Inject lateinit var container: WearContainer

    override fun onCreate() {
        super.onCreate()
        container.start()
    }
}
