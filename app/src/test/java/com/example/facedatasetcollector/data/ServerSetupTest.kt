package com.example.facedatasetcollector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerSetupTest {
    // Made once by tools/setup_codes/sign_setup_code.py with a throwaway key, since deleted, so these prove the tool and the app agree on the format.
    private val keys = mapOf("k1" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvdZaPNBI0sFh+WeU2e+fbJ6ohz6JmmH9gB4vfyVXEYpe0GqO/kEcEdt1AHtgDOfN2btkp2/N/H4D5iplrmilww==")
    private val signedCode = """{"base_url":"https://client.example.com/","client_id":"client-a","client_name":"M\u00fcller GmbH","kid":"k1","sig":"MEUCIBtyvB6lOheP0CJ+1GA46TQLj4Uqz5HoclvAkzGmHoONAiEAzrc9xORINJY5jR9utoatRKeroxAkn9+QerhgVyw0Mq0="}"""

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
    fun refusesPortsOutsideTheValidRange() {
        listOf("https://client.example.com:0", "https://client.example.com:65536", "https://client.example.com:99999").forEach { address ->
            assertThrows(address, InvalidServerAddress::class.java) { normalizeBaseUrl(address, false) }
        }
        assertEquals("https://client.example.com:65535", normalizeBaseUrl("https://client.example.com:65535", false))
    }

    @Test
    fun httpIsOnlyForLoopbackInDebugBuilds() {
        assertEquals("http://127.0.0.1:8081", normalizeBaseUrl("http://127.0.0.1:8081", true))
        assertThrows(InvalidServerAddress::class.java) { normalizeBaseUrl("http://127.0.0.1:8081", false) }
        assertThrows(InvalidServerAddress::class.java) { normalizeBaseUrl("http://192.168.1.5", true) }
    }

    @Test
    fun acceptsACodeSignedByTheTool() {
        // The trailing slash and the umlaut prove the raw signed text is verified, as UTF-8, and only then normalized.
        assertEquals(ServerSetup("https://client.example.com", "client-a", "Müller GmbH"), parseSetupCode(signedCode, false, keys))
    }

    @Test
    fun refusesAnyChangeToASignedCode() {
        listOf(
            signedCode.replace("client.example.com", "evil.example.com"),
            signedCode.replace("client-a", "client-b"),
            signedCode.replace("\"k1\"", "\"k2\""),
            signedCode.replace("MEUCIBty", "MEUCIBtA"),
            signedCode.replace("Müller", "Other").replace("M\\u00fcller", "Other"),
            signedCode.replace("\"sig\"", "\"nope\""),
            """{"base_url":"https://client.example.com","client_id":"client-a"}""",
        ).forEach { code ->
            assertThrows(code, InvalidServerAddress::class.java) { parseSetupCode(code, false, keys) }
        }
    }

    @Test
    fun aReleaseBuildWithNoKeysRefusesEveryCode() {
        assertThrows(InvalidServerAddress::class.java) { parseSetupCode(signedCode, false, emptyMap()) }
    }

    @Test
    fun onlyTheLocalMockRunsUnsignedAndOnlyInDebug() {
        val mock = """{"base_url":"http://127.0.0.1:8081","client_id":"mock-dedicated"}"""
        val unsignedHttps = """{"base_url":"https://client.example.com","client_id":"client-a"}"""
        assertEquals(ServerSetup("http://127.0.0.1:8081", "mock-dedicated"), parseSetupCode(mock, true, keys))
        assertThrows(InvalidServerAddress::class.java) { parseSetupCode(mock, false, keys) }
        assertThrows(InvalidServerAddress::class.java) { parseSetupCode(unsignedHttps, true, keys) }
    }

    @Test
    fun anOversizedCodeIsRefusedBeforeParsing() {
        assertThrows(InvalidServerAddress::class.java) { parseSetupCode("[".repeat(3000), false, keys) }
    }

    @Test
    fun refusesHostsTheHttpClientWouldRejectLater() {
        assertThrows(InvalidServerAddress::class.java) { normalizeBaseUrl("https://${"a".repeat(64)}.example.com", false) }
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
