package com.example.facedatasetcollector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerSetupTest {
    // Made once by tools/setup_codes/sign_setup_code.py with a throwaway key, since deleted, so these prove the tool and the app agree on the format.
    private val keys = mapOf("k1" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPD0j6W5TDETIzzy1hPKPGFl9hcbRB5sF8HRqZQQZknvdadBJsCkttTe3c5SZXnRLzYVa/cMeytU/E6DWE/4JtA==")
    private val signedCode = """{"base_url":"https://client.example.com","client_id":"client-a","kid":"k1","sig":"MEUCIDlNUqqaR6jiEoIUXcrx9YK7+8xmPGPaSPwEzwRpj/fSAiEAp0xvqu7dm+7eVe6X8keouxryA8A6+q8y6Qbmug08LPM="}"""

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
        assertEquals(ServerSetup("https://client.example.com", "client-a"), parseSetupCode(signedCode, false, keys))
    }

    @Test
    fun refusesAnyChangeToASignedCode() {
        listOf(
            signedCode.replace("client.example.com", "evil.example.com"),
            signedCode.replace("client-a", "client-b"),
            signedCode.replace("\"k1\"", "\"k2\""),
            signedCode.replace("MEUCIDlN", "MEUCIDlM"),
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
