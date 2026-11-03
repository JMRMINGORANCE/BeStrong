package com.bestrong.core

import com.bestrong.core.sensors.BarVelocity
import com.bestrong.core.sensors.RepConfidence
import com.bestrong.core.sensors.RepDetector
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Detección de repeticiones con señales sintéticas de la muñeca (aceleración vertical suavizada, m/s²). */
class RepDetectorTest {
    private val stepMs = 20L

    /** Señal en reposo (con algo de ruido) durante [ms]. */
    private fun rest(ms: Long, noise: Float = 0.05f): List<Float> = List((ms / stepMs).toInt()) { i -> if (i % 2 == 0) noise else -noise }

    /** Una repetición: pico positivo con forma de seno de [peak] m/s² y [width] ms, y luego su bajada. */
    private fun rep(peak: Float, width: Long = 400, gap: Long = 1_200): List<Float> {
        val n = (width / stepMs).toInt()
        val up = List(n) { i -> (peak * sin(PI * i / n)).toFloat() }
        return up + rest(gap)
    }

    /** Un golpe: un pico alto pero muy corto (dejar la barra, chocar). */
    private fun knock(peak: Float = 6f): List<Float> = listOf(peak, peak) + rest(600)

    private fun run(d: RepDetector, signal: List<Float>, from: Long = 0L): Long {
        var t = from
        signal.forEach { v ->
            d.onSample(t, v)
            t += stepMs
        }
        return t
    }

    @Test fun countsRegularReps() {
        val d = RepDetector(threshold = 1.2f).apply { reset(0) }
        run(d, rest(1_000) + List(8) { rep(2.5f) }.flatten())
        assertEquals(8, d.count)
        assertEquals(RepConfidence.HIGH, d.confidence())
    }

    @Test fun ignoresGrabbingTheBarAtStart() {
        val d = RepDetector(threshold = 1.2f).apply { reset(0) }
        // Al coger la barra hay un tirón dentro de la ventana de arranque: no es una rep
        run(d, rep(3f, width = 300, gap = 300) + List(5) { rep(2.5f) }.flatten())
        assertEquals(5, d.count)
    }

    @Test fun shortKnocksAreNotReps() {
        val d = RepDetector(threshold = 1.2f).apply { reset(0) }
        run(d, rest(1_000) + List(4) { rep(2.5f) }.flatten() + knock() + rep(2.5f))
        assertEquals(5, d.count)
        assertEquals(1, d.rejected)
    }

    @Test fun rerackingSpikeIsDiscarded() {
        val d = RepDetector(threshold = 1.2f).apply { reset(0) }
        // Al terminar, la barra vuelve a los soportes: pico largo y muy por encima del resto
        run(d, rest(1_000) + List(6) { rep(2.4f) }.flatten() + rep(9f, width = 300))
        assertEquals(6, d.count)
    }

    @Test fun bouncesInsideOneRepCountOnce() {
        val d = RepDetector(threshold = 1.2f).apply { reset(0) }
        // Cada rep con un rebote pegado (dos picos a 200 ms): sigue siendo una
        val bouncy = List(5) { rep(2.5f, gap = 200) + rep(2.0f, width = 200, gap = 1_200) }.flatten()
        run(d, rest(1_000) + bouncy)
        assertEquals(5, d.count)
    }

    @Test fun manualCorrectionNeverGoesNegative() {
        val d = RepDetector(threshold = 1.2f).apply { reset(0) }
        run(d, rest(1_000) + List(3) { rep(2.5f) }.flatten())
        d.adjust(+1)
        assertEquals(4, d.count)
        repeat(10) { d.adjust(-1) }
        assertEquals(0, d.count)
        d.reset(100_000)
        assertEquals(0, d.count)
    }

    @Test fun irregularSeriesHasLowConfidence() {
        val d = RepDetector(threshold = 1.2f).apply { reset(0) }
        run(d, rest(1_000) + rep(2.5f, gap = 700) + rep(2.5f, gap = 4_000) + rep(2.5f, gap = 600) + rep(2.5f, gap = 5_000))
        assertEquals(RepConfidence.LOW, d.confidence())
    }

    @Test fun calibrationSetsThresholdFromTestReps() {
        val d = RepDetector()
        d.startCalibration(0)
        run(d, rest(1_000) + List(5) { rep(2f) }.flatten())
        val c = d.finishCalibration()
        assertTrue(c.ok)
        assertEquals(5, c.repsSeen)
        // 55 % del pico medio (~2 m/s²)
        assertEquals(1.1f, c.threshold, 0.1f)
        assertFalse(d.calibrating)
    }

    @Test fun calibrationWithoutClearRepsKeepsThreshold() {
        val d = RepDetector(threshold = 1.2f)
        d.startCalibration(0)
        run(d, rest(1_000) + rep(2f) + rest(3_000))
        val c = d.finishCalibration()
        assertFalse(c.ok)
        assertEquals(1.2f, d.threshold, 0.0001f)
    }

    @Test fun implausibleVelocitiesDoNotDecideRir() {
        assertNull(BarVelocity.estimatedRir(listOf(0.5, 9.0)))
        assertEquals(4, BarVelocity.estimatedRir(listOf(0.50, 0.48, 7.0)))
    }
}
