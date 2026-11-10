package com.bestrong.core

import com.bestrong.core.model.Equipment
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.training.BodyFocus
import com.bestrong.core.training.CareZone
import com.bestrong.core.training.FocusArea
import com.bestrong.core.training.Goal
import com.bestrong.core.training.Level
import com.bestrong.core.training.ProgramGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Propuesta #5: zonas a cuidar y a reforzar en el programa a medida. */
class BodyFocusTest {
    private val gym = Equipment.entries.toSet()

    @Test fun excludedIdsExistInLibrary() {
        CareZone.entries.forEach { zone ->
            BodyFocus.excludedIds(zone).forEach { id -> assertTrue("$zone $id", com.bestrong.core.library.ExerciseLibrary.byId(id) != null) }
        }
    }

    @Test fun careZonesRemoveRiskyExercisesButKeepDaysFull() {
        for (goal in Goal.entries) {
            for (days in 2..6) {
                for (zone in CareZone.entries) {
                    val p = ProgramGenerator.build(days, goal, gym, 60, care = setOf(zone))
                    assertEquals(days, p.routines.size)
                    p.routines.forEach { r ->
                        assertTrue("$goal $days $zone ${r.name}", r.exercises.size >= 3)
                        assertTrue("$goal $zone", r.exercises.none { it.exercise.id in BodyFocus.excludedIds(zone) })
                    }
                }
            }
        }
        // También en casa, sin material
        val home = ProgramGenerator.build(3, Goal.FAT_LOSS, setOf(Equipment.BODYWEIGHT), 45, care = setOf(CareZone.KNEE, CareZone.ANKLE))
        assertTrue(home.routines.all { r -> r.exercises.size >= 3 && r.exercises.none { it.exercise.id == "running" || it.exercise.id == "jump_rope" } })
    }

    @Test fun focusAddsSetsAndAnExtraExercise() {
        val base = ProgramGenerator.build(4, Goal.HYPERTROPHY, gym, 60)
        val chest = ProgramGenerator.build(4, Goal.HYPERTROPHY, gym, 60, focus = setOf(FocusArea.CHEST))
        fun chestSets(p: com.bestrong.core.training.GeneratedProgram) =
            p.routines.sumOf { r -> r.exercises.filter { it.exercise.primaryMuscle == MuscleGroup.CHEST }.sumOf { it.sets.size } }
        assertTrue(chestSets(chest) > chestSets(base))
        chest.routines.forEach { r -> assertTrue(r.exercises.all { it.sets.size <= BodyFocus.MAX_SETS }) }
        // Los días de torso llevan un ejercicio de pecho más; los de pierna, no
        chest.routines.zip(base.routines).forEach { (c, b) ->
            val trains = b.exercises.any { it.exercise.primaryMuscle == MuscleGroup.CHEST }
            val more = c.exercises.count { it.exercise.primaryMuscle == MuscleGroup.CHEST } - b.exercises.count { it.exercise.primaryMuscle == MuscleGroup.CHEST }
            if (trains && b.exercises.size < BodyFocus.MAX_STRENGTH) assertEquals(c.name, 1, more) else assertEquals(c.name, 0, more)
        }
    }

    @Test fun beginnersOnlyGetExtraSets() {
        val base = ProgramGenerator.build(3, Goal.HYPERTROPHY, gym, 60, level = Level.BEGINNER)
        val glutes = ProgramGenerator.build(3, Goal.HYPERTROPHY, gym, 60, level = Level.BEGINNER, focus = setOf(FocusArea.GLUTES))
        assertEquals(base.routines.map { it.exercises.size }, glutes.routines.map { it.exercises.size })
    }

    @Test fun atMostTwoAreasAndCareWins() {
        val all = ProgramGenerator.build(3, Goal.HYPERTROPHY, gym, 60, focus = FocusArea.entries.toSet())
        assertTrue(all.routines.all { r -> r.exercises.count { it.exercise.tracking != com.bestrong.core.model.TrackingType.CARDIO } <= BodyFocus.MAX_STRENGTH })
        val kneeLegs = ProgramGenerator.build(4, Goal.HYPERTROPHY, gym, 60, care = setOf(CareZone.KNEE), focus = setOf(FocusArea.LEGS))
        assertTrue(kneeLegs.routines.none { r -> r.exercises.any { it.exercise.id in BodyFocus.excludedIds(CareZone.KNEE) } })
    }

    @Test fun untrainedAreaGoesToLightestDays() {
        // Fuerza con 3 días de cuerpo completo: no hay gemelos, el extra va a dos días
        val calves = ProgramGenerator.build(3, Goal.STRENGTH, gym, 60, focus = setOf(FocusArea.CALVES))
        assertEquals(2, calves.routines.count { r -> r.exercises.any { it.exercise.primaryMuscle == MuscleGroup.CALVES } })
    }
}
