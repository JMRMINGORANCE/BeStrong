package com.bestrong.core

import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.RestStats
import com.bestrong.core.stats.VolumeZone
import com.bestrong.core.stats.WorkoutStats
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Iteration10Test {
    @Test fun copyToFollowingOnlyTouchesPendingSetsOfSameType() {
        val sets = listOf(
            WorkoutSet(weightKg = 60.0, reps = 5, type = SetType.WARMUP),
            WorkoutSet(weightKg = 100.0, reps = 5),
            WorkoutSet(weightKg = 90.0, reps = 8, completed = true),
            WorkoutSet(weightKg = 90.0, reps = 8),
            WorkoutSet(weightKg = 70.0, reps = 10, type = SetType.DROP),
        )
        val we = WorkoutExercise(exercise = bench, sets = sets)
        val w = Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(we))
        assertEquals(1, WorkoutEditor.followingCount(we, sets[1].id))
        val out = WorkoutEditor.copyToFollowing(w, we.id, sets[1].id).exercises[0].sets
        assertEquals(60.0, out[0].weightKg, 0.0)
        assertEquals(90.0, out[2].weightKg, 0.0)
        assertEquals(100.0, out[3].weightKg, 0.0)
        assertEquals(5, out[3].reps)
        assertEquals(70.0, out[4].weightKg, 0.0)
    }

    @Test fun restSummaryMeasuresGapsBetweenSetsOfSameExercise() {
        val t0 = 1_000_000L
        val sets = listOf(
            WorkoutSet(weightKg = 60.0, reps = 5, type = SetType.WARMUP, completed = true, completedAtEpochMs = t0),
            WorkoutSet(weightKg = 100.0, reps = 5, completed = true, completedAtEpochMs = t0 + 100_000),
            // 3 min tras la anterior: 180 − 40 s de serie = 140 s de descanso
            WorkoutSet(weightKg = 100.0, reps = 5, completed = true, completedAtEpochMs = t0 + 280_000),
            WorkoutSet(weightKg = 80.0, reps = 8, type = SetType.DROP, completed = true, completedAtEpochMs = t0 + 300_000),
            // Hueco de 30 min: no es descanso
            WorkoutSet(weightKg = 100.0, reps = 5, completed = true, completedAtEpochMs = t0 + 2_100_000),
        )
        val we = WorkoutExercise(exercise = bench, sets = sets, restSeconds = 120)
        val s = RestStats.summary(Workout(name = "w", startedAtEpochMs = t0 - 60_000, finishedAtEpochMs = t0 + 2_200_000, exercises = listOf(we)))!!
        assertEquals(140, s.realSeconds)
        assertEquals(120, s.plannedSeconds)
        assertEquals(1, s.samples)
    }

    @Test fun restSummaryNeedsTimestamps() {
        val we = WorkoutExercise(exercise = bench, sets = listOf(done(100.0, 5), done(100.0, 5)), restSeconds = 120)
        assertNull(RestStats.summary(Workout(name = "w", startedAtEpochMs = 0, finishedAtEpochMs = 1, exercises = listOf(we))))
    }

    @Test fun weeklySetsZoneIgnoresCurrentWeek() {
        val d = LocalDate.of(2026, 3, 2)
        val weeks = listOf(6.0, 8.0, 7.0, 9.0, 30.0).mapIndexed { i, v -> d.plusWeeks(i.toLong()) to v }
        val (avg, zone) = WorkoutStats.weeklySetsZone(weeks)!!
        assertEquals(7.5, avg, 0.001)
        assertEquals(VolumeZone.LOW, zone)
        assertEquals(VolumeZone.HIGH, WorkoutStats.weeklySetsZone(listOf(d to 25.0, d to 22.0, d to 0.0))!!.second)
        assertNull(WorkoutStats.weeklySetsZone(listOf(d to 12.0)))
        // Empezó hace 2 semanas a 14 series: no está «por debajo»
        val fresh = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 14.0, 14.0, 3.0).mapIndexed { i, v -> d.plusWeeks(i.toLong()) to v }
        assertEquals(VolumeZone.OPTIMAL, WorkoutStats.weeklySetsZone(fresh)!!.second)
    }

    @Test fun restSummaryIgnoresSupersetsAndUsesSetDuration() {
        val t0 = 1_000_000L
        val plank = com.bestrong.core.library.ExerciseLibrary.byId("plank")!!
        val timed = WorkoutExercise(
            exercise = plank,
            restSeconds = 60,
            sets = listOf(
                WorkoutSet(durationSec = 90, completed = true, completedAtEpochMs = t0),
                // 150 s después: 90 s de plancha → 60 s de descanso real
                WorkoutSet(durationSec = 90, completed = true, completedAtEpochMs = t0 + 150_000),
            ),
        )
        val ss = WorkoutExercise(exercise = bench, restSeconds = 90, supersetId = "a", sets = listOf(WorkoutSet(completed = true, completedAtEpochMs = t0), WorkoutSet(completed = true, completedAtEpochMs = t0 + 400_000)))
        val s = RestStats.summary(Workout(name = "w", startedAtEpochMs = 0, finishedAtEpochMs = t0 + 500_000, exercises = listOf(timed, ss)))!!
        assertEquals(60, s.realSeconds)
        assertEquals(1, s.samples)
    }
}
