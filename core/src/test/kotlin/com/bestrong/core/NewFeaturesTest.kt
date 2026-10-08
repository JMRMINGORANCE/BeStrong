package com.bestrong.core

import com.bestrong.core.io.Persistence
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.library.Programs
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.MuscleTargets
import com.bestrong.core.stats.VolumeStatus
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.WorkoutMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MergeTest {
    private fun base() = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(bench, squat)).copy(updatedAtEpochMs = 100)

    @Test fun completedSetsFromBothSidesSurvive() {
        val b = base()
        val bench = b.exercises[0]
        // Móvil completa la serie 1 de press banca; reloj completa la serie 2 (más tarde)
        val phone = WorkoutEditor.toggleSetCompleted(b, bench.id, bench.sets[0].id).copy(updatedAtEpochMs = 200)
        val watch = WorkoutEditor.toggleSetCompleted(b, bench.id, bench.sets[1].id).copy(updatedAtEpochMs = 300)
        val merged = WorkoutMerge.merge(phone, watch)
        assertEquals(listOf(true, true, false), merged.exercises[0].sets.map { it.completed })
        assertEquals(300L, merged.updatedAtEpochMs)
        assertEquals(merged, WorkoutMerge.merge(watch, phone))
    }

    @Test fun newerEditsWinForPendingSetsAndDeletions() {
        val b = base()
        val bench = b.exercises[0]
        val older = WorkoutEditor.updateSet(b, bench.id, bench.sets[0].id) { it.copy(weightKg = 60.0) }.copy(updatedAtEpochMs = 200)
        val newer = WorkoutEditor.removeExercise(
            WorkoutEditor.updateSet(b, bench.id, bench.sets[0].id) { it.copy(weightKg = 80.0) }, b.exercises[1].id,
        ).copy(updatedAtEpochMs = 300, name = "Nuevo")
        val merged = WorkoutMerge.merge(older, newer)
        assertEquals(80.0, merged.exercises[0].sets[0].weightKg, 0.0)
        assertEquals(1, merged.exercises.size) // sentadilla sin series hechas: se respeta el borrado
        assertEquals("Nuevo", merged.name)
    }

    @Test fun deletedExerciseWithCompletedWorkIsKept() {
        val b = base()
        val sq = b.exercises[1]
        val older = WorkoutEditor.toggleSetCompleted(b, sq.id, sq.sets[0].id).copy(updatedAtEpochMs = 200)
        val newer = WorkoutEditor.removeExercise(b, sq.id).copy(updatedAtEpochMs = 300)
        assertEquals(2, WorkoutMerge.merge(older, newer).exercises.size)
    }

    @Test fun differentWorkoutsNewestWins() {
        val a = WorkoutEditor.empty(0).copy(updatedAtEpochMs = 10)
        val b = WorkoutEditor.empty(0).copy(updatedAtEpochMs = 20)
        assertEquals(b.id, WorkoutMerge.merge(a, b).id)
    }
}

class PersistenceTest {
    @Test fun roundTripStampsVersion() {
        val w = finishedWorkout("p", LocalDate.of(2026, 1, 1), bench to listOf(done(80.0, 5)))
        val text = Persistence.encodeWorkout(w)
        assertTrue(text.contains("\"schemaVersion\":${Persistence.CURRENT}"))
        assertEquals(w, Persistence.decodeWorkout(text))
    }

    @Test fun migratesLegacyWorkoutWithoutUpdatedAt() {
        val legacy = """{"id":"x","name":"Viejo","startedAtEpochMs":1000,"finishedAtEpochMs":5000,"exercises":[]}"""
        val w = Persistence.decodeWorkout(legacy)
        assertEquals(5000L, w.updatedAtEpochMs)
        assertEquals("Viejo", w.name)
    }
}

class ProgramsAndTargetsTest {
    @Test fun programsUseExistingExercises() {
        Programs.all.forEach { p ->
            val routines = p.toRoutines("es")
            assertEquals(p.routines.size, routines.size)
            routines.forEach { r -> assertTrue(r.exercises.isNotEmpty()) }
            assertTrue(p.toRoutines("en").first().name.startsWith(p.nameEn))
        }
        val ppl = Programs.all.first { it.id == "ppl_6" }.toRoutines("es").first()
        assertEquals(2, ppl.exercises.count { it.supersetId != null })
        assertEquals(ExerciseLibrary.all.size, ExerciseLibrary.all.map { it.id }.distinct().size)
    }

    @Test fun weeklyMuscleStatus() {
        val monday = LocalDate.of(2026, 9, 7)
        val h = listOf(
            finishedWorkout("1", monday, bench to List(6) { done(80.0, 8) }),
            finishedWorkout("2", monday.plusDays(3), bench to List(6) { done(80.0, 8) }),
            finishedWorkout("3", monday.plusDays(8), bench to List(6) { done(80.0, 8) }),
        )
        val week = WorkoutStats.weeklySetsPerMuscle(h, monday.plusDays(2), UTC)
        assertEquals(12.0, week[MuscleGroup.CHEST]!!, 0.0)
        assertEquals(VolumeStatus.OPTIMAL, MuscleTargets.status(week[MuscleGroup.CHEST]!!))
        assertEquals(VolumeStatus.LOW, MuscleTargets.status(week[MuscleGroup.TRICEPS]!!))
        assertEquals(VolumeStatus.NONE, MuscleTargets.status(0.0))
        assertEquals(VolumeStatus.HIGH, MuscleTargets.status(25.0))
    }
}
