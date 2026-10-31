package com.example.facedatasetcollector.ui

import androidx.compose.ui.unit.dp
import com.example.facedatasetcollector.data.ApiFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {
    @Test
    fun aDifferentClientIdIsNeverTheSameClient() {
        assertTrue(isSameClient("client-a", "client-a"))
        assertFalse(isSameClient("client-a", "client-b"))
        assertFalse(isSameClient("client-a", "Client-A"))
        assertFalse(isSameClient("client-a", ""))
    }

    @Test
    fun twoPanesOnlyOnALargeScreenHeldSideways() {
        assertTrue(isWideLayout(1647.dp, 1100.dp))
        assertFalse(isWideLayout(1176.dp, 1647.dp))
        assertFalse(isWideLayout(800.dp, 400.dp))
        assertFalse(isWideLayout(390.dp, 844.dp))
    }

    @Test
    fun textGrowsOnlyOnTablets() {
        assertEquals(1f, tabletFontScale(390.dp))
        assertEquals(1.15f, tabletFontScale(600.dp))
        assertEquals(1.3f, tabletFontScale(1176.dp))
    }

    @Test
    fun poseNamesAndAdviceStayReadable() {
        assertEquals("Left 30", poseNameFor("left_30"))
        assertEquals("Chin up, with glasses", poseNameFor("chin_up_glasses"))
        assertEquals("something_new", poseNameFor("something_new"))
        assertEquals("Face too small. Move closer to the camera.", adviceFor("face_too_small"))
        assertTrue(adviceFor("a_code_added_later").endsWith("."))
    }

    @Test
    fun onlyKnownCodesGetASpecificMessage() {
        assertTrue(messageForCode("no_active_consent")!!.contains("consent"))
        assertEquals(null, messageForCode("anything_else"))
    }

    @Test
    fun timestampIsUtcIso8601() {
        assertEquals("1970-01-01T00:00:00Z", utcTimestamp(0))
        assertEquals("2026-10-31T10:30:00Z", utcTimestamp(1_793_442_600_000))
    }

    @Test
    fun everyFailureHasAMessageWithNoServerDetail() {
        ApiFailure.entries.forEach { failure ->
            val message = messageFor(failure)
            assertTrue(failure.name, message.endsWith("."))
            assertFalse(failure.name, message.contains(failure.name))
        }
    }
}
