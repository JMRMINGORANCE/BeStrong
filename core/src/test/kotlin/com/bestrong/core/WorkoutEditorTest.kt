package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.RoutineSet
import com.bestrong.core.model.SetType
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.sync.SyncContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEditorTest {

    @Test fun addExerciseUsesPreviousSets() {
        val w = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(bench)) { listOf(done(80.0, 8)) }
        val set = w.exercises.single().sets.single()
        assertEquals(80.0, set.weightKg, 0.0)
        assertEquals(false, set.completed)
    }

    @Test fun addSetCopiesLast() {
        var w = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(bench))
        val we = w.exercises.single()
        w = WorkoutEditor.updateSet(w, we.id, we.sets.last().id) { it.copy(weightKg = 60.0, reps = 12) }
        w = WorkoutEditor.addSet(w, we.id)
        val last = w.exercises.single().sets.last()
        assertEquals(4, w.exercises.single().sets.size)
        assertEquals(60.0, last.weightKg, 0.0)
        assertEquals(12, last.reps)
    }

    @Test fun finishDropsIncompleteSetsAndEmptyExercises() {
        var w = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(bench, squat))
        val we = w.exercises.first()
        w = WorkoutEditor.toggleSetCompleted(w, we.id, we.sets.first().id)
        val finished = WorkoutEditor.finish(w, 1000)
        assertEquals(1, finished.exercises.size)
        assertEquals(1, finished.exercises.single().sets.size)
        assertEquals(1000L, finished.finishedAtEpochMs)
    }

    @Test fun nextPendingSetAndCycleType() {
        var w = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(bench))
        val we = w.exercises.single()
        we.sets.forEach { w = WorkoutEditor.toggleSetCompleted(w, we.id, it.id) }
        assertNull(WorkoutEditor.nextPendingSet(w))
        w = WorkoutEditor.cycleSetType(w, we.id, we.sets.first().id)
        assertEquals(SetType.WARMUP, w.exercises.single().sets.first().type)
    }

    @Test fun routineToWorkoutPrefillsFromHistory() {
        val routine = Routine(name = "Push", exercises = listOf(RoutineExercise(exercise = bench, sets = List(2) { RoutineSet(50.0, 10) })))
        val w = routine.toWorkout(0, previousSets = { listOf(done(70.0, 6)) })
        assertEquals(listOf(70.0, 50.0), w.exercises.single().sets.map { it.weightKg })
        assertEquals(routine.id, w.routineId)
        val back = Routine.fromWorkout(w)
        assertEquals(2, back.exercises.single().sets.size)
    }

    @Test fun librarySearchIgnoresAccents() {
        val all = ExerciseLibrary.all
        assertTrue(ExerciseLibrary.search(all, "press frances").any { it.id == "skull_crusher" })
        assertTrue(
            ExerciseLibrary.search(all, "", muscle = MuscleGroup.CALVES).all {
                it.primaryMuscle == MuscleGroup.CALVES || MuscleGroup.CALVES in it.secondaryMuscles
            },
        )
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test fun serializationRoundTrip() {
        val w = finishedWorkout("x", java.time.LocalDate.of(2026, 1, 1), bench to listOf(done(80.0, 5)))
        assertEquals(w, SyncContract.decodeWorkout(SyncContract.encodeWorkout(w)))
        assertEquals(listOf(w), SyncContract.decodeWorkouts(SyncContract.encodeWorkouts(listOf(w))))
    }
}
