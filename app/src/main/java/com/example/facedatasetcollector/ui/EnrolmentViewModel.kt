package com.example.facedatasetcollector.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.facedatasetcollector.BuildConfig
import com.example.facedatasetcollector.data.ApiException
import com.example.facedatasetcollector.data.EnrolmentApi
import com.example.facedatasetcollector.data.InvalidServerAddress
import com.example.facedatasetcollector.data.ServerStore
import com.example.facedatasetcollector.data.StoredServer
import com.example.facedatasetcollector.data.normalizeBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Connect : Screen
    data object SignIn : Screen
    data object ClientChanged : Screen
    data object SignedIn : Screen
}

/** Owns navigation and the session. The session lives only here, so losing the process signs staff out (docs/DATA.md). */
class EnrolmentViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ServerStore(application)
    private var api: EnrolmentApi? = null

    var server by mutableStateOf(store.load())
        private set
    var screen by mutableStateOf<Screen>(if (server == null) Screen.Connect else Screen.SignIn)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        api = server?.let { EnrolmentApi(it.baseUrl) }
    }

    /** First-time setup. Trusts the server's identity only after it answers. */
    fun connect(address: String) = run {
        val baseUrl = normalizeBaseUrl(address, allowLoopbackHttp = BuildConfig.DEBUG)
        val client = EnrolmentApi(baseUrl)
        val info = client.serverInfo()
        val connected = StoredServer(baseUrl, info.clientId, info.clientName)
        store.save(connected)
        onMain {
            api = client
            server = connected
            screen = Screen.SignIn
        }
    }

    fun signIn(username: String, password: String) = run {
        val client = api ?: return@run
        val expected = server ?: return@run
        // WHY before the password is sent: a changed server must never receive staff credentials.
        val info = client.serverInfo()
        if (!isSameClient(expected.clientId, info.clientId)) {
            onMain { screen = Screen.ClientChanged }
            return@run
        }
        client.login(username, password)
        onMain { screen = Screen.SignedIn }
    }

    fun signOut() {
        api?.signOut()
        error = null
        screen = Screen.SignIn
    }

    /** Forgets this device's server, so that setup starts again. */
    fun resetServer() {
        api?.signOut()
        api = null
        store.clear()
        server = null
        error = null
        screen = Screen.Connect
    }

    val serverHost: String? get() = server?.baseUrl?.toUri()?.host

    val isMock: Boolean get() = BuildConfig.DEBUG && (serverHost == "127.0.0.1" || serverHost == "localhost")

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: InvalidServerAddress) {
                error = e.message
            } catch (e: ApiException) {
                error = messageFor(e.failure)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
}
