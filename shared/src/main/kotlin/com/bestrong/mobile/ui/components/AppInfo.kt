package com.bestrong.mobile.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/** Datos de la compilación (versión y enlaces) que solo conoce el módulo de la app. */
data class AppInfo(val versionName: String, val versionCode: Int, val repoUrl: String, val donationUrl: String)

val LocalAppInfo = staticCompositionLocalOf { AppInfo("", 0, "", "") }
