package com.bestrong.core

import com.bestrong.core.library.Alternatives
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.Autoregulation
import com.bestrong.core.training.GymProfile
import com.bestrong.core.training.RestPolicy
import com.bestrong.core.training.SetComparison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1Test {
    private fun ex(id: String) = ExerciseLibrary.byId(id)!!

    @Test fun restByExerciseType() {
        assertEquals(RestPolicy.HEAVY, RestPolicy.defaultFor(ex("squat")))
        assertEquals(RestPolicy.HEAVY, RestPolicy.defaultFor(ex("bench_press")))
        assertEquals(RestPolicy.ISOLATION, RestPolicy.defaultFor(ex("lateral_raise")))
        assertEquals(RestPolicy.ISOLATION, RestPolicy.defaultFor(ex("db_curl")))
        assertEquals(RestPolicy.SHORT, RestPolicy.defaultFor(ex("plank")))
        assertEquals(RestPolicy.SHORT, RestPolicy.defaultFor(ex("standing_calf_raise")))
        // Aprende del historial: si cambiaste el descanso, se recuerda
        var w = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(ex("squat")))
        w = WorkoutEditor.updateExercise(w, w.exercises[0].id) { it.copy(restSeconds = 240) }
        w = WorkoutEditor.finish(WorkoutEditor.toggleSetCompleted(w, w.exercises[0].id, w.exercises[0].sets[0].id), 1_000)
        assertEquals(240, RestPolicy.restFor(ex("squat"), listOf(w)))
    }

    @Test fun setComparison() {
        val prev = WorkoutSet(weightKg = 80.0, reps = 8)
        val d = SetComparison.delta(WorkoutSet(weightKg = 82.5, reps = 7), prev)!!
        assertEquals(2.5, d.weightKg, 0.0)
        assertEquals(-1, d.reps)
        assertEquals(true, SetComparison.beats(WorkoutSet(weightKg = 82.5, reps = 8), prev))
        assertEquals(false, SetComparison.beats(WorkoutSet(weightKg = 80.0, reps = 7), prev))
        assertNull(SetComparison.delta(prev, null))
    }

    @Test fun rpeAutoregulation() {
        var w = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(ex("bench_press")))
        val we0 = w.exercises[0]
        w = WorkoutEditor.updateExercise(w, we0.id) { e -> e.copy(sets = e.sets.map { it.copy(weightKg = 100.0, reps = 5) }) }
        val first = w.exercises[0].sets[0]
        w = WorkoutEditor.updateSet(w, we0.id, first.id) { it.copy(completed = true, rpe = 10.0) }
        val down = Autoregulation.suggest(w.exercises[0], first.id, 2.5)!!
        assertEquals(95.0, down.newWeightKg, 0.0)
        val applied = Autoregulation.apply(w.exercises[0], down)
        assertEquals(listOf(100.0, 95.0, 95.0), applied.sets.map { it.weightKg })
        w = WorkoutEditor.updateSet(w, we0.id, first.id) { it.copy(rpe = 6.5) }
        assertEquals(102.5, Autoregulation.suggest(w.exercises[0], first.id, 2.5)!!.newWeightKg, 0.0)
        w = WorkoutEditor.updateSet(w, we0.id, first.id) { it.copy(rpe = 8.0) }
        assertNull(Autoregulation.suggest(w.exercises[0], first.id, 2.5))
    }

    @Test fun pauseExcludedFromDuration() {
        var w = WorkoutEditor.empty(0)
        w = WorkoutEditor.pause(w, 60_000)
        assertEquals(60, w.durationSeconds(10 * 60_000))
        w = WorkoutEditor.resume(w, 5 * 60_000)
        assertEquals(120, w.durationSeconds(6 * 60_000))
        val finished = WorkoutEditor.finish(WorkoutEditor.pause(w, 7 * 60_000), 9 * 60_000)
        assertEquals(180, finished.durationSeconds())
    }

    @Test fun forgottenWorkoutFinishesAtLastActivity() {
        var w = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(ex("squat")))
        w = WorkoutEditor.toggleSetCompleted(w, w.exercises[0].id, w.exercises[0].sets[0].id).copy(updatedAtEpochMs = 40 * 60_000)
        assertTrue(WorkoutEditor.isForgotten(w, 5 * 3_600_000L))
        assertFalse(WorkoutEditor.isForgotten(w, 2 * 3_600_000L))
        val done = WorkoutEditor.finishAtLastActivity(w)
        assertEquals(40 * 60L, done.durationSeconds())
        assertEquals(1, done.completedSetCount)
    }

    @Test fun alternativesAndConversion() {
        val bench = ex("bench_press")
        val alts = Alternatives.forExercise(bench, ExerciseLibrary.all)
        assertTrue(alts.isNotEmpty())
        // Mismo músculo principal, o variantes del mismo movimiento (press cerrado), y estas primero
        val family = ExerciseLibrary.familyOf(bench.id)
        assertTrue(alts.all { it.primaryMuscle == bench.primaryMuscle || it.id in family })
        assertTrue(alts.first().id in family)
        val onlyDb = Alternatives.forExercise(bench, ExerciseLibrary.all, setOf(Equipment.DUMBBELL))
        assertTrue(onlyDb.all { it.equipment == Equipment.DUMBBELL })
        // 100 kg en barra → ~40 kg por mancuerna
        val converted = Alternatives.convertSets(bench, ex("db_bench_press"), listOf(WorkoutSet(weightKg = 100.0, reps = 8)), emptyList(), 2.0)
        assertEquals(40.0, converted[0].weightKg, 0.0)
        // Si ya hiciste el nuevo ejercicio, se usa tu historial
        val fromHistory = Alternatives.convertSets(bench, ex("db_bench_press"), listOf(WorkoutSet(weightKg = 100.0, reps = 8)), listOf(WorkoutSet(weightKg = 32.0, reps = 10)), 2.0)
        assertEquals(32.0, fromHistory[0].weightKg, 0.0)
        assertEquals(10, fromHistory[0].reps)
    }

    @Test fun gymProfileLoads() {
        val gym = GymProfile(name = "Sin discos pequeños", platesKg = listOf(20.0, 10.0, 5.0))
        assertEquals(60.0, gym.nearestBarbell(61.0), 0.0)
        assertEquals(70.0, gym.nearestBarbell(68.0), 0.0)
        val home = GymProfile.home()
        assertEquals(22.0, home.nearestLoad(21.5, Equipment.DUMBBELL), 0.0)
        assertEquals(30.0, home.nearestLoad(40.0, Equipment.DUMBBELL), 0.0)
    }
}
