package com.bestrong.wear

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.bestrong.wear.complication.TodayComplicationService
import com.bestrong.wear.complication.WeeklyComplicationService
import com.bestrong.wear.data.PhoneSync
import com.bestrong.wear.data.WearStore
import com.bestrong.wear.session.CardioSession
import com.bestrong.wear.session.WorkoutSession
import com.bestrong.wear.tile.BeStrongTileService
import com.bestrong.wear.tile.TodayTileService
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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
    /** Pide a las tiles y a las complicaciones que se redibujen con los datos nuevos. */
    fun refreshSurfaces() {
        for (tile in listOf(BeStrongTileService::class.java, TodayTileService::class.java)) {
            runCatching { TileService.getUpdater(app).requestUpdate(tile) }
        }
        for (complication in listOf(WeeklyComplicationService::class.java, TodayComplicationService::class.java, com.bestrong.wear.complication.RestComplicationService::class.java)) {
            runCatching {
                ComplicationDataSourceUpdateRequester
                    .create(app, ComponentName(app, complication))
                    .requestUpdateAll()
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun start() {
        // Historial, rutinas («Hoy toca»), entreno en curso («Continuar») y ajustes (objetivo semanal,
        // unidad): cualquier cambio redibuja las superficies, agrupando ráfagas para no saturar.
        scope.launch {
            merge(
                store.history.drop(1),
                store.routines.drop(1),
                session.workout.map { it != null }.distinctUntilChanged().drop(1),
                store.settings.drop(1),
                // La complicación del descanso: al empezar, alargar o acabar uno
                session.rest.map { it?.endsAtEpochMs }.distinctUntilChanged().drop(1),
            ).debounce(500).collect { refreshSurfaces() }
        }
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
