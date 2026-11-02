package com.bestrong.wear.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.bestrong.core.sync.SyncContract
import kotlinx.coroutines.guava.await

/** Abre cosas en el móvil emparejado (la app del móvil atiende los enlaces bestrong://). */
object PhoneLink {
    /** Enlace que el móvil abre en Historial con ese entreno (puede llegar justo antes que el propio entreno). */
    fun workoutUri(workoutId: String): Uri = Uri.parse("${SyncContract.DEEP_LINK_SCHEME}://${SyncContract.DEEP_LINK_WORKOUT_HOST}/$workoutId")

    /** true si el móvil recibió la petición (luego el usuario lo desbloquea y la ve). */
    suspend fun openWorkout(context: Context, workoutId: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(workoutUri(workoutId))
        RemoteActivityHelper(context).startRemoteActivity(intent).await()
        true
    }.getOrElse {
        Log.w("PhoneLink", "No se pudo abrir en el móvil", it)
        false
    }
}
