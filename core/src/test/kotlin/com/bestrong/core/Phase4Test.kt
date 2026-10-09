package com.bestrong.core

import com.bestrong.core.sensors.BarVelocity
import com.bestrong.core.training.HeartRate
import com.bestrong.core.training.VoiceSetParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4Test {
    @Test fun voiceParsesSpanishAndEnglish() {
        VoiceSetParser.parse("80 kilos 8 repeticiones").let { assertEquals(80.0, it.weight); assertEquals(8, it.reps) }
        VoiceSetParser.parse("ochenta por ocho").let { assertEquals(80.0, it.weight); assertEquals(8, it.reps) }
        VoiceSetParser.parse("ochenta y dos y medio por cinco RPE 8").let {
            assertEquals(82.5, it.weight)
            assertEquals(5, it.reps)
            assertEquals(8.0, it.rpe)
        }
        VoiceSetParser.parse("ciento veinte kilos tres reps").let { assertEquals(120.0, it.weight); assertEquals(3, it.reps) }
        VoiceSetParser.parse("100 pounds 5 reps").let { assertEquals(100.0, it.weight); assertEquals(5, it.reps); assertTrue(it.pounds) }
        VoiceSetParser.parse("two hundred twenty five pounds for three").let { assertEquals(225.0, it.weight); assertEquals(3, it.reps) }
        VoiceSetParser.parse("12 repeticiones").let { assertNull(it.weight); assertEquals(12, it.reps) }
        VoiceSetParser.parse("60x10").let { assertEquals(60.0, it.weight); assertEquals(10, it.reps) }
        VoiceSetParser.parse("82,5 kg 6").let { assertEquals(82.5, it.weight); assertEquals(6, it.reps) }
        VoiceSetParser.parse("diez").let { assertNull(it.weight); assertEquals(10, it.reps) }
    }

    @Test fun heartRateZonesAndRecovery() {
        assertEquals(187, HeartRate.estimatedMax(30))
        assertEquals(4, HeartRate.zone(160, 190))
        assertEquals(0, HeartRate.zone(80, 190))
        assertEquals(110, HeartRate.recoveryTarget(155, 65, 190))
        assertTrue(HeartRate.isRecovered(105, 155, 60))
        assertFalse(HeartRate.isRecovered(105, 155, 20))
        assertFalse(HeartRate.isRecovered(130, 155, 90))
    }

    @Test fun velocityRirAndCalibration() {
        val dt = 0.02f

        // Subida a 0,5 m/s: aceleración positiva y luego negativa
        fun rep(peak: Float) = List(25) { peak } + List(25) { -peak }
        val fast = BarVelocity.meanConcentricVelocity(rep(2f), dt)
        val slow = BarVelocity.meanConcentricVelocity(rep(1f), dt)
        assertTrue(fast > slow && slow > 0)
        assertEquals(4, BarVelocity.estimatedRir(listOf(0.5, 0.49, 0.48)))
        assertEquals(2, BarVelocity.estimatedRir(listOf(0.5, 0.45, 0.37)))
        assertEquals(0, BarVelocity.estimatedRir(listOf(0.5, 0.4, 0.28)))
        assertNull(BarVelocity.estimatedRir(listOf(0.5)))
        assertEquals(8.0, BarVelocity.rpeFromRir(2), 0.0)
        assertEquals(1.1f, BarVelocity.calibratedThreshold(listOf(2f, 2f, 2f)), 0.001f)
        assertEquals(1.2f, BarVelocity.calibratedThreshold(listOf(2f)), 0.0f)
    }
}
