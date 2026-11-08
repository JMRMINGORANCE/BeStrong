package com.bestrong.mobile.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bestrong.mobile.notify.ImplementedProposal
import com.bestrong.mobile.notify.ImplementedProposals
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Banner de propuesta hecha: solo las implementadas, una vez cada una y de la más antigua a la más nueva. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ImplementedProposalsTest {
    private val json = JSONObject(
        """
        {"ok": true, "proposals": [
          {"number": 7, "title": "Modo oscuro", "status": "implementada"},
          {"number": 5, "title": "Recomposición", "status": "implementada"},
          {"number": 6, "title": "Más ejercicios", "status": "en-desarrollo"},
          {"number": 0, "title": "Sin número", "status": "implementada"}
        ]}
        """,
    )

    @Test fun onlyImplementedOldestFirst() {
        val list = ImplementedProposals.parse(json)
        assertEquals(3, list.size)
        assertEquals(listOf(ImplementedProposal(5, "Recomposición"), ImplementedProposal(7, "Modo oscuro")), ImplementedProposals.toShow(list, emptySet()))
    }

    @Test fun eachOnlyOnce() {
        val list = ImplementedProposals.parse(json)
        assertEquals(listOf(ImplementedProposal(7, "Modo oscuro")), ImplementedProposals.toShow(list, setOf(5)))
        assertTrue(ImplementedProposals.toShow(list, setOf(5, 7)).isEmpty())
    }

    @Test fun countsOnlyTeamMessages() {
        val withMessages = JSONObject(
            """
            {"ok": true, "proposals": [
              {"number": 4, "messages": [{"fromUser": false, "text": "¿Qué ejercicio?"}, {"fromUser": true, "text": "Landmine"}]},
              {"number": 8},
              {"number": 0, "messages": [{"fromUser": false, "text": "x"}]}
            ]}
            """,
        )
        assertEquals(mapOf(4 to 1, 8 to 0), ImplementedProposals.teamMessages(withMessages))
    }

    @Test fun emptyOrBrokenResponse() {
        assertTrue(ImplementedProposals.parse(JSONObject("""{"ok": true}""")).isEmpty())
    }
}
