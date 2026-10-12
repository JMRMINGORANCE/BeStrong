package com.bestrong.core

import com.bestrong.core.model.HeartRateSample
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.sync.WorkoutMerge
import com.bestrong.core.training.Energy
import com.bestrong.core.training.HeartRateTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyTest {

    @Test
    fun average_isTimeWeighted_notBiasedByBatches() {
        val t = HeartRateTracker(startedAtEpochMs = 0)
        // 60 s a 100 ppm con una lectura por segundo…
        for (s in 0 until 60) t.add(100, s * 1000L)
        // …y luego 60 s a 160 ppm, pero llegan solo 4 lecturas (lote con la pantalla apagada)
        listOf(60, 75, 90, 105).forEach { t.add(160, it * 1000L) }
        t.add(160, 120_000)
        // Media simple de muestras ≈ 104; la real (mitad del tiempo a cada pulso) es 130
        assertEquals(130, t.average)
        assertEquals(160, t.max)
    }

    @Test
    fun gaps_areCapped_andInvalidReadingsIgnored() {
        val t = HeartRateTracker(startedAtEpochMs = 0, maxGapMs = 30_000)
        t.add(90, 0)
        t.add(0, 1_000) // sensor sin contacto
        t.add(300, 2_000) // lectura imposible
        t.add(150, 600_000) // 10 min sin datos: solo cuentan 30 s a 90
        t.add(150, 630_000)
        assertEquals(120, t.average)
        assertEquals(150, t.max)
    }

    @Test
    fun samples_areBucketedAverages() {
        val t = HeartRateTracker(startedAtEpochMs = 1_000, bucketSec = 10)
        t.add(100, 1_000)
        t.add(110, 5_000)
        t.add(140, 12_000)
        assertEquals(listOf(HeartRateSample(0, 105), HeartRateSample(10, 140)), t.samples())
        assertNull(HeartRateTracker(0).average)
    }

    @Test
    fun strengthCalories_useMetWeightAndActiveTime() {
        // 3,5 MET × 80 kg × 1 h = 280 kcal
        assertEquals(280, Energy.estimateStrengthKcal(3600, 80.0))
        assertEquals(140, Energy.estimateStrengthKcal(1800, 80.0))
        assertNull(Energy.estimateStrengthKcal(3600, null))
    }

    @Test
    fun heartRateSeries_isKeptOnMerge_butNotSentToTheWatch() {
        val samples = listOf(HeartRateSample(0, 100), HeartRateSample(10, 120))
        val w = Workout(id = "a", name = "W", startedAtEpochMs = 0, finishedAtEpochMs = 60_000, heartRateSamples = samples)
        val merged = WorkoutMerge.merge(w, w.copy(heartRateSamples = emptyList(), updatedAtEpochMs = 10))
        assertEquals(samples, merged.heartRateSamples)
        assertTrue(SyncContract.compactHistory(listOf(w)).single().heartRateSamples.isEmpty())
    }
}
