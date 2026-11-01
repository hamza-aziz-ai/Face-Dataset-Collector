package com.example.facedatasetcollector.data

import android.content.Context
import androidx.core.content.edit

data class StoredServer(val baseUrl: String, val clientId: String, val clientName: String)

/**
 * Remembers which server this device was set up for. Holds no secrets, and backup is off, so plain preferences are enough. The token is never stored here.
 */
class ServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)

    fun load(): StoredServer? {
        val baseUrl = prefs.getString(BASE_URL, null) ?: return null
        val clientId = prefs.getString(CLIENT_ID, null) ?: return null
        val clientName = prefs.getString(CLIENT_NAME, null) ?: return null
        return StoredServer(baseUrl, clientId, clientName)
    }

    fun save(server: StoredServer) {
        prefs.edit {
            putString(BASE_URL, server.baseUrl)
            putString(CLIENT_ID, server.clientId)
            putString(CLIENT_NAME, server.clientName)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val BASE_URL = "base_url"
        const val CLIENT_ID = "client_id"
        const val CLIENT_NAME = "client_name"
    }
}
