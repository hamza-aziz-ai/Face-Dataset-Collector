package com.example.facedatasetcollector.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** One screen at a time, chosen by the ViewModel. The flow is linear, so a navigation library would add nothing. */
@Composable
fun AppRoot(model: EnrolmentViewModel, modifier: Modifier = Modifier) {
    val server = model.server
    when (model.screen) {
        Screen.Connect -> ConnectScreen(model.busy, model.error, model::connect, modifier)
        Screen.SignIn -> SignInScreen(
            clientName = server?.clientName.orEmpty(),
            host = model.serverHost.orEmpty(),
            isMock = model.isMock,
            busy = model.busy,
            error = model.error,
            onSignIn = model::signIn,
            onChangeServer = model::resetServer,
            modifier = modifier,
        )
        Screen.ClientChanged -> ClientChangedScreen(server?.clientName.orEmpty(), model::resetServer, modifier)
        // ponytail: the old preview stands in until the roster screen lands.
        Screen.SignedIn -> CameraPreview(modifier)
    }
}
