package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.RoutineSet
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import org.junit.Assert.assertEquals
import org.junit.Test

class Round150Test {
    private val bench = ExerciseLibrary.byId("bench_press")!!
    private val squat = ExerciseLibrary.byId("squat")!!

    @Test fun skippedWarmupIsNotTheNextSet() {
        val sets = listOf(
            WorkoutSet(id = "w1", type = SetType.WARMUP),
            WorkoutSet(id = "n1", weightKg = 100.0, reps = 5),
            WorkoutSet(id = "n2", weightKg = 100.0, reps = 5),
        )
        val w = Workout(id = "w", name = "W", startedAtEpochMs = 0, exercises = listOf(WorkoutExercise(id = "e", exercise = bench, sets = sets)))
        val done = WorkoutEditor.toggleSetCompleted(w, "e", "n1", nowEpochMs = 1_000)
        assertEquals("n2", WorkoutEditor.upcomingSet(done)?.second?.id)
    }

    @Test fun substituteKeepsSuperset() {
        fun ex(id: String, e: com.bestrong.core.model.Exercise) = WorkoutExercise(
            id = id, exercise = e, supersetId = "g",
            sets = listOf(WorkoutSet(id = "$id-1", weightKg = 50.0, reps = 8, completed = true, completedAtEpochMs = 1), WorkoutSet(id = "$id-2", weightKg = 50.0, reps = 8)),
        )
        val w = Workout(id = "w", name = "W", startedAtEpochMs = 0, exercises = listOf(ex("a", bench), ex("b", squat)))
        val out = WorkoutEditor.substitute(w, "a", ExerciseLibrary.byId("dumbbell_bench_press") ?: ExerciseLibrary.all.first { it.id != "bench_press" }, emptyList(), 2.5)
        assertEquals(3, out.exercises.size)
        assertEquals(listOf("g", "g"), out.exercises.drop(1).map { it.supersetId })
    }

    @Test fun routinePrefillKeepsAssistanceAndRightReps() {
        val r = Routine(name = "R", exercises = listOf(RoutineExercise(exercise = bench, sets = listOf(RoutineSet(), RoutineSet(), RoutineSet(type = SetType.DROP)))))
        val prev = listOf(
            WorkoutSet(weightKg = -20.0, reps = 8, repsRight = 7, completed = true),
            WorkoutSet(weightKg = 100.0, reps = 8, type = SetType.DROP, completed = true),
        )
        val sets = r.toWorkout(0, { prev }).exercises.single().sets
        assertEquals(-20.0, sets[0].weightKg, 0.0)
        assertEquals(7, sets[0].repsRight)
        // La segunda efectiva (saltada la última vez) toma la última efectiva, no el drop set
        assertEquals(-20.0, sets[1].weightKg, 0.0)
        assertEquals(100.0, sets[2].weightKg, 0.0)
    }
}
