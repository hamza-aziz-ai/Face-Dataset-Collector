package com.example.facedatasetcollector.data

import okio.ByteString.Companion.decodeBase64
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** The bytes a setup code signs. The prefix stops a signature made for anything else from passing as one of these. tools/setup_codes must build the same bytes. */
fun setupCodeMessage(baseUrl: String, clientId: String): ByteArray = "enrolment-setup-v1\n$baseUrl\n$clientId".toByteArray()

/** True only when [signature] is a valid ECDSA P-256 signature over this address and client, from a key in [keys]. */
fun isSignedSetupCode(baseUrl: String, clientId: String, keyId: String, signature: String, keys: Map<String, String>): Boolean {
    val publicKey = keys[keyId]?.decodeBase64()?.toByteArray() ?: return false
    val signed = signature.decodeBase64()?.toByteArray() ?: return false
    return try {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(setupCodeMessage(baseUrl, clientId))
            verify(signed)
        }
    } catch (_: GeneralSecurityException) {
        false
    }
}
