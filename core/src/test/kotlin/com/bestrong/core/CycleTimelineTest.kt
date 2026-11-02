package com.bestrong.core

import com.bestrong.core.training.CyclePhase
import com.bestrong.core.training.CycleTimeline
import com.bestrong.core.training.CycleTimelines
import com.bestrong.core.training.Mesocycle
import com.bestrong.core.training.PeriodizedPrograms
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CycleTimelineTest {
    private val zone = ZoneId.of("Europe/Madrid")

    @Test
    fun fiveThreeOneHasAccumulationIntensificationAndDeload() {
        val p = PeriodizedPrograms.byId("wendler_531_bbb")!!
        val t = CycleTimelines.forProgram(p, week = 2, cycle = 3, today = LocalDate.of(2026, 11, 4), history = emptyList(), zone = zone)!!
        assertEquals(CycleTimeline.Kind.PROGRAM, t.kind)
        assertEquals(listOf(CyclePhase.ACCUMULATION, CyclePhase.ACCUMULATION, CyclePhase.INTENSIFICATION, CyclePhase.DELOAD), t.weeks.map { it.phase })
        assertEquals(2, t.current!!.index)
        // La semana actual empieza el lunes de hoy; la primera, una semana antes
        assertEquals(LocalDate.of(2026, 11, 2), t.current!!.monday)
        assertEquals(LocalDate.of(2026, 10, 26), t.weeks.first().monday)
        assertEquals(2, t.weeksToDeload)
        // Press militar, peso muerto, press banca y sentadilla con 5/3/1 y con BBB
        assertEquals(8, t.intensity.size)
        assertEquals(0.95, t.intensity.first().percents[2]!!, 1e-9)
    }

    @Test
    fun linearProgramHasNoCycle() {
        val gzclp = PeriodizedPrograms.all.first { it.weeksPerCycle == 1 }
        assertNull(CycleTimelines.forProgram(gzclp, 1, 1, LocalDate.of(2026, 11, 4), emptyList(), zone))
    }

    @Test
    fun mesocycleMarksIntensificationAndHalvesDeloadPlan() {
        val m = Mesocycle(LocalDate.of(2026, 10, 5).toEpochDay(), accumulationWeeks = 4)
        val t = CycleTimelines.forMesocycle(m, LocalDate.of(2026, 11, 4), emptyList(), zone) { 20 }
        assertEquals(listOf(CyclePhase.ACCUMULATION, CyclePhase.ACCUMULATION, CyclePhase.ACCUMULATION, CyclePhase.INTENSIFICATION, CyclePhase.DELOAD), t.weeks.map { it.phase })
        assertEquals(5, t.current!!.index)
        assertEquals(10, t.weeks.last().plannedSets)
        assertEquals(0, t.weeksToDeload)
        assertNotNull(t.nextDeload)
    }
}
