package com.bestrong.core

import com.bestrong.core.community.ProposalPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pedir ideas en buen momento y sin agobiar; respuestas nuevas del equipo. */
class ProposalPromptTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    @Test fun asksOnMilestones() {
        assertTrue(ProposalPrompt.shouldAsk(5, hasRecords = false, lastAskedMs = 0, nowMs = now, optedOut = false))
        assertTrue(ProposalPrompt.shouldAsk(20, hasRecords = false, lastAskedMs = 0, nowMs = now, optedOut = false))
        assertFalse(ProposalPrompt.shouldAsk(6, hasRecords = false, lastAskedMs = 0, nowMs = now, optedOut = false))
    }

    @Test fun asksWithRecordsAfterAFewWorkouts() {
        assertFalse(ProposalPrompt.shouldAsk(1, hasRecords = true, lastAskedMs = 0, nowMs = now, optedOut = false))
        assertTrue(ProposalPrompt.shouldAsk(7, hasRecords = true, lastAskedMs = 0, nowMs = now, optedOut = false))
    }

    @Test fun respectsGapAndOptOut() {
        assertFalse(ProposalPrompt.shouldAsk(20, hasRecords = true, lastAskedMs = now - 10 * day, nowMs = now, optedOut = false))
        assertTrue(ProposalPrompt.shouldAsk(20, hasRecords = true, lastAskedMs = now - 31 * day, nowMs = now, optedOut = false))
        assertFalse(ProposalPrompt.shouldAsk(20, hasRecords = true, lastAskedMs = 0, nowMs = now, optedOut = true))
    }

    @Test fun unreadTeamMessages() {
        assertFalse(ProposalPrompt.hasUnread(mapOf(3 to 1), mapOf(3 to 1)))
        assertTrue(ProposalPrompt.hasUnread(mapOf(3 to 2), mapOf(3 to 1)))
        assertTrue(ProposalPrompt.hasUnread(mapOf(4 to 1), emptyMap()))
        assertFalse(ProposalPrompt.hasUnread(mapOf(4 to 0), emptyMap()))
    }

    @Test fun encodeDecodeRoundTrip() {
        val counts = mapOf(5 to 2, 3 to 1)
        assertEquals("3:1,5:2", ProposalPrompt.encode(counts))
        assertEquals(counts, ProposalPrompt.decode(ProposalPrompt.encode(counts)))
        assertTrue(ProposalPrompt.decode(null).isEmpty())
        assertEquals(mapOf(2 to 1), ProposalPrompt.decode("x:1,2:1,,3"))
    }
}
