package com.example.facedatasetcollector.ui

import androidx.compose.ui.unit.dp
import com.example.facedatasetcollector.data.ApiFailure
import com.example.facedatasetcollector.data.cleanPersonName
import com.example.facedatasetcollector.data.isValidPersonId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun onlyTheHttpLoopbackMockIsTheMock() {
        assertTrue(isMockAddress("http://127.0.0.1:8081"))
        assertTrue(isMockAddress("http://localhost:8081"))
        assertFalse(isMockAddress("https://localhost:8443"))
        assertFalse(isMockAddress("https://127.0.0.1:8443"))
        assertFalse(isMockAddress("https://attendance.example.com"))
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
    fun poseNamesStayReadable() {
        assertEquals("Left 30", poseNameFor("left_30"))
        assertEquals("Chin up, with glasses", poseNameFor("chin_up_glasses"))
        assertEquals("something_new", poseNameFor("something_new"))
    }

    @Test
    fun personIdsFollowTheServerRule() {
        listOf("INST-7", "a", "0.b_c-d", "x".repeat(64), "CONSOLE", "COM10", "LPT").forEach { assertTrue(it, isValidPersonId(it)) }
        listOf("", ".hidden", "..", "-x", "_x", "a b", "a/b", "é1", "x".repeat(65), "CON", "con", "nul.txt", "COM1", "lpt9.log").forEach { assertFalse(it, isValidPersonId(it)) }
    }

    @Test
    fun namesAreTrimmedAndLimited() {
        assertEquals("Sample Person", cleanPersonName("  Sample Person "))
        assertNull(cleanPersonName("   "))
        assertEquals(120, cleanPersonName("n".repeat(120))!!.length)
        assertNull(cleanPersonName("n".repeat(121)))
    }

    @Test
    fun onlyKnownCodesGetASpecificMessage() {
        assertTrue(messageForCode("no_active_consent")!!.contains("consent"))
        assertEquals(null, messageForCode("anything_else"))
        assertTrue(messageForCode("deletion_pending")!!.contains("being removed"))
    }

    @Test
    fun removalPromiseComesFromTheServer() {
        assertEquals("Removal requested. Their photos and face data are deleted within 90 days.", removalMessage(90))
        assertFalse(removalMessage(null).contains("days"))
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
