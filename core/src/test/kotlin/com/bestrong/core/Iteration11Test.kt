package com.bestrong.core

import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.ProgramTexts
import org.junit.Assert.assertEquals
import org.junit.Test

class Iteration11Test {
    @Test fun estimateAddsRunningRestAndShortWarmupRest() {
        val sets = listOf(WorkoutSet(type = SetType.WARMUP), WorkoutSet(), WorkoutSet())
        val w = Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(WorkoutExercise(exercise = bench, sets = sets, restSeconds = 180)))
        // 3 series × 40 s + 60 s tras el calentamiento + 180 s entre las efectivas
        assertEquals(3 * 40 + 60 + 180, WorkoutEditor.estimatedRemainingSeconds(w, nowEpochMs = 0))
        // Con 100 s de descanso aún por delante
        assertEquals(100 + 3 * 40 + 60 + 180, WorkoutEditor.estimatedRemainingSeconds(w, nowEpochMs = 0, restEndsAtEpochMs = 100_000))
    }

    @Test fun userPartKeepsWhatTheUserWroteInProgramNotes() {
        val gen = listOf(ProgramTexts.ES.amrapNote, ProgramTexts.EN.amrapNote)
        assertEquals("", ProgramTexts.userPart("T1 · Última serie: máximas repeticiones (+)", gen))
        assertEquals("", ProgramTexts.userPart("T2", gen))
        assertEquals("me costó", ProgramTexts.userPart("T1 · Última serie: máximas repeticiones (+) me costó", gen))
        assertEquals("hombro raro", ProgramTexts.userPart("hombro raro", gen))
    }
}
