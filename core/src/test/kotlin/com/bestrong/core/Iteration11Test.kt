package com.bestrong.core

import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.ProgramTexts
import org.junit.Assert.assertEquals
import org.junit.Test

class Iteration11Test {
    @Test fun estimateAddsRunningRestAndShortWarmupRest() {
        val sets = listOf(WorkoutSet(type = SetType.WARMUP), WorkoutSet(), WorkoutSet())
        val w = Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(WorkoutExercise(exercise = bench, sets = sets, restSeconds = 180)))
        // 3 series × 40 s + 60 s tras el calentamiento + 180 s entre las efectivas
        assertEquals(3 * 40 + 60 + 180, WorkoutEditor.estimatedRemainingSeconds(w, nowEpochMs = 0))
        // Con 100 s de descanso aún por delante
        assertEquals(100 + 3 * 40 + 60 + 180, WorkoutEditor.estimatedRemainingSeconds(w, nowEpochMs = 0, restEndsAtEpochMs = 100_000))
    }

    @Test fun userPartKeepsWhatTheUserWroteInProgramNotes() {
        val gen = listOf(ProgramTexts.ES.amrapNote, ProgramTexts.EN.amrapNote)
        assertEquals("", ProgramTexts.userPart("T1 · Última serie: máximas repeticiones (+)", gen))
        assertEquals("", ProgramTexts.userPart("T2", gen))
        assertEquals("me costó", ProgramTexts.userPart("T1 · Última serie: máximas repeticiones (+) me costó", gen))
        assertEquals("hombro raro", ProgramTexts.userPart("hombro raro", gen))
    }
}

class Iteration11ImprovementsTest {
    @Test fun duplicateSetInsertsPendingCopyBelow() {
        val sets = listOf(WorkoutSet(weightKg = 100.0, reps = 5, completed = true, completedAtEpochMs = 1, note = "x"), WorkoutSet(weightKg = 80.0, reps = 8))
        val we = WorkoutExercise(exercise = bench, sets = sets)
        val out = WorkoutEditor.duplicateSet(Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(we)), we.id, sets[0].id).exercises[0].sets
        assertEquals(3, out.size)
        assertEquals(100.0, out[1].weightKg, 0.0)
        assertEquals(false, out[1].completed)
        assertEquals("", out[1].note)
        assertEquals(80.0, out[2].weightKg, 0.0)
    }

    @Test fun completeRemainingSkipsEmptySets() {
        val sets = listOf(WorkoutSet(weightKg = 100.0, reps = 5), WorkoutSet(weightKg = 0.0, reps = 0), WorkoutSet(weightKg = 90.0, reps = 6, completed = true, completedAtEpochMs = 5))
        val we = WorkoutExercise(exercise = bench, sets = sets)
        assertEquals(1, WorkoutEditor.completableCount(we))
        val out = WorkoutEditor.completeRemaining(Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(we)), we.id, 1_000).exercises[0].sets
        assertEquals(listOf(true, false, true), out.map { it.completed })
        assertEquals(1_000L, out[0].completedAtEpochMs)
        assertEquals(5L, out[2].completedAtEpochMs)
    }

    @Test fun laggingMusclesFromThursdayForUsuallyTrainedMuscles() {
        // Lunes 2/3/2026: 4 semanas de 12 series de pecho; esta semana solo 3
        val monday = java.time.LocalDate.of(2026, 3, 2)
        val history = (1..4).map { k -> finishedWorkout("w$k", monday.minusWeeks(k.toLong()), bench to List(12) { done(80.0, 5) }) } +
            finishedWorkout("now", monday, bench to List(3) { done(80.0, 5) })
        val wed = com.bestrong.core.stats.WorkoutStats.laggingMuscles(history, monday.plusDays(2), UTC)
        assertEquals(emptyList<Any>(), wed)
        val thu = com.bestrong.core.stats.WorkoutStats.laggingMuscles(history, monday.plusDays(3), UTC)
        assertEquals(listOf(bench.primaryMuscle to 3.0), thu)
    }

    @Test fun restByExercise() {
        val t0 = 1_000_000L
        val sets = listOf(WorkoutSet(completed = true, completedAtEpochMs = t0), WorkoutSet(completed = true, completedAtEpochMs = t0 + 160_000))
        val we = WorkoutExercise(exercise = bench, sets = sets, restSeconds = 90)
        val map = com.bestrong.core.stats.RestStats.byExercise(Workout(name = "w", startedAtEpochMs = 0, finishedAtEpochMs = t0 + 200_000, exercises = listOf(we)))
        assertEquals(120, map.getValue(we.id).realSeconds)
    }
}
