package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.training.ActiveProgram
import com.bestrong.core.training.Calisthenics
import com.bestrong.core.training.Goal
import com.bestrong.core.training.Periodization
import com.bestrong.core.training.PeriodizedPrograms
import com.bestrong.core.training.RoutineGenerator
import com.bestrong.core.training.RoutineTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5Test {
    private val day = 86_400_000L
    private fun ex(id: String) = ExerciseLibrary.byId(id)!!

    @Test fun newProgramsHaveSessions() {
        listOf("dup_3day", "texas_method", "madcow_5x5", "nsuns_4day").forEach { id ->
            val p = PeriodizedPrograms.byId(id)!!
            val active = ActiveProgram(id, 0, p.mainLifts.associateWith { 100.0 })
            val s = Periodization.nextSession(active, emptyList(), "es")!!
            assertTrue("$id sin ejercicios", s.routine.exercises.isNotEmpty())
            s.routine.exercises.forEach { re -> assertTrue("$id sin series", re.sets.isNotEmpty()) }
        }
        // Un programa que ya no existe (p. ej. uno propio de versiones anteriores) no da sesión
        assertEquals(null, Periodization.nextSession(ActiveProgram("mine", 0, mapOf("squat" to 100.0)), emptyList(), "es"))
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

    @Test fun routineGeneratorAndTime() {
        val home = RoutineGenerator.generate(4, setOf(Equipment.DUMBBELL), Goal.HYPERTROPHY, 45)
        assertEquals(4, home.size)
        assertTrue(home.all { r -> r.exercises.all { it.exercise.equipment == Equipment.DUMBBELL || it.exercise.equipment == Equipment.BODYWEIGHT } })
        assertTrue(home[0].name != home[2].name)
        val minutes = RoutineTime.estimateMinutes(home.first())
        assertTrue(minutes in 20..70)
        assertEquals(0, RoutineTime.overBy(home.first(), 200))
    }

    @Test fun calisthenicsProgression() {
        val pushUps = Workout(
            name = "c",
            startedAtEpochMs = 0,
            finishedAtEpochMs = 1,
            exercises = listOf(WorkoutExercise(exercise = ex("knee_push_up"), sets = List(3) { WorkoutSet(reps = 12, completed = true) })),
        )
        assertEquals("push_up", Calisthenics.next("knee_push_up", listOf(pushUps)))
        assertEquals("knee_push_up", Calisthenics.next("knee_push_up", emptyList()))
        assertNotNull(ExerciseLibrary.byId("pistol_squat"))
    }
}
