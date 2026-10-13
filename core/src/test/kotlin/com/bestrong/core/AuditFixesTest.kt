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

class RoutinePrefillKindTest {
    @Test fun failureAndDropSetsStillPrefillWorkingSets() {
        val last = finishedWorkout(
            "w", LocalDate.of(2026, 3, 2),
            bench to listOf(done(40.0, 10).copy(type = SetType.WARMUP), done(80.0, 5), done(82.5, 5), done(85.0, 4).copy(type = SetType.FAILURE)),
        )
        val routine = Routine(name = "R", exercises = listOf(RoutineExercise(exercise = bench, sets = List(3) { RoutineSet(weightKg = 70.0, reps = 5) })))
        val w = routine.toWorkout(0, previousSets = { id -> com.bestrong.core.stats.WorkoutStats.previousSets(id, listOf(last)) })
        assertEquals(listOf(80.0, 82.5, 85.0), w.exercises[0].sets.map { it.weightKg })
    }
}

class SharedSettingsTest {
    @Test fun oldSettingsWithoutIncrementsStillDecode() {
        val old = """{"unit":"LB","progressionEnabled":true}"""
        val s = com.bestrong.core.sync.SyncContract.json.decodeFromString(com.bestrong.core.sync.SharedSettings.serializer(), old)
        assertEquals(4, s.weeklyGoal)
        assertEquals(com.bestrong.core.util.WeightUnit.LB.stepKg(), s.stepKgFor("bench_press"), 0.0)
        assertEquals(2.0, s.copy(exerciseIncrementKg = mapOf("bench_press" to 2.0)).stepKgFor("bench_press"), 0.0)
    }
}

class PerformanceFixesTest {
    private fun history(n: Int) = (0 until n).map { i ->
        finishedWorkout(
            "w$i", LocalDate.of(2024, 1, 1).plusDays(i.toLong()),
            bench to List(4) { done(60.0 + i % 10, 8) }, squat to List(4) { done(100.0, 5) },
        )
    }

    @Test fun lastSetsByExerciseMatchesPreviousSets() {
        val h = history(40).shuffled(java.util.Random(1))
        val map = com.bestrong.core.stats.WorkoutStats.lastSetsByExercise(h)
        listOf("bench_press", "squat", "deadlift").forEach { id ->
            assertEquals(com.bestrong.core.stats.WorkoutStats.previousSets(id, h), map[id].orEmpty())
        }
    }

    @Test fun watchHistoryFitsInOneDataItem() {
        val bytes = com.bestrong.core.sync.SyncContract.encodeRecentHistory(history(200), maxBytes = 20_000)
        assertTrue(bytes.size <= 20_000)
        assertTrue(com.bestrong.core.sync.SyncContract.decodeWorkouts(bytes).isNotEmpty())
    }

    @Test fun persistenceFastPathRoundTrips() {
        val w = history(1).single()
        val text = com.bestrong.core.io.Persistence.encodeWorkout(w)
        assertEquals(w, com.bestrong.core.io.Persistence.decodeWorkout(text))
    }
}

class RecordsInEachTest {
    @Test fun matchesNewRecordsInPerWorkout() {
        val rnd = java.util.Random(7)
        val h = (0 until 60).map { i ->
            finishedWorkout(
                "w$i", LocalDate.of(2025, 1, 1).plusDays(i.toLong()),
                bench to List(3) { done(50.0 + rnd.nextInt(40), 3 + rnd.nextInt(8)) },
                squat to List(3) { done(80.0 + rnd.nextInt(60), 2 + rnd.nextInt(6)) },
            )
        }
        val each = com.bestrong.core.stats.WorkoutStats.newRecordsInEach(h) { true }
        h.forEach { w ->
            val expected = com.bestrong.core.stats.WorkoutStats.newRecordsIn(w, h.filter { it.startedAtEpochMs < w.startedAtEpochMs })
            assertEquals(expected.map { it.exerciseId to it.type to it.value }.toSet(), each[w.id].orEmpty().map { it.exerciseId to it.type to it.value }.toSet())
        }
    }
}

class PlateLoadTest {
    @Test fun gymPlateLoadAndZeroPlateGuard() {
        val gym = com.bestrong.core.training.GymProfile(name = "G", platesKg = listOf(0.0, 20.0, 10.0, 2.5))
        val load = gym.plateLoad(65.0, com.bestrong.core.util.WeightUnit.KG)
        assertEquals(listOf(20.0, 2.5), load.perSide)
        assertEquals(65.0, gym.nearestBarbell(65.0), 0.0)
        val lb = gym.plateLoad(com.bestrong.core.util.WeightUnit.LB.toKg(135.0), com.bestrong.core.util.WeightUnit.LB)
        assertEquals(listOf(45.0), lb.perSide)
    }
}

class RecordsTieTest {
    @Test fun workoutsAtSameTimeAreNotBeforeEachOther() {
        val day = LocalDate.of(2025, 3, 1)
        val a = finishedWorkout("a", day, bench to listOf(done(60.0, 5)))
        val b = finishedWorkout("b", day, bench to listOf(done(70.0, 5)))
        val first = finishedWorkout("0", day.minusDays(1), bench to listOf(done(50.0, 5)))
        val h = listOf(first, a, b)
        val each = com.bestrong.core.stats.WorkoutStats.newRecordsInEach(h) { true }
        listOf(a, b).forEach { w ->
            val expected = com.bestrong.core.stats.WorkoutStats.newRecordsIn(w, h.filter { it.startedAtEpochMs < w.startedAtEpochMs })
            assertEquals(expected.map { it.type to it.value }.toSet(), each[w.id].orEmpty().map { it.type to it.value }.toSet())
        }
    }
}

class WarmupRestTest {
    @Test fun shortRestAfterWarmupSet() {
        val w = Workout(
            name = "W", startedAtEpochMs = 0,
            exercises = listOf(
                WorkoutExercise(
                    exercise = squat, restSeconds = 180,
                    sets = listOf(WorkoutSet(weightKg = 60.0, reps = 5, type = SetType.WARMUP), WorkoutSet(weightKg = 100.0, reps = 5)),
                ),
            ),
        )
        val we = w.exercises.single()
        val afterWarm = WorkoutEditor.toggleSetCompleted(w, we.id, we.sets[0].id, 1_000)
        assertEquals(0 to WorkoutEditor.WARMUP_REST_SECONDS, WorkoutEditor.afterSetCompleted(afterWarm, 0))
        val withMore = afterWarm.copy(exercises = listOf(afterWarm.exercises[0].copy(sets = afterWarm.exercises[0].sets + WorkoutSet(weightKg = 100.0, reps = 5))))
        val afterWork = WorkoutEditor.toggleSetCompleted(withMore, we.id, we.sets[1].id, 2_000)
        assertEquals(0 to 180, WorkoutEditor.afterSetCompleted(afterWork, 0))
    }
}

class SetTimelineTest {
    @Test fun windowsDoNotOverlapAndStayInsideWorkout() {
        val sets = listOf(
            done(60.0, 5).copy(completedAtEpochMs = 30_000),
            done(60.0, 5).copy(completedAtEpochMs = 50_000),
            done(60.0, 5).copy(completedAtEpochMs = null),
            done(60.0, 5).copy(completedAtEpochMs = 200_000),
        )
        val w = Workout(name = "W", startedAtEpochMs = 10_000, finishedAtEpochMs = 300_000, exercises = listOf(WorkoutExercise(exercise = bench, sets = sets)))
        val windows = com.bestrong.core.stats.SetTimeline.windows(w)
        assertEquals(listOf(10_000L to 30_000L, 30_000L to 50_000L, 160_000L to 200_000L), windows.map { it.startEpochMs to it.endEpochMs })
    }
}

class TrainingLoadTest {
    @Test fun sessionLoadAndAcuteChronicRatio() {
        val monday = LocalDate.of(2026, 10, 12)
        fun w(id: String, day: LocalDate, rpe: Double?) = finishedWorkout(id, day, bench to listOf(done(60.0, 5).copy(rpe = rpe)))
        // 60 min (18:00–19:00) a RPE 8 → 480
        assertEquals(480.0, com.bestrong.core.stats.TrainingLoad.session(w("a", monday, 8.0)), 1e-6)
        assertEquals(420.0, com.bestrong.core.stats.TrainingLoad.session(w("b", monday, null)), 1e-6)
        val today = monday.plusDays(2)
        // Una sesión en cada una de las 4 semanas anteriores (días 8–35) y dos esta semana
        val history = (1..4).map { w("p$it", today.minusDays(7L * it + 1), null) } + listOf(w("c1", monday, 8.0), w("c2", monday.plusDays(1), 8.0))
        val weekly = com.bestrong.core.stats.TrainingLoad.weekly(history, today, UTC)
        assertEquals(960.0 / 420.0, weekly.acuteChronicRatio!!, 1e-6)
        assertTrue(weekly.tooHigh)
        assertEquals(8, weekly.weeks.size)
        assertEquals(monday, weekly.weeks.last().first)
        // Usuario nuevo (una sola semana previa): sin relación, sin falsa alarma
        val newcomer = com.bestrong.core.stats.TrainingLoad.weekly(history.drop(1).drop(1).drop(1), today, UTC)
        assertEquals(null, newcomer.acuteChronicRatio)
    }
}

class CsvFormulaTest {
    @Test fun formulasAreNeutralizedAndRoundTrip() {
        assertEquals("'=HYPERLINK(\"x\")", com.bestrong.core.io.Csv.neutralizeFormula("=HYPERLINK(\"x\")"))
        assertEquals("-10.0", com.bestrong.core.io.Csv.neutralizeFormula("-10.0"))
        val w = finishedWorkout("w1", LocalDate.of(2026, 3, 2), bench to listOf(done(80.0, 8))).copy(name = "=1+1", notes = "@cmd")
        val back = WorkoutCsv.import(WorkoutCsv.export(listOf(w), UTC), ExerciseLibrary.all, UTC).workouts.single()
        assertEquals("=1+1", back.name)
        assertEquals("@cmd", back.notes)
        assertTrue(!WorkoutCsv.export(listOf(w), UTC).contains("\n" + "w1," + "="))
    }
}

class SecurityFixesTest {
    @Test fun routinesForWatchFitAndXlsxDropsControlChars() {
        val many = (1..300).map { i -> Routine(name = "R$i", notes = "x".repeat(200), exercises = listOf(RoutineExercise(exercise = bench, sets = List(5) { RoutineSet(60.0, 8) }))) }
        val bytes = com.bestrong.core.sync.SyncContract.encodeRoutinesWithin(many, maxBytes = 30_000)
        assertTrue(bytes.size <= 30_000)
        assertTrue(com.bestrong.core.sync.SyncContract.decodeRoutines(bytes).isNotEmpty())
        assertTrue(com.bestrong.core.io.RoutineLink.decode("bestrong://routine?d=%%%") == null)
    }
}

class EncryptedBackupErrorsTest {
    @Test fun damagedFileIsNotReportedAsWrongPassword() {
        val enc = com.bestrong.core.io.EncryptedBackup.encrypt("hola", "contraseña1".toCharArray())
        assertEquals("hola", com.bestrong.core.io.EncryptedBackup.decrypt(enc, "contraseña1".toCharArray()))
        val wrong = runCatching { com.bestrong.core.io.EncryptedBackup.decrypt(enc, "otra".toCharArray()) }.exceptionOrNull()
        assertTrue(wrong is com.bestrong.core.io.WrongPasswordException)
        val damaged = runCatching { com.bestrong.core.io.EncryptedBackup.decrypt(enc.dropLast(30) + "%%%", "contraseña1".toCharArray()) }.exceptionOrNull()
        assertTrue(damaged is com.bestrong.core.io.DamagedBackupException)
    }
}

class DropSetTest {
    @Test fun dropSetGoesAfterLastWorkingSetAt80Percent() {
        val w = Workout(name = "W", startedAtEpochMs = 0, exercises = listOf(WorkoutExercise(exercise = bench, sets = listOf(done(100.0, 8), WorkoutSet(weightKg = 100.0, reps = 8)))))
        val we = w.exercises.single()
        val after = WorkoutEditor.addDropSet(w, we.id, 2.5).exercises.single().sets
        assertEquals(3, after.size)
        assertEquals(SetType.DROP, after[2].type)
        assertEquals(80.0, after[2].weightKg, 0.0)
    }
}

class BodyWeightTrendTest {
    @Test fun sevenDayAverageAndChange() {
        val day = 86_400_000L
        val now = 100 * day
        val points = listOf(now - 10 * day to 81.0, now - 9 * day to 81.4, now - 3 * day to 80.2, now - 1 * day to 80.6)
        val t = com.bestrong.core.stats.BodyWeightTrend.of(points, now)!!
        assertEquals(80.4, t.averageKg, 1e-9)
        assertEquals(80.4 - 81.2, t.changeKg!!, 1e-9)
        assertEquals(null, com.bestrong.core.stats.BodyWeightTrend.of(listOf(now - 20 * day to 80.0), now))
    }
}

class EstimateAndBarTest {
    @Test fun timedSetsAndSupersetsInEstimate() {
        val run = ExerciseLibrary.all.first { it.tracking == com.bestrong.core.model.TrackingType.CARDIO }
        val w = Workout(
            name = "W", startedAtEpochMs = 0,
            exercises = listOf(
                WorkoutExercise(exercise = run, restSeconds = 60, sets = listOf(WorkoutSet(durationSec = 1800))),
                WorkoutExercise(exercise = bench, restSeconds = 90, supersetId = "g", sets = List(2) { WorkoutSet(weightKg = 60.0, reps = 8) }),
                WorkoutExercise(exercise = squat, restSeconds = 90, supersetId = "g", sets = List(2) { WorkoutSet(weightKg = 80.0, reps = 8) }),
            ),
        )
        // 1800 + 4×40 de trabajo; descansos: 60 tras el cardio y 90 al cerrar la 1.ª ronda (la última no cuenta)
        assertEquals(1800 + 160 + 60 + 90, WorkoutEditor.estimatedRemainingSeconds(w))
    }

    @Test fun twentyKiloBarIsTheStandard45PoundBar() {
        assertEquals(45.0, com.bestrong.core.training.GymProfile.barIn(com.bestrong.core.util.WeightUnit.LB, 20.0), 0.0)
        assertEquals(20.0, com.bestrong.core.training.GymProfile.barIn(com.bestrong.core.util.WeightUnit.KG, 20.0), 0.0)
    }
}
