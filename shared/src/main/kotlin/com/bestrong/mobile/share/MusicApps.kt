package com.bestrong.mobile.share

import android.content.Context
import android.content.Intent

/** App de música que se puede abrir desde el entreno (los controles funcionan con cualquiera). */
data class MusicApp(val packageName: String, val label: String)

object MusicApps {
    /** Las más habituales; deben estar también en <queries> del manifiesto para poder detectarlas. */
    val known = listOf(
        MusicApp("com.apple.android.music", "Apple Music"),
        MusicApp("com.spotify.music", "Spotify"),
        MusicApp("com.google.android.apps.youtube.music", "YouTube Music"),
        MusicApp("com.sec.android.app.music", "Samsung Music"),
        MusicApp("com.amazon.mp3", "Amazon Music"),
        MusicApp("deezer.android.app", "Deezer"),
        MusicApp("com.aspiro.tidal", "Tidal"),
    )

    private fun launchIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    /** Apps de la lista que están instaladas. */
    fun installed(context: Context): List<MusicApp> = known.filter { launchIntent(context, it.packageName) != null }

    /** La elegida en Ajustes si está instalada; si no, la primera instalada (null si no hay ninguna). */
    fun preferred(context: Context, packageName: String?): MusicApp? {
        val installed = installed(context)
        return installed.firstOrNull { it.packageName == packageName } ?: installed.firstOrNull()
    }

    fun open(context: Context, app: MusicApp) {
        launchIntent(context, app.packageName)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
