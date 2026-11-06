package com.bestrong.core

import com.bestrong.core.library.Programs
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los programas predefinidos: se pueden crear, están completos y respetan el material y el objetivo. */
class ProgramsCatalogTest {
    private val bodyweight = setOf("calisthenics_3", "bodyweight_conditioning_3")
    private val dumbbells = setOf("dumbbell_full_body_3", "dumbbell_upper_lower_4", "fat_loss_home_3")
    private val withCardio = setOf("fat_loss_3", "fat_loss_4", "fat_loss_home_3", "bodyweight_conditioning_3", "endurance_circuit_3", "recomposition_4")

    @Test fun everyProgramBuildsWithEnoughExercises() {
        val ids = Programs.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Programs.all.size in 14..21)
        Programs.all.forEach { p ->
            val routines = p.toRoutines("es")
            assertEquals(p.id, p.routines.size, routines.size)
            routines.forEach { r -> assertTrue("${p.id} ${r.name}", r.exercises.size >= 3) }
            // Cada superserie, dos ejercicios distintos dentro de la rutina
            p.routines.forEach { t -> t.supersetPairs.forEach { (a, b) -> assertTrue(p.id, a != b && a in t.items.indices && b in t.items.indices) } }
        }
        // Todas las frecuencias de 2 a 6 días
        assertEquals((2..6).toSet(), Programs.all.map { it.daysPerWeek }.toSet())
    }

    @Test fun equipmentProgramsUseOnlyTheirEquipment() {
        val freeCardio = { e: com.bestrong.core.model.Exercise -> e.tracking == TrackingType.CARDIO && e.equipment == Equipment.OTHER }
        Programs.all.filter { it.id in bodyweight }.forEach { p ->
            p.toRoutines("es").flatMap { it.exercises }.forEach { re ->
                assertTrue("${p.id} ${re.exercise.id}", re.exercise.equipment == Equipment.BODYWEIGHT || freeCardio(re.exercise))
            }
        }
        Programs.all.filter { it.id in dumbbells }.forEach { p ->
            p.toRoutines("es").flatMap { it.exercises }.forEach { re ->
                assertTrue("${p.id} ${re.exercise.id}", re.exercise.equipment in setOf(Equipment.DUMBBELL, Equipment.BODYWEIGHT) || freeCardio(re.exercise))
            }
        }
    }

    @Test fun fatLossDaysEndWithTimedCardio() {
        assertEquals(withCardio, Programs.all.map { it.id }.filter { it in withCardio }.toSet())
        Programs.all.filter { it.id in withCardio }.forEach { p ->
            p.toRoutines("es").forEach { r ->
                val last = r.exercises.last()
                assertEquals("${p.id} ${r.name}", TrackingType.CARDIO, last.exercise.tracking)
                // Duración en segundos, sin reps
                assertTrue(last.sets.single().durationSec >= 10 * 60)
                assertEquals(0, last.sets.single().reps)
            }
        }
        // Los isométricos siguen con su duración
        val plank = Programs.all.first { it.id == "full_body_3" }.toRoutines("es").first().exercises.first { it.exercise.id == "plank" }
        assertEquals(45, plank.sets.first().durationSec)
    }

    @Test fun recompositionKeepsLoadAndModerateVolume() {
        val p = Programs.all.first { it.id == "recomposition_4" }
        assertEquals(4, p.daysPerWeek)
        p.toRoutines("es").forEach { r ->
            val lifts = r.exercises.dropLast(1)
            // Básicos pesados (6-8 reps) con descanso completo; nada de descansos cortos de definición
            assertTrue(r.name, lifts.first().sets.first { it.type != com.bestrong.core.model.SetType.WARMUP }.reps in 6..8)
            assertTrue(r.name, lifts.first().restSeconds >= 150)
            assertTrue(r.name, lifts.all { it.restSeconds >= 60 })
            // Cardio corto y suave: 10 min
            assertEquals(10 * 60, r.exercises.last().sets.single().durationSec)
        }
        // Unas 10-14 series semanales por músculo grande (volumen moderado, sostenible en déficit)
        val sets = p.routines.flatMap { it.items }.filter { it.exerciseId in setOf("bench_press", "db_incline_press", "dips") }.sumOf { it.sets }
        assertTrue("$sets", sets in 10..14)
    }
}
