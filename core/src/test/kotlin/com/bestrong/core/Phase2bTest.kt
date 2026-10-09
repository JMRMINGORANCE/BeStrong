package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.training.NextTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2bTest {
    private val bench = ExerciseLibrary.byId("bench_press")!!

    private fun workout(vararg sets: WorkoutSet) =
        Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(WorkoutExercise(exercise = bench, sets = sets.toList())))

    @Test fun changingFirstSetFillsPendingSetsWithOldValue() {
        val w = workout(
            WorkoutSet(weightKg = 20.0, reps = 10, type = SetType.WARMUP),
            WorkoutSet(weightKg = 60.0, reps = 8),
            WorkoutSet(weightKg = 60.0, reps = 8),
            WorkoutSet(weightKg = 55.0, reps = 8),
            WorkoutSet(weightKg = 60.0, reps = 8, completed = true),
        )
        val we = w.exercises[0]
        val out = WorkoutEditor.updateSetPropagating(w, we.id, we.sets[1].id, weightKg = 62.5).exercises[0].sets
        assertEquals(listOf(20.0, 62.5, 62.5, 55.0, 60.0), out.map { it.weightKg })
        val reps = WorkoutEditor.updateSetPropagating(w, we.id, we.sets[1].id, reps = 6).exercises[0].sets
        assertEquals(listOf(10, 6, 6, 6, 8), reps.map { it.reps })
    }

    @Test fun steppersAndCleanup() {
        val w = workout(WorkoutSet(weightKg = 1.0, reps = 1))
        val we = w.exercises[0]
        val s = we.sets[0].id
        assertEquals(0.0, WorkoutEditor.stepWeight(w, we.id, s, -2.5).exercises[0].sets[0].weightKg, 0.0)
        assertEquals(-1.5, WorkoutEditor.stepWeight(w, we.id, s, -2.5, allowNegative = true).exercises[0].sets[0].weightKg, 1e-9)
        assertEquals(3.5, WorkoutEditor.stepWeight(w, we.id, s, 2.5).exercises[0].sets[0].weightKg, 1e-9)
        assertEquals(0, WorkoutEditor.stepReps(w, we.id, s, -5).exercises[0].sets[0].reps)
        val empty = WorkoutEditor.removeSet(w, we.id, s)
        assertTrue(WorkoutEditor.cleanupFinished(empty).exercises.isEmpty())
    }

    @Test fun linkedVariantsShareRecords() {
        val incline = ExerciseLibrary.byId("incline_bench_press")!!
        val w1 = workout(WorkoutSet(weightKg = 80.0, reps = 5, completed = true))
        val w2 = Workout(name = "i", startedAtEpochMs = 1, exercises = listOf(WorkoutExercise(exercise = incline, sets = listOf(WorkoutSet(weightKg = 90.0, reps = 5, completed = true)))))
        val history = listOf(w2.copy(finishedAtEpochMs = 2), w1.copy(finishedAtEpochMs = 1))
        val separate = WorkoutStats.personalRecords(history)["bench_press"]!!
        val merged = WorkoutStats.personalRecords(WorkoutStats.mergeVariants(history, "bench_press", ExerciseLibrary.familyOf("bench_press")))["bench_press"]!!
        assertEquals(80.0, separate[RecordType.HEAVIEST_WEIGHT]!!.value, 0.0)
        assertEquals(90.0, merged[RecordType.HEAVIEST_WEIGHT]!!.value, 0.0)
    }

    @Test fun nextTimeDoubleProgression() {
        fun we(vararg sets: WorkoutSet) = workout(*sets).exercises[0]
        val up = NextTime.suggest(we(WorkoutSet(weightKg = 80.0, reps = 8, completed = true), WorkoutSet(weightKg = 80.0, reps = 8, completed = true, rpe = 9.0)), 2.5)!!
        assertEquals(82.5, up.weightKg, 0.0)
        assertEquals(1, up.direction)
        val keep = NextTime.suggest(we(WorkoutSet(weightKg = 80.0, reps = 8, completed = true), WorkoutSet(weightKg = 80.0, reps = 6, completed = true)), 2.5)!!
        assertEquals(0, keep.direction)
        val down = NextTime.suggest(we(WorkoutSet(weightKg = 80.0, reps = 8, completed = true), WorkoutSet(weightKg = 80.0, reps = 5, completed = true, rpe = 10.0)), 2.5)!!
        assertEquals(-1, down.direction)
        assertEquals(75.0, down.weightKg, 0.0)
    }
}
