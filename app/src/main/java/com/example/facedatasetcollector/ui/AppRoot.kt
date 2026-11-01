package com.example.facedatasetcollector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** One screen at a time, chosen by the ViewModel. The flow is linear, so a navigation library would add nothing. */
@Composable
fun AppRoot(model: EnrolmentViewModel, modifier: Modifier = Modifier) {
    val server = model.server
    val person = model.selectedPerson
    val notice = model.notice
    BackHandler(enabled = model.screen == Screen.Consent) { model.personDeclined() }
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
        Screen.SelectPerson -> PersonScreen(
            persons = model.persons,
            hasMore = model.hasMorePersons,
            isMock = model.isMock,
            busy = model.busy,
            error = model.error,
            onSearch = model::search,
            onLoadMore = model::loadMorePersons,
            onSelect = model::selectPerson,
            onSignOut = model::signOut,
            modifier = modifier,
        )
        Screen.Consent -> if (person != null && notice != null) {
            ConsentScreen(person, notice, model.busy, model.error, model::recordConsent, model::personDeclined, modifier)
        }
        Screen.Capture -> {
            SecureWindow()
            val pose = model.capturePose
            if (pose != null) {
                CaptureScreen(
                    pose = pose,
                    set = model.poseSet,
                    uploadingTo = "${server?.clientName.orEmpty()} (${model.serverHost.orEmpty()})",
                    onPhoto = model::onPhoto,
                    onCancel = model::cancelCapture,
                    modifier = modifier,
                )
            }
        }
        Screen.Review -> {
            SecureWindow()
            ReviewScreen(
                personName = person?.name.orEmpty(),
                photos = model.photos,
                set = model.poseSet,
                isMock = model.isMock,
                busy = model.busy,
                error = model.error,
                onRetake = model::retake,
                // ponytail: upload lands in the next PR. Until then this only clears the photos.
                onUpload = model::finishEnrolment,
                onCancel = model::cancelCapture,
                modifier = modifier,
            )
        }
    }
}
