package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.SetType
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.ExerciseTimeline
import com.bestrong.core.stats.MilestoneKind
import com.bestrong.core.stats.MovementPattern
import com.bestrong.core.stats.MovementPatterns
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelinePatternsTest {
    private val d0 = LocalDate.of(2026, 1, 5)

    @Test
    fun timelineHasFirstTimeRecordsAndRoundWeights() {
        val h = listOf(
            finishedWorkout("a", d0, bench to listOf(done(55.0, 8))),
            finishedWorkout("b", d0.plusDays(7), bench to listOf(done(60.0, 8))),
            finishedWorkout("c", d0.plusDays(14), bench to listOf(done(60.0, 6))),
        )
        val m = ExerciseTimeline.milestones(h, "bench_press")
        // Lo más reciente primero: «b» trae récord de 1RM, de peso y los 60 kg; «a» es la primera vez
        assertEquals("b", m.first().workoutId)
        assertTrue(m.any { it.kind == MilestoneKind.RECORD_WEIGHT && it.value == 60.0 && it.previous == 55.0 })
        assertTrue(m.any { it.kind == MilestoneKind.ROUND_WEIGHT && it.value == 60.0 && it.workoutId == "b" })
        assertFalse(m.any { it.kind == MilestoneKind.ROUND_WEIGHT && it.workoutId == "a" })
        assertEquals(MilestoneKind.FIRST_TIME, m.last().kind)
        // «c» no mejora nada
        assertFalse(m.any { it.workoutId == "c" })
        // Un solo récord por sesión: con récord de peso no se repite el de 1RM
        assertFalse(m.any { it.kind == MilestoneKind.RECORD_ONE_REP_MAX && it.workoutId == "b" })
        // Más reps con el mismo peso: récord de 1RM
        val more = ExerciseTimeline.milestones(h + finishedWorkout("d", d0.plusDays(21), bench to listOf(done(60.0, 10))), "bench_press")
        assertTrue(more.any { it.kind == MilestoneKind.RECORD_ONE_REP_MAX && it.workoutId == "d" })
    }

    @Test
    fun warmupsDoNotCountAndGymChangesAndComebacksDo() {
        val warm = WorkoutSet(weightKg = 100.0, reps = 1, type = SetType.WARMUP, completed = true)
        val h = listOf(
            finishedWorkout("a", d0, bench to listOf(done(60.0, 5))).copy(gymName = "Centro"),
            finishedWorkout("b", d0.plusDays(40), bench to listOf(warm, done(60.0, 5))).copy(gymName = "Casa"),
        )
        val m = ExerciseTimeline.milestones(h, "bench_press")
        assertFalse(m.any { it.kind == MilestoneKind.RECORD_WEIGHT })
        assertTrue(m.any { it.kind == MilestoneKind.GYM_CHANGE && it.gym == "Casa" })
        assertTrue(m.any { it.kind == MilestoneKind.COMEBACK && it.value == 40.0 })
    }

    @Test
    fun deloadIsALighterSessionFollowedByABounceBack() {
        val h = listOf(100.0, 100.0, 80.0, 102.5).mapIndexed { i, w -> finishedWorkout("w$i", d0.plusDays(7L * i), squat to listOf(done(w, 5))) }
        val m = ExerciseTimeline.milestones(h, "squat")
        assertTrue(m.any { it.kind == MilestoneKind.DELOAD && it.workoutId == "w2" })
        assertTrue(m.any { it.kind == MilestoneKind.RECORD_WEIGHT && it.workoutId == "w3" })
        assertEquals(4, ExerciseTimeline.oneRepMaxSeries(h, "squat").size)
    }

    @Test
    fun patternsByIdAndByMuscleForCustomExercises() {
        assertEquals(MovementPattern.PUSH_HORIZONTAL, MovementPatterns.of(bench))
        assertEquals(MovementPattern.HINGE, MovementPatterns.of(ExerciseLibrary.byId("deadlift")!!))
        assertEquals(MovementPattern.PULL_VERTICAL, MovementPatterns.of(ExerciseLibrary.byId("pull_up")!!))
        val custom = Exercise("custom_x", "Máquina rara", MuscleGroup.HAMSTRINGS, emptyList(), Equipment.MACHINE)
        assertEquals(MovementPattern.HINGE, MovementPatterns.of(custom))
        assertNull(MovementPatterns.of(ExerciseLibrary.byId("running")!!))
        // Cada patrón tiene ejercicios para sugerir
        MovementPattern.entries.forEach { assertTrue(MovementPatterns.suggestions(it, emptyList()).isNotEmpty()) }
    }

    @Test
    fun balanceCountsWorkSetsAndFindsLaggingPatterns() {
        val now = epoch(d0.plusDays(20))
        val h = listOf(
            finishedWorkout("a", d0, bench to List(6) { done(60.0, 8) }, squat to List(4) { done(80.0, 5) }),
            finishedWorkout("b", d0.plusDays(3), ExerciseLibrary.byId("barbell_row")!! to List(3) { done(50.0, 8) }),
            // Fuera de las 4 semanas: no cuenta
            finishedWorkout("old", d0.minusDays(60), ExerciseLibrary.byId("deadlift")!! to List(5) { done(100.0, 5) }),
        )
        val b = MovementPatterns.balance(h, now)
        assertEquals(6.0, b.push, 1e-9)
        assertEquals(3.0, b.pull, 1e-9)
        assertEquals(2.0, b.pushPullRatio!!, 1e-9)
        assertEquals(0.0, b.hinge, 1e-9)
        assertNull(MovementPatterns.balance(emptyList(), now).pushPullRatio)
        // Bisagra, core y los verticales van cortos
        val lag = b.lagging()
        assertTrue(MovementPattern.HINGE in lag && MovementPattern.CORE in lag)
        assertFalse(MovementPattern.PUSH_HORIZONTAL in lag)
    }
}
