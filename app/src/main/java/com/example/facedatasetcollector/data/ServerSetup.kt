package com.example.facedatasetcollector.data

import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException

/** What a setup QR code gives the app. [clientId] is null when the address was typed, and is then taken from server-info. */
data class ServerSetup(val baseUrl: String, val clientId: String?) {
    /** A setup code names its client, so a server answering as anyone else is refused before anything is stored. */
    fun requireClient(serverClientId: String) {
        if (clientId != null && clientId != serverClientId) {
            throw InvalidServerAddress("This setup code does not match the server. Ask your administrator for a new code.")
        }
    }
}

class InvalidServerAddress(message: String) : Exception(message)

/**
 * Trust boundary: a typed address or scanned QR code decides where face photos go, so anything other than a plain server origin is refused.
 *
 * @param allowLoopbackHttp true only in debug builds, for the local mock server.
 * @return the address as `scheme://host[:port]`, with no trailing slash.
 */
fun normalizeBaseUrl(raw: String, allowLoopbackHttp: Boolean): String {
    val uri = try {
        URI(raw.trim())
    } catch (_: URISyntaxException) {
        throw InvalidServerAddress("This is not a valid server address.")
    }
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase() ?: throw InvalidServerAddress("Enter the full address, starting with https://")
    val isLoopback = host == "127.0.0.1" || host == "localhost"
    val allowed = scheme == "https" || (scheme == "http" && isLoopback && allowLoopbackHttp)
    if (!allowed) throw InvalidServerAddress("The server address must start with https://")
    // A username, query, or fragment has no place here and could disguise the real host.
    if (uri.userInfo != null || uri.query != null || uri.fragment != null) {
        throw InvalidServerAddress("Enter only the server address.")
    }
    if (!uri.path.isNullOrEmpty() && uri.path != "/") throw InvalidServerAddress("Enter only the server address.")
    // URI accepts any number here, and the HTTP client would crash on it later.
    if (uri.port != -1 && uri.port !in 1..65535) throw InvalidServerAddress("This is not a valid server address.")
    val port = if (uri.port == -1) "" else ":${uri.port}"
    return "$scheme://$host$port"
}

/**
 * Parses the signed QR payload. Without a valid signature a forged code could send staff passwords and photos to any server (D-020).
 *
 * @param keys public keys allowed to sign, by key ID.
 */
fun parseSetupCode(raw: String, allowLoopbackHttp: Boolean, keys: Map<String, String> = SetupKeys.trusted): ServerSetup {
    val json = try {
        JSONObject(raw)
    } catch (_: JSONException) {
        throw InvalidServerAddress("This is not a setup code.")
    }
    val baseUrl = json.optString("base_url")
    val clientId = json.optString("client_id")
    if (baseUrl.isEmpty() || clientId.isEmpty()) throw InvalidServerAddress("This setup code is incomplete.")
    val normalized = normalizeBaseUrl(baseUrl, allowLoopbackHttp)
    // Only the local mock runs unsigned, and normalizeBaseUrl allows http only for loopback in debug builds.
    val mockCode = allowLoopbackHttp && normalized.startsWith("http://")
    if (!mockCode && !isSignedSetupCode(baseUrl, clientId, json.optString("kid"), json.optString("sig"), keys)) {
        throw InvalidServerAddress("This setup code could not be verified. Ask your administrator for a new one.")
    }
    return ServerSetup(normalized, clientId)
}
