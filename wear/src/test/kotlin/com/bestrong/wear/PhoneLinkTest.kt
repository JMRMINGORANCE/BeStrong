package com.bestrong.wear

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bestrong.wear.data.PhoneLink
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** El enlace que abre un entreno en el móvil es el que atiende la app del móvil (bestrong://workout/<id>). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PhoneLinkTest {
    @Test fun workoutLink() {
        val uri = PhoneLink.workoutUri("abc-123")
        assertEquals("bestrong", uri.scheme)
        assertEquals("workout", uri.host)
        assertEquals("abc-123", uri.lastPathSegment)
    }
}
