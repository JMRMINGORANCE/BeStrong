package com.bestrong.mobile.security

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/**
 * Bloqueo con huella (o el PIN/patrón del móvil) para las fotos de progreso.
 * Usa la API del sistema (Android 11+), sin dependencias extra.
 */
object BiometricLock {
    /** Si el móvil no tiene bloqueo de pantalla no hay nada con lo que proteger: se abre directamente. */
    fun isAvailable(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun authenticate(context: Context, title: String, onSuccess: () -> Unit, onFail: () -> Unit = {}) {
        if (!isAvailable(context)) return onSuccess()
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onFail()
            },
        )
    }
}
