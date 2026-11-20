package com.bestrong.core

import com.bestrong.core.library.ExerciseInfo
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.stats.MovementPattern
import com.bestrong.core.stats.MovementPatterns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Propuesta #6: crunch abdominal en máquina (sentado). */
class Propuesta6Test {
    private val machineCrunch = ExerciseLibrary.byId("machine_crunch")!!

    @Test fun machineCrunchIsAnAbsMachineExercise() {
        assertEquals(MuscleGroup.ABS, machineCrunch.primaryMuscle)
        assertEquals(Equipment.MACHINE, machineCrunch.equipment)
        assertEquals(MovementPattern.CORE, MovementPatterns.of(machineCrunch))
        assertEquals("Seated Machine Crunch", ExerciseLibrary.displayName(machineCrunch, "en"))
        assertNotNull(ExerciseInfo.instructions("machine_crunch", "es"))
        assertNotNull(ExerciseInfo.instructions("machine_crunch", "en"))
    }

    @Test fun machineCrunchIsFoundBySearchAndImport() {
        assertTrue("machine_crunch" in ExerciseLibrary.search(ExerciseLibrary.all, "crunch maquina").map { it.id })
        assertTrue("machine_crunch" in ExerciseLibrary.search(ExerciseLibrary.all, "crunch abdominal sentado").map { it.id })
        assertEquals("machine_crunch", ExerciseLibrary.englishAliases["Crunch (Machine)"])
    }
}
