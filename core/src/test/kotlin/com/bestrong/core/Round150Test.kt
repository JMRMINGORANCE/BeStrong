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

class RoutineLinkFitsTest {
    @Test fun tooManySetsDoesNotFit() {
        val bench = ExerciseLibrary.byId("bench_press")!!
        val ok = Routine(name = "R", exercises = listOf(RoutineExercise(exercise = bench, sets = List(30) { RoutineSet() })))
        val big = Routine(name = "R", exercises = listOf(RoutineExercise(exercise = bench, sets = List(31) { RoutineSet() })))
        org.junit.Assert.assertTrue(com.bestrong.core.io.RoutineLink.fits(ok))
        org.junit.Assert.assertFalse(com.bestrong.core.io.RoutineLink.fits(big))
    }
}

class AutoregulationSnapTest {
    @Test fun rpeUpSnapsToLoadableWeight() {
        val curl = ExerciseLibrary.all.first { it.equipment == com.bestrong.core.model.Equipment.DUMBBELL }
        val we = WorkoutExercise(
            id = "e", exercise = curl,
            sets = listOf(WorkoutSet(id = "s1", weightKg = 20.0, reps = 10, rpe = 6.0, completed = true), WorkoutSet(id = "s2", weightKg = 20.0, reps = 10)),
        )
        // Mancuernas de 2 en 2: +2,5 se convierte en 22
        val a = com.bestrong.core.training.Autoregulation.suggest(we, "s1", 2.5) { kg, _ -> Math.round(kg / 2.0) * 2.0 }
        assertEquals(22.0, a!!.newWeightKg, 0.0)
    }
}

class CopyToDayTest {
    @Test fun copyIsNotAProgramSessionNorFromWatch() {
        val w = Workout(
            id = "w", name = "A", startedAtEpochMs = 0, finishedAtEpochMs = 3_600_000, routineId = "program:p:0",
            source = com.bestrong.core.model.WorkoutSource.WATCH,
            exercises = listOf(WorkoutExercise(exercise = ExerciseLibrary.byId("squat")!!, sets = listOf(WorkoutSet(weightKg = 100.0, reps = 5, completed = true)))),
        )
        val c = WorkoutEditor.copyToDay(w, 86_400_000, 90_000_000, 0)
        assertEquals(null, c.routineId)
        assertEquals(com.bestrong.core.model.WorkoutSource.PHONE, c.source)
    }
}

class NewlyUnlockedTest {
    @Test fun firstWorkoutUnlocksFirstAchievementOnce() {
        fun w(id: String, start: Long) = Workout(
            id = id, name = id, startedAtEpochMs = start, finishedAtEpochMs = start + 3_600_000,
            exercises = listOf(WorkoutExercise(exercise = ExerciseLibrary.byId("squat")!!, sets = listOf(WorkoutSet(weightKg = 60.0, reps = 5, completed = true)))),
        )
        val zone = java.time.ZoneOffset.UTC
        val now = System.currentTimeMillis()
        val first = w("a", now - 86_400_000)
        org.junit.Assert.assertTrue("first_workout" in com.bestrong.core.stats.Strength.newlyUnlocked(listOf(first), "a", zone, null))
        org.junit.Assert.assertFalse("first_workout" in com.bestrong.core.stats.Strength.newlyUnlocked(listOf(w("b", now), first), "b", zone, null))
    }
}

class CompactSyncTest {
    @Test fun routinesRoundTripAndShrink() {
        val r = Routine(name = "R", exercises = ExerciseLibrary.all.take(6).map { RoutineExercise(exercise = it) })
        val bytes = com.bestrong.core.sync.SyncContract.encodeRoutines(listOf(r))
        assertEquals(listOf(r), com.bestrong.core.sync.SyncContract.decodeRoutines(bytes))
        val full = com.bestrong.core.sync.SyncContract.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Routine.serializer()), listOf(r)).length
        org.junit.Assert.assertTrue("${bytes.size} vs $full", bytes.size < full * 0.8)
    }
}
