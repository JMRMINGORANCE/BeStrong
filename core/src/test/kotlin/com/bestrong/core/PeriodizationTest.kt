package com.bestrong.core

import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.training.ActiveProgram
import com.bestrong.core.training.Periodization
import com.bestrong.core.training.PeriodizedPrograms
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PeriodizationTest {
    private val zone = ZoneOffset.UTC
    private val tms = mapOf("squat" to 100.0, "bench_press" to 80.0, "deadlift" to 140.0, "overhead_press" to 50.0)

    /** Simula hacer la sesión propuesta tal cual (todas las series completadas). */
    private fun doSession(active: ActiveProgram, history: List<Workout>, at: Long, success: Boolean = true): Workout {
        val s = Periodization.nextSession(active, history, "es")!!
        val w = s.routine.toWorkout(at)
        return w.copy(
            finishedAtEpochMs = at + 3_600_000,
            exercises = w.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(completed = true, reps = if (success) it.reps else it.reps - 1) }) },
        )
    }

    @Test fun fiveThreeOneWeeksAndCycleIncrease() {
        val active = ActiveProgram("wendler_531_bbb", 0, tms)
        val first = Periodization.nextSession(active, emptyList(), "es")!!
        assertEquals(1, first.week)
        assertEquals(1, first.cycle)
        // Semana 1, press militar: 65/75/85 % de 50 → 32,5 / 37,5 / 42,5
        assertEquals(listOf(32.5, 37.5, 42.5), first.routine.exercises[0].sets.map { it.weightKg })
        assertEquals(listOf(5, 5, 5), first.routine.exercises[0].sets.map { it.reps })
        // BBB 5 × 10 al 50 %
        assertEquals(List(5) { 25.0 }, first.routine.exercises[1].sets.map { it.weightKg })

        var history = emptyList<Workout>()
        repeat(4) { i -> history = history + doSession(active, history, (i + 1) * 86_400_000L) }
        val week2 = Periodization.nextSession(active, history, "es")!!
        assertEquals(2, week2.week)
        assertEquals(listOf(3, 3, 3), week2.routine.exercises[0].sets.map { it.reps })

        repeat(12) { i -> history = history + doSession(active, history, (i + 10) * 86_400_000L) }
        val cycle2 = Periodization.nextSession(active, history, "es")!!
        assertEquals(2, cycle2.cycle)
        assertEquals(1, cycle2.week)
        // TM del press militar sube 2,5 kg → 52,5 × 0,85 = 44,6 → 45
        assertEquals(45.0, cycle2.routine.exercises[0].sets[2].weightKg, 0.0)
    }

    @Test fun gzclpLinearProgressionPerTier() {
        val active = ActiveProgram("gzclp", 0, tms)
        val a1 = Periodization.nextSession(active, emptyList(), "es")!!
        // T1 sentadilla: TM 100 → 1RM ≈ 111 → 85 % ≈ 94,4 → 95 kg
        val t1Start = a1.routine.exercises[0].sets[0].weightKg
        assertEquals(95.0, t1Start, 0.0)
        var history = listOf(doSession(active, emptyList(), 1))
        // B1, A2 (T2 de sentadilla no depende del T1)
        history = history + doSession(active, history, 2)
        val a2 = Periodization.nextSession(active, history, "es")!!
        val t2Squat = a2.routine.exercises.first { it.exercise.id == "squat" }.sets[0].weightKg
        assertEquals(Periodization.round(100 / 0.9 * 0.65), t2Squat, 0.0)
        history = history + doSession(active, history, 3)
        history = history + doSession(active, history, 4)
        // Siguiente A1: T1 sentadilla sube 5 kg tras completar
        val nextA1 = Periodization.nextSession(active, history, "es")!!
        assertEquals(t1Start + 5.0, nextA1.routine.exercises[0].sets[0].weightKg, 0.0)
    }

    @Test fun gzclpDeloadsAfterTwoMisses() {
        val active = ActiveProgram("gzclp", 0, tms)
        var history = emptyList<Workout>()
        repeat(8) { i -> history = history + doSession(active, history, i.toLong() + 1, success = false) }
        val a1 = Periodization.nextSession(active, history, "es")!!
        assertEquals(Periodization.round(95.0 * 0.9), a1.routine.exercises[0].sets[0].weightKg, 0.0)
    }

    @Test fun adherenceAndTrainedDays() {
        val monday = LocalDate.of(2026, 10, 5)
        val day = { d: LocalDate -> Workout(name = "x", startedAtEpochMs = d.atStartOfDay(zone).toInstant().toEpochMilli()) }
        val history = listOf(day(monday.minusWeeks(1)), day(monday.minusWeeks(1).plusDays(2)), day(monday.minusWeeks(2)), day(monday.plusDays(1)))
        assertEquals(setOf(java.time.DayOfWeek.TUESDAY), Periodization.trainedDaysOfWeek(history, monday.plusDays(3), zone))
        val adherence = Periodization.adherence(history, 2, monday.plusDays(3), zone, weeks = 2)
        assertNotNull(adherence)
        assertEquals(0.75, adherence!!, 1e-9)
    }

    @Test fun suggestedTrainingMaxIsNinetyPercent() {
        assertEquals(6, PeriodizedPrograms.all.size)
        val w = Workout(
            name = "x", startedAtEpochMs = 0, finishedAtEpochMs = 1,
            exercises = listOf(com.bestrong.core.model.WorkoutExercise(exercise = squat, sets = listOf(WorkoutSet(weightKg = 100.0, reps = 5, completed = true)))),
        )
        // Epley 100 × (1 + 5/30) = 116,7 → 90 % = 105
        assertEquals(105.0, Periodization.suggestedTrainingMax("squat", listOf(w))!!, 0.0)
    }
}
