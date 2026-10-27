package com.bestrong.mobile.health

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.permission.HealthPermission
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Health Connect solo concede (y solo lista la app) con los permisos declarados en el manifiesto con su nombre exacto
 * (android.permission.health.*). Si falta uno o está mal escrito, pedirlos no muestra nada.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xxhdpi")
class HealthPermissionsManifestTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Suppress("DEPRECATION")
    private val declared: Set<String> =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()

    @Test
    fun everyRequestedHealthPermissionIsDeclared() {
        val wanted = HealthConnectExporter(context).permissions + HealthConnectReader(context).permissions +
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        val missing = wanted - declared
        assertTrue("Faltan en el manifiesto: $missing", missing.isEmpty())
    }

    @Test
    fun noMisspelledHealthPermissions() {
        val wrong = declared.filter { "health" in it && !it.startsWith("android.permission.health.") }
        assertTrue("Permisos de salud mal escritos: $wrong", wrong.isEmpty())
    }
}
