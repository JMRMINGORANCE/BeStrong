package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.training.ActiveProgram
import com.bestrong.core.training.Calisthenics
import com.bestrong.core.training.Fatigue
import com.bestrong.core.training.Goal
import com.bestrong.core.training.Injuries
import com.bestrong.core.training.InjuryZone
import com.bestrong.core.training.IntervalPlan
import com.bestrong.core.training.IntervalTimer
import com.bestrong.core.training.Mesocycle
import com.bestrong.core.training.Peaking
import com.bestrong.core.training.Periodization
import com.bestrong.core.training.PeriodizedProgram
import com.bestrong.core.training.PeriodizedPrograms
import com.bestrong.core.training.ProgressionCap
import com.bestrong.core.training.RoutineGenerator
import com.bestrong.core.training.RoutineTime
import com.bestrong.core.training.WeekPlan
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5Test {
    private val day = 86_400_000L
    private fun ex(id: String) = ExerciseLibrary.byId(id)!!

    @Test fun newProgramsAndCustomProgramsSerialize() {
        listOf("dup_3day", "texas_method", "madcow_5x5", "nsuns_4day").forEach { id ->
            val p = PeriodizedPrograms.byId(id)!!
            val active = ActiveProgram(id, 0, p.mainLifts.associateWith { 100.0 })
            val s = Periodization.nextSession(active, emptyList(), "es")!!
            assertTrue("$id sin ejercicios", s.routine.exercises.isNotEmpty())
            s.routine.exercises.forEach { re -> assertTrue("$id sin series", re.sets.isNotEmpty()) }
        }
        // Programa propio: se guarda en JSON y se usa igual que los predefinidos
        val custom = PeriodizedPrograms.byId("texas_method")!!.copy(id = "mine", nameEs = "Mío", custom = true)
        val json = SyncContract.json.encodeToString(ListSerializer(PeriodizedProgram.serializer()), listOf(custom))
        val back = SyncContract.json.decodeFromString(ListSerializer(PeriodizedProgram.serializer()), json)
        assertEquals(custom, back.single())
        val s = Periodization.nextSession(ActiveProgram("mine", 0, mapOf("squat" to 100.0)), emptyList(), "es", back)!!
        assertEquals(80.0, s.routine.exercises.first().sets.first().weightKg, 0.0)
    }

    @Test fun texasMethodRaisesTrainingMaxWeekly() {
        val p = PeriodizedPrograms.byId("texas_method")!!
        val active = ActiveProgram("texas_method", 0, mapOf("squat" to 100.0, "bench_press" to 80.0, "deadlift" to 140.0, "overhead_press" to 50.0))
        val week1 = Periodization.nextSession(active, emptyList(), "es")!!
        val done = (0 until 3).map { i ->
            Workout(name = "w", startedAtEpochMs = 1L + i, finishedAtEpochMs = 2L + i, routineId = Periodization.routineId(p.id, i))
        }
        val week2 = Periodization.nextSession(active, done, "es")!!
        assertEquals(week1.dayIndex, week2.dayIndex)
        assertTrue(week2.routine.exercises.first().sets.first().weightKg > week1.routine.exercises.first().sets.first().weightKg)
    }

    @Test fun fatigueMesocycleAndDeload() {
        val bench = ex("bench_press")
        fun w(at: Long, kg: Double, rpe: Double) = Workout(
            name = "w",
            startedAtEpochMs = at,
            finishedAtEpochMs = at + 1,
            exercises = listOf(WorkoutExercise(exercise = bench, sets = listOf(WorkoutSet(weightKg = kg, reps = 5, completed = true, rpe = rpe)))),
        )
        val squat = ex("squat")
        fun s(at: Long, kg: Double) = Workout(name = "s", startedAtEpochMs = at, finishedAtEpochMs = at + 1, exercises = listOf(WorkoutExercise(exercise = squat, sets = listOf(WorkoutSet(weightKg = kg, reps = 5, completed = true, rpe = 9.5)))))
        val now = 30 * day
        val history = (0 until 4).map { w(now - (4 - it) * day, 100.0 - it, 9.5) } + (0 until 4).map { s(now - (4 - it) * day - 1000, 140.0 - it) }
        val report = Fatigue.assess(history, now)
        assertEquals(2, report.stalledLifts.size)
        assertTrue(report.deloadRecommended)
        val meso = Mesocycle(LocalDate.of(2026, 1, 5).toEpochDay(), accumulationWeeks = 3)
        assertEquals(1, meso.weekOf(LocalDate.of(2026, 1, 5)))
        assertTrue(meso.isDeloadWeek(LocalDate.of(2026, 1, 26)))
        assertFalse(meso.isDeloadWeek(LocalDate.of(2026, 2, 2)))
        val routine = RoutineGenerator.generate(3, setOf(Equipment.BARBELL), Goal.STRENGTH, 60).first()
        val deload = Fatigue.deload(routine)
        assertTrue(deload.exercises.first().sets.size < routine.exercises.first().sets.size)
    }

    @Test fun peakingGeneratorAndTime() {
        val plan = Peaking.plan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 26))
        assertEquals(12, plan.size)
        assertEquals(1, plan.last().weeksOut)
        assertTrue(plan.map { it.percent }.subList(0, 10).zipWithNext().all { (a, b) -> b >= a })
        assertEquals(185.0, Peaking.weight(plan.first { it.weeksOut == 3 }, 200.0), 0.0)

        val home = RoutineGenerator.generate(4, setOf(Equipment.DUMBBELL), Goal.HYPERTROPHY, 45)
        assertEquals(4, home.size)
        assertTrue(home.all { r -> r.exercises.all { it.exercise.equipment == Equipment.DUMBBELL || it.exercise.equipment == Equipment.BODYWEIGHT } })
        assertTrue(home[0].name != home[2].name)
        val minutes = RoutineTime.estimateMinutes(home.first())
        assertTrue(minutes in 20..70)
        assertEquals(0, RoutineTime.overBy(home.first(), 200))

        val plan2 = WeekPlan().assign(DayOfWeek.MONDAY, "a").assign(DayOfWeek.WEDNESDAY, "b").move(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertEquals("b", plan2.routineFor(DayOfWeek.MONDAY))
        assertEquals("a", plan2.routineFor(DayOfWeek.WEDNESDAY))
    }

    @Test fun calisthenicsTimersInjuriesAndCap() {
        val pushUps = Workout(
            name = "c",
            startedAtEpochMs = 0,
            finishedAtEpochMs = 1,
            exercises = listOf(WorkoutExercise(exercise = ex("knee_push_up"), sets = List(3) { WorkoutSet(reps = 12, completed = true) })),
        )
        assertEquals("push_up", Calisthenics.next("knee_push_up", listOf(pushUps)))
        assertEquals("knee_push_up", Calisthenics.next("knee_push_up", emptyList()))
        assertEquals(240, IntervalTimer.totalSeconds(IntervalTimer.TABATA) + 10)
        val (phase, left) = IntervalTimer.at(IntervalPlan.Hiit(20, 10, 8), 25)!!
        assertFalse(phase.work)
        assertEquals(5, left)
        assertEquals(10 * 60, IntervalTimer.totalSeconds(IntervalPlan.Emom(10)))
        assertTrue(Injuries.conflicts(ex("deadlift"), setOf(InjuryZone.LOWER_BACK)).isNotEmpty())
        assertTrue(Injuries.isSafe(ex("leg_curl"), setOf(InjuryZone.LOWER_BACK, InjuryZone.SHOULDER)))
        assertEquals(105.0, ProgressionCap.limit(115.0, 100.0), 0.0)
        assertEquals(100.0, ProgressionCap.limit(100.0, 100.0), 0.0)
        assertNotNull(ExerciseLibrary.byId("pistol_squat"))
    }
}
