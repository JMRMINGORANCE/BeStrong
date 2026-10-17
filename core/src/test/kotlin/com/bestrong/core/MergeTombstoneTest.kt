package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.sync.WorkoutMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTombstoneTest {
    private val set = WorkoutSet(id = "s1", weightKg = 60.0, reps = 8)
    private val base = Workout(
        id = "w",
        name = "W",
        startedAtEpochMs = 0,
        exercises = listOf(WorkoutExercise(id = "e1", exercise = ExerciseLibrary.byId("bench_press")!!, sets = listOf(set, WorkoutSet(id = "s2")))),
    )

    @Test fun untickOnOneSideWins() {
        val done = WorkoutEditor.toggleSetCompleted(base, "e1", "s1", nowEpochMs = 1_000).copy(updatedAtEpochMs = 1_000)
        val undone = WorkoutEditor.uncompleteSet(done, "e1", "s1", nowEpochMs = 2_000).copy(updatedAtEpochMs = 2_000)
        // El otro lado aún la tiene completada (y es incluso «más reciente» por reloj): no vuelve a quedar hecha
        val stale = done.copy(updatedAtEpochMs = 3_000)
        assertFalse(WorkoutMerge.merge(undone, stale).exercises[0].sets[0].completed)
        assertFalse(WorkoutMerge.merge(stale, undone).exercises[0].sets[0].completed)
        // Completarla otra vez después sí cuenta
        val again = WorkoutEditor.toggleSetCompleted(undone, "e1", "s1", nowEpochMs = 4_000).copy(updatedAtEpochMs = 4_000)
        assertTrue(WorkoutMerge.merge(again, stale).exercises[0].sets[0].completed)
    }

    @Test fun deletedCompletedSetDoesNotComeBack() {
        val done = WorkoutEditor.toggleSetCompleted(base, "e1", "s1", nowEpochMs = 1_000).copy(updatedAtEpochMs = 1_000)
        val removed = WorkoutEditor.removeSet(done, "e1", "s1").copy(updatedAtEpochMs = 2_000)
        val merged = WorkoutMerge.merge(done, removed)
        assertEquals(listOf("s2"), merged.exercises[0].sets.map { it.id })
        assertEquals(listOf("s2"), WorkoutMerge.merge(removed, done.copy(updatedAtEpochMs = 3_000)).exercises[0].sets.map { it.id })
        // Deshacer el borrado la recupera
        val restored = WorkoutEditor.undoRemoval(done, removed)(removed).copy(updatedAtEpochMs = 4_000)
        assertTrue(WorkoutMerge.merge(done, restored).exercises[0].sets.any { it.id == "s1" })
    }

    @Test fun deletedExerciseDoesNotComeBack() {
        val done = WorkoutEditor.toggleSetCompleted(base, "e1", "s1", nowEpochMs = 1_000).copy(updatedAtEpochMs = 1_000)
        val removed = WorkoutEditor.removeExercise(done, "e1").copy(updatedAtEpochMs = 2_000)
        assertTrue(WorkoutMerge.merge(done, removed).exercises.isEmpty())
    }
}
