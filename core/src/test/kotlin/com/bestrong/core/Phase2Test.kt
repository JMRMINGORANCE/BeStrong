package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.WorkoutStats
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2Test {
    private fun ex(id: String) = ExerciseLibrary.byId(id)!!
    private fun done(w: Double, r: Int, type: SetType = SetType.NORMAL, assisted: Boolean = false) = WorkoutSet(weightKg = w, reps = r, completed = true, type = type, assisted = assisted)
    private fun workout(at: Long, vararg sets: WorkoutSet) = Workout(
        name = "x", startedAtEpochMs = at, finishedAtEpochMs = at + 1,
        exercises = listOf(WorkoutExercise(exercise = bench, sets = sets.toList())),
    )

    @Test fun partialsNegativesAndAssistedDontCountAsRecords() {
        val history = listOf(
            workout(1, done(100.0, 5)),
            workout(2, done(140.0, 3, SetType.NEGATIVE), done(120.0, 5, assisted = true), done(110.0, 3, SetType.PARTIAL)),
        )
        val best = WorkoutStats.personalRecords(history)["bench_press"]!![RecordType.HEAVIEST_WEIGHT]!!.value
        assertEquals(100.0, best, 0.0)
    }

    @Test fun unilateralAndAssistedVolume() {
        val s = WorkoutSet(weightKg = 20.0, reps = 10, repsRight = 8, completed = true)
        assertEquals(18, s.totalReps)
        assertEquals(360.0, s.volume, 0.0)
        assertEquals(0.2, s.sideImbalance!!, 1e-9)
        // Dominada asistida: −20 kg no resta volumen
        assertEquals(0.0, WorkoutSet(weightKg = -20.0, reps = 8, completed = true).volume, 0.0)
        assertTrue(ex("lunge").unilateral)
        assertEquals(TrackingType.DISTANCE, ex("farmers_walk").tracking)
    }

    @Test fun skipAndRemainingTime() {
        var w = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(ex("squat"), ex("bench_press"), ex("lateral_raise")))
        w = WorkoutEditor.skipExercise(w, w.exercises[0].id)
        assertEquals(listOf("bench_press", "lateral_raise", "squat"), w.exercises.map { it.exercise.id })
        // 9 series × 40 s + descansos de 8 de ellas (3×180 sentadilla… según orden)
        val expected = 9 * 40 + (w.exercises.flatMap { we -> List(3) { we.restSeconds } }.dropLast(1).sum())
        assertEquals(expected, WorkoutEditor.estimatedRemainingSeconds(w))
    }

    @Test fun compareSearchHeatmapImbalance() {
        val zone = ZoneOffset.UTC
        val day = LocalDate.of(2026, 3, 2).atStartOfDay(zone).toInstant().toEpochMilli()
        val a = workout(day, done(80.0, 8))
        val b = workout(day + 86_400_000L * 2, done(85.0, 8)).copy(notes = "buena sesión")
        val cmp = WorkoutStats.compareWithPrevious(b, listOf(a, b)).single()
        assertEquals(40.0, cmp.volumeDelta, 1e-9)
        assertTrue(cmp.best1rmDelta > 0)
        assertEquals(listOf(b), WorkoutStats.search(listOf(a, b), "buena"))
        assertEquals(2, WorkoutStats.search(listOf(a, b), "press").size)
        assertEquals(2, WorkoutStats.dailyCounts(listOf(a, b), 2026, zone).size)
        val uni = Workout(name = "u", startedAtEpochMs = 0, exercises = listOf(WorkoutExercise(exercise = ex("lunge"), sets = listOf(WorkoutSet(reps = 10, repsRight = 7, completed = true)))))
        assertEquals("lunge", WorkoutStats.imbalances(uni).single().first)
    }

    @Test fun synonymsAndFamilies() {
        val found = ExerciseLibrary.search(ExerciseLibrary.all, "press de hombro").map { it.id }
        assertTrue("overhead_press" in found)
        assertTrue("pull_up" in ExerciseLibrary.search(ExerciseLibrary.all, "dominadas").map { it.id })
        assertTrue("db_bench_press" in ExerciseLibrary.familyOf("bench_press"))
        assertFalse("squat" in ExerciseLibrary.familyOf("bench_press"))
    }
}
