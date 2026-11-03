package com.bestrong.mobile.update

import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cuándo se ofrece una versión nueva de Google Play (diálogo y notificación). */
class AppUpdatesTest {
    private val day = 86_400_000L
    private val now = 100 * day

    @Test fun asksForNewVersionUnlessJustDismissed() {
        assertTrue(AppUpdates.shouldAsk(available = 120, dismissedVersion = 0, dismissedAt = 0, now = now))
        assertFalse(AppUpdates.shouldAsk(available = 120, dismissedVersion = 120, dismissedAt = now - day, now = now))
        // Pasados unos días se vuelve a preguntar, y una versión más nueva se ofrece enseguida
        assertTrue(AppUpdates.shouldAsk(available = 120, dismissedVersion = 120, dismissedAt = now - 3 * day, now = now))
        assertTrue(AppUpdates.shouldAsk(available = 121, dismissedVersion = 120, dismissedAt = now, now = now))
    }

    @Test fun notifiesOncePerVersionOnlyWhenAppUnused() {
        assertTrue(UpdateCheckWorker.shouldNotify(available = 120, notifiedVersion = 0, lastOpen = now - 3 * day, now = now))
        // Abierta hace poco: ya ha visto el diálogo
        assertFalse(UpdateCheckWorker.shouldNotify(available = 120, notifiedVersion = 0, lastOpen = now - day, now = now))
        assertFalse(UpdateCheckWorker.shouldNotify(available = 120, notifiedVersion = 120, lastOpen = now - 3 * day, now = now))
    }

    private fun decide(
        availability: Int = UpdateAvailability.UPDATE_AVAILABLE,
        status: Int = InstallStatus.UNKNOWN,
        inFlight: Int? = null,
        dismissedVersion: Int = 0,
    ) = AppUpdates.decide(availability, status, flexibleAllowed = true, available = 120, inFlightVersion = inFlight, dismissedVersion = dismissedVersion, dismissedAt = now, now = now)

    @Test fun offersOnceAndNotAgainWhileDownloading() {
        assertEquals(UpdatePrompt.Available(120), decide())
        // Al volver de la pantalla de Play la descarga sigue «disponible»: no se repite el diálogo
        assertEquals(UpdatePrompt.None, decide(inFlight = 120))
        assertEquals(UpdatePrompt.None, decide(status = InstallStatus.DOWNLOADING))
        assertEquals(UpdatePrompt.None, decide(status = InstallStatus.PENDING))
        assertEquals(UpdatePrompt.None, decide(status = InstallStatus.INSTALLING))
        assertEquals(UpdatePrompt.None, decide(availability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS))
    }

    @Test fun downloadedAlwaysOffersRestart() {
        assertEquals(UpdatePrompt.Downloaded, decide(status = InstallStatus.DOWNLOADED, inFlight = 120))
        assertEquals(UpdatePrompt.Downloaded, decide(status = InstallStatus.DOWNLOADED, dismissedVersion = 120))
    }

    @Test fun failedOrCancelledCanBeOfferedAgain() {
        // Tras un fallo (sin descarga en curso) se vuelve a ofrecer; si la rechazó hace poco, no
        assertEquals(UpdatePrompt.Available(120), decide(status = InstallStatus.FAILED))
        assertEquals(UpdatePrompt.None, decide(status = InstallStatus.CANCELED, dismissedVersion = 120))
        assertEquals(UpdatePrompt.None, decide(availability = UpdateAvailability.UPDATE_NOT_AVAILABLE))
    }
}
