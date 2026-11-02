package com.example.facedatasetcollector.data

/**
 * Public keys that may sign setup codes, by key ID, as base64 of the DER public key. Public, so safe in git. The private key never enters this repo (D-020).
 *
 * See docs/SETUP-CODES.md. Keep an old key listed while its codes are still in use.
 */
object SetupKeys {
    val trusted: Map<String, String> = mapOf(
        // Owner's key, created 2026-11-02. SHA-256 of the DER: 73796d21dbe463e259f3c6d1b8c3fbd472f85f234801c1fabba69b53252f48d2
        "k1" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE0lE0crpBe3Q1ncTX9QE08WGpGpORvaKUXApF8d1tMGVHLoxxqgMUP/hsue7WbDjb6oZcEpn9jaFpFM6v9ZkeRQ==",
    )
}
