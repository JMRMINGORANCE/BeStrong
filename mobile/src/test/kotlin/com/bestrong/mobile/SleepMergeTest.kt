package com.bestrong.mobile

import com.bestrong.mobile.health.mergedMinutes
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** El sueño de dos apps que apuntan la misma noche no se cuenta dos veces. */
class SleepMergeTest {
    private val t0 = Instant.parse("2026-01-01T23:00:00Z")
    private fun h(n: Long) = t0.plusSeconds(n * 3600)

    @Test fun overlappingSessionsCountOnce() {
        assertEquals(8 * 60L, mergedMinutes(listOf(t0 to h(8), t0 to h(8))))
        assertEquals(9 * 60L, mergedMinutes(listOf(t0 to h(8), h(7) to h(9))))
    }

    @Test fun separateSessionsAdd() {
        assertEquals(7 * 60L, mergedMinutes(listOf(h(5) to h(6), t0 to h(4), h(10) to h(12))))
    }
}
