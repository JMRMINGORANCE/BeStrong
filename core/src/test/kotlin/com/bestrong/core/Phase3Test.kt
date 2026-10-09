package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.OneRepMax
import com.bestrong.core.stats.Scores
import com.bestrong.core.stats.Sex
import com.bestrong.core.stats.Strength
import com.bestrong.core.stats.StrengthLevel
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3Test {
    private val day = 86_400_000L
    private fun ex(id: String) = ExerciseLibrary.byId(id)!!
    private fun w(at: Long, id: String, kg: Double, reps: Int = 5, sets: Int = 3, rpe: Double? = null) = Workout(
        name = "w",
        startedAtEpochMs = at,
        finishedAtEpochMs = at + 3_600_000,
        exercises = listOf(WorkoutExercise(exercise = ex(id), sets = List(sets) { WorkoutSet(weightKg = kg, reps = reps, completed = true, rpe = rpe) })),
    )

    @Test fun oneRepMaxFormulasAndTable() {
        assertEquals(116.67, OneRepMax.estimate(100.0, 5, OneRepMax.Formula.EPLEY), 0.01)
        assertEquals(112.5, OneRepMax.estimate(100.0, 5, OneRepMax.Formula.BRZYCKI), 0.01)
        assertEquals(100.0, OneRepMax.estimate(100.0, 1, OneRepMax.Formula.MAYHEW), 0.0)
        val avg = OneRepMax.average(100.0, 5)
        assertTrue(avg in 110.0..120.0)
        assertEquals(80.0, OneRepMax.table(100.0).first { it.first == 80 }.third, 0.0)
        assertEquals(6, OneRepMax.repsAt(120.0, 100.0))
    }

    @Test fun scoresAreInExpectedRange() {
        // 600 kg de total con 83 kg: DOTS ≈ 405, Wilks ≈ 400, IPF GL ≈ 84
        assertEquals(405.0, Scores.dots(600.0, 83.0, Sex.MALE), 3.0)
        assertEquals(400.0, Scores.wilks(600.0, 83.0, Sex.MALE), 5.0)
        assertEquals(84.0, Scores.ipfGl(600.0, 83.0, Sex.MALE), 2.0)
        assertEquals(Triple(182.5, 192.5, 202.5), Scores.attempts(200.0))
    }

    @Test fun strengthLevels() {
        val s = Strength.standing("bench_press", 100.0, 80.0, Sex.MALE)!!
        assertEquals(StrengthLevel.INTERMEDIATE, s.level)
        assertEquals(140.0, s.nextLevelKg!!, 0.001)
        assertNull(Strength.standing("bench_press", 30.0, 80.0, Sex.MALE)!!.level)
        assertNull(Strength.standing("lateral_raise_unknown", 30.0, 80.0, Sex.MALE))
    }

    @Test fun balanceEffectiveVolumeAndTimeline() {
        val now = 100 * day
        val history = listOf(w(now - day, "bench_press", 80.0, sets = 12), w(now - 2 * day, "barbell_row", 60.0, sets = 4), w(now - 3 * day, "squat", 100.0, sets = 4))
        val b = Strength.balance(history, now)
        assertTrue(b.pushPullImbalanced)
        assertEquals(16.0, b.upper, 0.0)
        val easy = w(0, "bench_press", 50.0, rpe = 5.0)
        assertEquals(0.0, Strength.effectiveSets(easy), 0.0)
        assertEquals(3.0, Strength.effectiveSets(w(0, "bench_press", 50.0)), 0.0)
        val timeline = Strength.recordTimeline(listOf(w(0, "bench_press", 60.0), w(day, "bench_press", 70.0), w(2 * day, "bench_press", 65.0)))
        assertTrue(timeline.isNotEmpty())
        assertTrue(timeline.all { it.dateEpochMs >= day && it.dateEpochMs < 2 * day })
    }

    @Test fun achievementsPredictionAndSummaries() {
        val zone = ZoneOffset.UTC
        val history = (0 until 12).map { w(it * 7 * day, "bench_press", 60.0 + it * 2.5) }
        val today = LocalDate.ofEpochDay(11 * 7L)
        val a = Strength.achievements(history, today, zone, 80.0)
        assertTrue(a.first { it.id == "workouts_10" }.unlocked)
        assertFalse(a.first { it.id == "workouts_50" }.unlocked)
        assertNotNull(a.firstOrNull { it.id == "bench_bw" })
        val points = history.map { it.startedAtEpochMs to (it.exercises[0].sets[0].weightKg) }
        val eta = Strength.predictDate(points, 100.0)!!
        assertTrue(eta > history.last().startedAtEpochMs)
        assertNull(Strength.predictDate(points.map { it.first to 60.0 }, 100.0))
        val week = Strength.weekSummary(history, today, zone)
        assertEquals(1, week.workouts)
        assertTrue(week.volumeChange!! > 0)
    }

    @Test fun daysSinceSorenessAndCurve() {
        val zone = ZoneOffset.UTC
        val now = 10 * day
        val history = listOf(w(now - 12 * 3_600_000L, "squat", 100.0, sets = 10), w(now - 5 * day, "bench_press", 80.0))
        val since = Strength.daysSinceTrained(history, LocalDate.ofEpochDay(10), zone)
        assertEquals(1, since[MuscleGroup.QUADRICEPS])
        assertEquals(5, since[MuscleGroup.CHEST])
        assertNull(since[MuscleGroup.CALVES])
        val sore = Strength.soreness(history, now)
        assertTrue(sore[MuscleGroup.QUADRICEPS]!! > 0.5)
        assertNull(sore[MuscleGroup.CHEST])
        val curve = Strength.strengthCurve("bench_press", listOf(w(0, "bench_press", 80.0, reps = 5), w(day, "bench_press", 90.0, reps = 1)))
        assertEquals(mapOf(1 to 90.0, 5 to 80.0), curve)
        assertEquals(SetType.NORMAL, history[0].exercises[0].sets[0].type)
    }
}
