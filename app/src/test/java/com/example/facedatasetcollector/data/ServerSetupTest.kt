package com.example.facedatasetcollector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerSetupTest {
    @Test
    fun acceptsPlainHttpsOriginAndNormalizesIt() {
        assertEquals("https://client.example.com", normalizeBaseUrl("  HTTPS://Client.Example.com/ ", false))
        assertEquals("https://10.0.0.5:8443", normalizeBaseUrl("https://10.0.0.5:8443", false))
    }

    @Test
    fun refusesAnythingThatIsNotAPlainHttpsOrigin() {
        listOf(
            "http://client.example.com",
            "ftp://client.example.com",
            "client.example.com",
            "https://",
            "https://user:pass@client.example.com",
            "https://client.example.com/path",
            "https://client.example.com?next=https://evil.example.com",
            "https://client.example.com#fragment",
            "not an address",
        ).forEach { address ->
            assertThrows(address, InvalidServerAddress::class.java) { normalizeBaseUrl(address, false) }
        }
    }

    @Test
    fun httpIsOnlyForLoopbackInDebugBuilds() {
        assertEquals("http://127.0.0.1:8081", normalizeBaseUrl("http://127.0.0.1:8081", true))
        assertThrows(InvalidServerAddress::class.java) { normalizeBaseUrl("http://127.0.0.1:8081", false) }
        assertThrows(InvalidServerAddress::class.java) { normalizeBaseUrl("http://192.168.1.5", true) }
    }

    @Test
    fun parsesSetupCode() {
        assertEquals(
            ServerSetup("https://client.example.com", "client-a"),
            parseSetupCode("""{"base_url":"https://client.example.com","client_id":"client-a"}""", false),
        )
    }

    @Test
    fun aCodeBindsTheClientAndATypedAddressDoesNot() {
        val scanned = ServerSetup("https://client.example.com", "client-a")
        scanned.requireClient("client-a")
        assertThrows(InvalidServerAddress::class.java) { scanned.requireClient("client-b") }
        ServerSetup("https://client.example.com", null).requireClient("client-b")
    }

    @Test
    fun refusesBadSetupCodes() {
        listOf(
            "plain text",
            """{"base_url":"https://client.example.com"}""",
            """{"client_id":"client-a"}""",
            """{"base_url":"http://client.example.com","client_id":"client-a"}""",
        ).forEach { code ->
            assertThrows(code, InvalidServerAddress::class.java) { parseSetupCode(code, false) }
        }
    }
}
