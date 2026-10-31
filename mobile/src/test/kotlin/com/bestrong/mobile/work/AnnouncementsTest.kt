package com.bestrong.mobile.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bestrong.mobile.notify.Announcement
import com.bestrong.mobile.notify.AnnouncementsWorker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Avisos del equipo: lectura de la respuesta del script y elección del que toca enseñar. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AnnouncementsTest {
    private val json = JSONObject(
        """
        {"ok": true, "avisos": [
          {"id": 3, "titulo": {"es": "Hola", "en": "Hi"}, "texto": {"es": "Nueva versión", "en": "New version"}},
          {"id": 2, "titulo": {}, "texto": {"fr": "Seulement en français"}},
          {"id": 0, "texto": "Sin id"},
          {"id": 5, "texto": {}}
        ]}
        """,
    )

    @Test fun picksDeviceLanguageThenSpanishThenAny() {
        val en = AnnouncementsWorker.parse(json, "en")
        assertEquals(listOf(Announcement(3, "Hi", "New version"), Announcement(2, "", "Seulement en français")), en)
        val de = AnnouncementsWorker.parse(json, "de")
        assertEquals(Announcement(3, "Hola", "Nueva versión"), de.first())
    }

    @Test fun showsOnlyTheNewestUnseen() {
        val list = AnnouncementsWorker.parse(json, "es")
        assertEquals(3, AnnouncementsWorker.pick(list, 0)?.id)
        assertEquals(3, AnnouncementsWorker.pick(list, 2)?.id)
        assertNull(AnnouncementsWorker.pick(list, 3))
    }

    @Test fun keepsPlainBoundedText() {
        val long = "a".repeat(800)
        val raw = JSONObject().put("avisos", org.json.JSONArray().put(JSONObject().put("id", 1).put("texto", "Hola\u0007 $long")))
        val text = AnnouncementsWorker.parse(raw, "es").single().text
        assertEquals(500, text.length)
        assertEquals("Hola a", text.take(6))
    }
}
