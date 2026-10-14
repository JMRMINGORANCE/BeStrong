package com.bestrong.mobile

import android.content.Context
import android.content.Intent

/**
 * Intents para abrir la actividad principal desde módulos que no la conocen
 * (notificaciones, servicio del entreno, atajos). Se referencia por nombre de clase.
 */
object AppIntents {
    const val MAIN_ACTIVITY = "com.bestrong.mobile.MainActivity"
    const val PRIVACY_ACTIVITY = "com.bestrong.mobile.PrivacyPolicyActivity"
    const val ACTION_OPEN_WORKOUT = "com.bestrong.OPEN_WORKOUT"
    const val ACTION_START_EMPTY = "com.bestrong.START_EMPTY"
    const val ACTION_START_ROUTINE = "com.bestrong.START_ROUTINE"
    const val EXTRA_ROUTINE_ID = "routine_id"
    const val ACTION_OPEN_MUSCLE = "com.bestrong.OPEN_MUSCLE"
    const val EXTRA_MUSCLE = "muscle"

    fun main(context: Context): Intent = Intent().setClassName(context, MAIN_ACTIVITY)

    fun privacy(context: Context): Intent = Intent().setClassName(context, PRIVACY_ACTIVITY)
}
