package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.CaloriesSource
import com.bestrong.core.model.HeartRateSample
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.Sex
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.sync.WorkoutMerge
import com.bestrong.core.training.BodyProfile
import com.bestrong.core.training.Energy
import com.bestrong.core.training.HeartRateTracker
import kotlin.math.roundToInt
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

    private fun cardio(id: String, durationSec: Int, distanceM: Double = 0.0, endOffsetSec: Int? = null) = WorkoutExercise(
        exercise = ExerciseLibrary.byId(id)!!,
        sets = listOf(
            WorkoutSet(durationSec = durationSec, distanceM = distanceM, completed = true, completedAtEpochMs = endOffsetSec?.let { it * 1000L }),
        ),
    )

    private fun workout(seconds: Int, vararg exercises: WorkoutExercise) =
        Workout(name = "W", startedAtEpochMs = 0, finishedAtEpochMs = seconds * 1000L, exercises = exercises.toList())

    @Test
    fun treadmill_usesSpeed_acsm() {
        // 5 km en 30 min = 166,7 m/min (corriendo): VO₂ = 3,5 + 0,2·v = 36,8 ml/kg/min → 442 kcal
        val r = Energy.forWorkout(workout(1800, cardio("treadmill", 1800, 5000.0)), me)!!
        assertEquals(CaloriesSource.ESTIMATE, r.source)
        assertEquals(442, r.totalKcal)
        // 3 km en 30 min (100 m/min, caminando): VO₂ = 3,5 + 0,1·v = 13,5 → 162 kcal
        assertEquals(162, Energy.forWorkout(workout(1800, cardio("treadmill", 1800, 3000.0)), me)!!.totalKcal)
    }

    @Test
    fun rower_usesPower() {
        // 2000 m en 8 min (2:00/500 m) ≈ 202 W → ≈ 16 kcal/min
        assertEquals(129, Energy.forWorkout(workout(480, cardio("rowing_machine", 480, 2000.0)), me)!!.totalKcal)
    }

    @Test
    fun machineWithoutData_usesCompendium_andHeartRateWhenPresent() {
        // Elíptica 20 min sin pulso: 5 MET × basal/h × 1/3 h ≈ 124
        assertEquals(124, Energy.forWorkout(workout(1200, cardio("elliptical", 1200)), me)!!.totalKcal)
        // Con pulso 150 en ese tramo: VO₂ por reserva de FC (VO₂máx de Uth ≈ 47,7) ≈ 34,8 → ≈ 278
        val hr = List(120) { HeartRateSample(it * 10, 150) }
        val r = Energy.forWorkout(workout(1200, cardio("elliptical", 1200, endOffsetSec = 1200)), me, heartRate = hr)!!
        assertEquals(CaloriesSource.HEART_RATE, r.source)
        assertEquals(278, r.totalKcal)
    }

    @Test
    fun mixedRoutine_splitsCardioAndStrength() {
        val bench = WorkoutExercise(
            exercise = ExerciseLibrary.byId("bench_press")!!,
            sets = List(20) { WorkoutSet(weightKg = 60.0, reps = 8, completed = true) },
        )
        // 60 min: 20 min de bici estática (7 MET) + 40 min de fuerza con 20 series (denso: 5 MET)
        val r = Energy.forWorkout(workout(3600, cardio("stationary_bike", 1200), bench), me)!!
        val bmrPerHour = 1780.0 / 24
        assertEquals((7.0 * bmrPerHour / 3 + 5.0 * bmrPerHour * 2 / 3).roundToInt(), r.totalKcal)
        // Con calorías del reloj, mandan esas
        assertEquals(500, Energy.forWorkout(workout(3600, cardio("stationary_bike", 1200), bench), me, watchKcal = 500)!!.totalKcal)
    }

    @Test
    fun implausibleDistance_isIgnored() {
        // 50 km en 30 min en cinta: error de tecleo → MET de la cinta (8) en vez de la velocidad
        val r = Energy.forWorkout(workout(1800, cardio("treadmill", 1800, 50_000.0)), me)!!
        assertEquals((8.0 * 1780.0 / 24 / 2).roundToInt(), r.totalKcal)
    }

    @Test
    fun cardioTimer_measuresTime_andOnlyOneRuns() {
        val w0 = workout(0, cardio("treadmill", 1200), cardio("elliptical", 1200)).copy(finishedAtEpochMs = null)
        val (t, e) = w0.exercises
        var w = com.bestrong.core.session.WorkoutEditor.startCardio(w0, t.id, t.sets[0].id, nowEpochMs = 1_000)
            .let { it.copy(exercises = it.exercises.map { we -> we.copy(sets = we.sets.map { s -> s.copy(completed = false) }) }) }
        w = com.bestrong.core.session.WorkoutEditor.startCardio(w, t.id, t.sets[0].id, nowEpochMs = 1_000)
        assertEquals(t.id, com.bestrong.core.session.WorkoutEditor.runningCardio(w)!!.first.id)
        // Arrancar otro para el primero sin guardarlo
        w = com.bestrong.core.session.WorkoutEditor.startCardio(w, e.id, e.sets[0].id, nowEpochMs = 2_000)
        assertEquals(e.id, com.bestrong.core.session.WorkoutEditor.runningCardio(w)!!.first.id)
        assertTrue(!w.exercises[0].sets[0].completed && w.exercises[0].sets[0].startedAtEpochMs == null)
        // Marcarlo como hecho lo termina con el tiempo medido (15 min)
        w = com.bestrong.core.session.WorkoutEditor.toggleSetCompleted(w, e.id, e.sets[0].id, nowEpochMs = 2_000 + 900_000)
        val done = w.exercises[1].sets[0]
        assertTrue(done.completed)
        assertEquals(900, done.durationSec)
        assertNull(com.bestrong.core.session.WorkoutEditor.runningCardio(w))
    }
}
