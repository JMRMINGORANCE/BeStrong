package com.bestrong.core

import com.bestrong.core.model.CaloriesSource
import com.bestrong.core.model.HeartRateSample
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.Sex
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.sync.WorkoutMerge
import com.bestrong.core.training.BodyProfile
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

    private val me = BodyProfile(weightKg = 80.0, heightCm = 180.0, age = 30, sex = Sex.MALE, restingHr = 60)

    @Test
    fun bmr_mifflinStJeor() {
        // 10×80 + 6,25×180 − 5×30 + 5 = 1780 kcal/día
        assertEquals(1780.0, Energy.bmrKcalPerDay(me)!!, 0.01)
        assertEquals(1780.0 - 166, Energy.bmrKcalPerDay(me.copy(sex = Sex.FEMALE))!!, 0.01)
        assertNull(Energy.bmrKcalPerDay(me.copy(heightCm = null)))
    }

    @Test
    fun watchCalories_areKept_andActiveSubtractsResting() {
        val r = Energy.compute(activeSeconds = 3600, completedSets = 20, profile = me, watchKcal = 300)!!
        assertEquals(CaloriesSource.WATCH, r.source)
        assertEquals(300, r.totalKcal)
        // 300 − 1780/24 ≈ 226
        assertEquals(226, r.activeKcal)
    }

    @Test
    fun metEstimate_isCorrectedWithOwnBmr_andDensity() {
        // 20 series en 1 h (descansos largos): 3,5 MET × 1780/24 ≈ 260 kcal
        val relaxed = Energy.compute(3600, 20, me)!!
        assertEquals(CaloriesSource.ESTIMATE, relaxed.source)
        assertEquals(260, relaxed.totalKcal)
        assertEquals(186, relaxed.activeKcal)
        // 40 series en 1 h (≈1 min de descanso): 5 MET ≈ 371 kcal
        assertEquals(371, Energy.compute(3600, 40, me)!!.totalKcal)
        // Sin altura ni edad: MET estándar (1 kcal/kg/h) → 3,5 × 80 = 280
        assertEquals(280, Energy.compute(3600, 20, BodyProfile(weightKg = 80.0))!!.totalKcal)
        assertNull(Energy.compute(3600, 20, BodyProfile()))
    }

    @Test
    fun heartRate_setsTheIntensity_notAnAerobicFormula() {
        // Pulso medio 150 con reposo 60 y máximo 187 (Tanaka): 71 % de reserva → 6 MET ≈ 445 kcal
        val dense = List(360) { HeartRateSample(it * 10, 150) }
        val r = Energy.compute(3600, 20, me, heartRate = dense)!!
        assertEquals(CaloriesSource.HEART_RATE, r.source)
        assertEquals(445, r.totalKcal)
        // Pulso bajo (95): 28 % → 3,5 MET, igual que la estimación sin pulso
        assertEquals(260, Energy.compute(3600, 20, me, heartRate = List(360) { HeartRateSample(it * 10, 95) })!!.totalKcal)
        // Pocas lecturas (una cada 10 min): no representan el entreno → estimación por densidad
        val sparse = List(6) { HeartRateSample(it * 600, 150) }
        assertEquals(CaloriesSource.ESTIMATE, Energy.compute(3600, 20, me, heartRate = sparse)!!.source)
    }
}
