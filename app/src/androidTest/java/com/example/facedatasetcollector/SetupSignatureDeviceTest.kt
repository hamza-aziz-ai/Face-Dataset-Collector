package com.example.facedatasetcollector

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.facedatasetcollector.data.InvalidServerAddress
import com.example.facedatasetcollector.data.parseSetupCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Android's crypto provider differs from the JVM's that unit tests use, so a signature must also verify on a device. Same throwaway key as ServerSetupTest, since deleted. */
@RunWith(AndroidJUnit4::class)
class SetupSignatureDeviceTest {
    private val keys = mapOf("k1" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvdZaPNBI0sFh+WeU2e+fbJ6ohz6JmmH9gB4vfyVXEYpe0GqO/kEcEdt1AHtgDOfN2btkp2/N/H4D5iplrmilww==")
    private val signedCode = """{"base_url":"https://client.example.com/","client_id":"client-a","client_name":"M\u00fcller GmbH","kid":"k1","sig":"MEUCIBtyvB6lOheP0CJ+1GA46TQLj4Uqz5HoclvAkzGmHoONAiEAzrc9xORINJY5jR9utoatRKeroxAkn9+QerhgVyw0Mq0="}"""

    @Test
    fun verifiesASignedCode() {
        assertEquals("M\u00fcller GmbH", parseSetupCode(signedCode, false, keys).clientName)
    }

    @Test
    fun refusesAChangedCode() {
        assertThrows(InvalidServerAddress::class.java) { parseSetupCode(signedCode.replace("client-a", "client-b"), false, keys) }
    }
}
