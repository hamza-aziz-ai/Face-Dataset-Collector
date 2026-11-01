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
    BackHandler(enabled = model.screen == Screen.ScanCode) { model.cancelScan() }
    // While photos are on their way or being checked, back does nothing, so a rejection is not missed. After that the flow is finished, so back goes to the roster.
    BackHandler(enabled = model.screen == Screen.Upload) {
        if (model.uploadState != UploadState.Sending && model.uploadState != UploadState.Checking) model.finishEnrolment()
    }
    when (model.screen) {
        Screen.Connect -> ConnectScreen(model.busy, model.error, model::startScan, model::connect, modifier)
        Screen.ScanCode -> {
            // A bystander can be in view of the preview, even though no frame is kept.
            SecureWindow()
            QrScanScreen(model::connectWithCode, model::scanFailed, model::cancelScan, modifier)
        }
        Screen.SignIn -> SignInScreen(
            clientName = server?.clientName.orEmpty(),
            host = model.serverAddress.orEmpty(),
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
            onReEnrol = model::selectPerson,
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
                    uploadingTo = "${server?.clientName.orEmpty()} (${model.serverAddress.orEmpty()})",
                    showReadout = model.isMock,
                    onPhoto = model::onPhoto,
                    onCancel = model::cancelCapture,
                    modifier = modifier,
                )
            }
        }
        Screen.Upload -> when (val state = model.uploadState) {
            null -> Unit
            is UploadState.Rejected -> RejectedScreen(
                personName = person?.name.orEmpty(),
                photoCount = model.poseSet.size,
                problems = state.problems,
                stillEnrolled = model.reEnrolReason != null,
                onRetakeAll = model::retakeAll,
                onBackToRoster = model::finishEnrolment,
                modifier = modifier,
            )
            else -> UploadScreen(
                personName = person?.name.orEmpty(),
                photoCount = model.poseSet.size,
                noticeVersion = notice?.version.orEmpty(),
                state = state,
                isMock = model.isMock,
                onDone = model::finishEnrolment,
                modifier = modifier,
            )
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
                onUpload = model::upload,
                onCancel = model::cancelCapture,
                modifier = modifier,
            )
        }
    }
}
