package com.bestrong.mobile.update

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
}
