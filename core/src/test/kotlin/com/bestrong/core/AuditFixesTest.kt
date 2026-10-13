package com.bestrong.core

import com.bestrong.core.io.WorkoutCsv
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.RoutineSet
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.OneRepMax
import com.bestrong.core.stats.OneRepMax.Formula
import com.bestrong.core.stats.Strength
import com.bestrong.core.training.Mesocycle
import com.bestrong.core.training.Progression
import com.bestrong.core.training.SuggestionKind
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresiones de los fallos encontrados en la auditoría. */
class AuditFixesTest {
    private val pushUp = ExerciseLibrary.byId("push_up")!!
    private val plank = ExerciseLibrary.byId("plank")!!

    @Test fun bodyweightAndTimedExercisesDoNotDeload() {
        val days = (1..4).map { LocalDate.of(2026, 3, it) }
        val reps = days.mapIndexed { i, d -> finishedWorkout("p$i", d, pushUp to List(3) { done(0.0, 11 + i) }) }
        assertEquals(SuggestionKind.ADD_ONE_REP, Progression.suggest(pushUp, reps)!!.kind)
        val timed = days.mapIndexed { i, d -> finishedWorkout("t$i", d, plank to List(3) { WorkoutSet(durationSec = 35 + 5 * i, completed = true) }) }
        assertEquals(SuggestionKind.ADD_TIME, Progression.suggest(plank, timed)!!.kind)
    }

    @Test fun routinePrefillSkipsLastWarmups() {
        val last = finishedWorkout(
            "w", LocalDate.of(2026, 3, 2),
            bench to listOf(done(20.0, 10).copy(type = SetType.WARMUP), done(50.0, 5).copy(type = SetType.WARMUP), done(80.0, 5), done(82.5, 5), done(85.0, 5)),
        )
        val routine = Routine(name = "R", exercises = listOf(RoutineExercise(exercise = bench, sets = List(3) { RoutineSet(weightKg = 70.0, reps = 5) })))
        val w = routine.toWorkout(0, previousSets = { id -> com.bestrong.core.stats.WorkoutStats.previousSets(id, listOf(last)) })
        assertEquals(listOf(80.0, 82.5, 85.0), w.exercises[0].sets.map { it.weightKg })
    }

    @Test fun csvRoundTripKeepsOnlyDoneSetsAndExtraFields() {
        val w = finishedWorkout(
            "w1", LocalDate.of(2026, 3, 2),
            bench to listOf(done(20.0, 10).copy(repsRight = 9, distanceM = 50.0, assisted = true, note = "hombro"), WorkoutSet(weightKg = 100.0, reps = 10)),
        )
        val back = WorkoutCsv.import(WorkoutCsv.export(listOf(w), UTC), ExerciseLibrary.all, UTC).workouts.single()
        val s = back.exercises.single().sets.single()
        assertEquals(9, s.repsRight)
        assertEquals(50.0, s.distanceM, 0.0)
        assertTrue(s.assisted)
        assertEquals("hombro", s.note)
    }

    @Test fun csvDecimalCommaIsParsed() {
        val csv = "workout_id;date;end_date;workout_name;workout_notes;exercise_id;exercise_name;superset_id;exercise_notes;set_index;set_type;weight_kg;reps;duration_s;rpe\n" +
            "w1;2026-03-02T18:00:00;2026-03-02T19:00:00;A;;bench_press;Press;;;0;NORMAL;82,5;5;0;8,5\n"
        val s = WorkoutCsv.import(csv, ExerciseLibrary.all, UTC).workouts.single().exercises.single().sets.single()
        assertEquals(82.5, s.weightKg, 0.0)
        assertEquals(8.5, s.rpe!!, 0.0)
    }

    @Test fun finishKeepsRunningCardio() {
        val treadmill = ExerciseLibrary.all.first { it.tracking == com.bestrong.core.model.TrackingType.CARDIO }
        val w = Workout(
            name = "C", startedAtEpochMs = 0,
            exercises = listOf(WorkoutExercise(exercise = treadmill, sets = listOf(WorkoutSet(startedAtEpochMs = 0)))),
        )
        val done = WorkoutEditor.finish(w, 30 * 60_000L)
        assertEquals(1, done.exercises.size)
        assertTrue(done.exercises.single().sets.single().completed)
    }

    @Test fun weekSummaryFollowsCalendarAcrossDst() {
        val madrid = ZoneId.of("Europe/Madrid")
        val late = LocalDate.of(2026, 10, 25).atTime(23, 30).atZone(madrid).toInstant().toEpochMilli()
        val w = Workout(name = "X", startedAtEpochMs = late, finishedAtEpochMs = late + 1000, exercises = listOf(WorkoutExercise(exercise = bench, sets = listOf(done(50.0, 5)))))
        assertEquals(1, Strength.weekSummary(listOf(w), LocalDate.of(2026, 10, 25), madrid).workouts)
        assertEquals(0, Strength.weekSummary(listOf(w), LocalDate.of(2026, 10, 26), madrid).workouts)
    }

    @Test fun landerIsNeverNegative() {
        assertTrue(OneRepMax.estimate(50.0, 40, Formula.LANDER) >= 0.0)
    }

    @Test fun mesocycleBeforeStartIsWeekOne() {
        val start = LocalDate.of(2026, 11, 2)
        val m = Mesocycle(start.toEpochDay())
        assertEquals(1, m.weekOf(start.minusDays(10)))
        assertNotEquals(true, m.isDeloadWeek(start.minusDays(10)))
    }
}

class RoutineUpdateTest {
    @Test fun updatedFromKeepsIdentityAndTakesStructure() {
        val routine = Routine(name = "Empuje", emoji = "💪", exercises = listOf(RoutineExercise(exercise = bench, sets = List(3) { RoutineSet(60.0, 8) })))
        val w = routine.toWorkout(0)
        assertTrue(!routine.structureDiffersFrom(w))
        val changed = w.copy(exercises = w.exercises + WorkoutExercise(exercise = squat, sets = listOf(done(100.0, 5)), notes = "talones"))
        assertTrue(routine.structureDiffersFrom(changed))
        val updated = routine.updatedFrom(changed)
        assertEquals(routine.id, updated.id)
        assertEquals("💪", updated.emoji)
        assertEquals(listOf("bench_press", "squat"), updated.exercises.map { it.exercise.id })
        assertEquals("talones", updated.exercises[1].notes)
    }
}
