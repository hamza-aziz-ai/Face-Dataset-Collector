package com.example.facedatasetcollector.ui

import com.example.facedatasetcollector.data.ApiFailure
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
    fun everyFailureHasAMessageWithNoServerDetail() {
        ApiFailure.entries.forEach { failure ->
            val message = messageFor(failure)
            assertTrue(failure.name, message.endsWith("."))
            assertFalse(failure.name, message.contains(failure.name))
        }
    }
}
