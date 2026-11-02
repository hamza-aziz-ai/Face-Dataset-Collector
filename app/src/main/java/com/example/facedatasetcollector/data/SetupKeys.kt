package com.example.facedatasetcollector.data

/**
 * Public keys that may sign setup codes, by key ID, as base64 of the DER public key. Public, so safe in git. The private key never enters this repo (D-020).
 *
 * Empty until the owner creates the key, so a release build refuses every code. See docs/SETUP-CODES.md. Keep an old key listed while its codes are still in use.
 */
object SetupKeys {
    val trusted: Map<String, String> = emptyMap()
}
