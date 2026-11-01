package com.example.facedatasetcollector.ui

import com.example.facedatasetcollector.data.ApiFailure
import com.example.facedatasetcollector.data.Pose
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The device stays bound to the client it was set up for. Any difference means staff must set up again. */
fun isSameClient(expected: String, actual: String): Boolean = expected == actual

/** Withdrawing during a re-enrolment ends that attempt only (CCTV 8c). */
const val RE_ENROL_STAYS = "This cancels this re-enrolment only. Their existing enrolment stays until they ask for it to be removed, through the withdrawal contact in the notice."

/** ISO-8601 in UTC. SimpleDateFormat because java.time needs API 26 and minSdk is 23. */
fun utcTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))

/** Specific server refusals. Returns null when the general message for the failure fits better. */
fun messageForCode(code: String): String? = when (code) {
    "no_active_consent" -> "This person has no active consent. Go back and record it again."
    "notice_version_superseded" -> "The notice has changed. Go back to the roster and show the new one."
    "invalid_pose_set" -> "Some photos are missing. Cancel and take them again."
    "photo_too_large", "body_too_large" -> "The photos are too large to send. Cancel and take them again."
    "role_cannot_enrol" -> "This account cannot enrol people."
    "enrolment_open" -> "This person already has an enrolment being checked. Cancel and wait for its result."
    "reason_required" -> "This person is already enrolled. Cancel and use Re-enrol on the roster."
    else -> null
}

/** The pose name from the contract, as staff know it. */
fun poseNameFor(wireName: String): String {
    val pose = Pose.entries.firstOrNull { it.wireName == wireName } ?: return wireName
    return if (pose.withGlasses) "${pose.label}, with glasses" else pose.label
}

/** What to do about a server photo problem. An unknown code still gives useful advice, because the contract is provisional. */
fun adviceFor(problem: String): String = when (problem) {
    "face_too_small" -> "Face too small. Move closer to the camera."
    "too_blurry" -> "Too blurry. Hold still until the photo is taken."
    "no_face", "multiple_faces" -> "One face must be clearly visible."
    "wrong_pose" -> "The head position did not match. Follow the on-screen prompt."
    else -> "This photo did not pass. Try again in even light."
}

/** What staff read. Says what happened and what to do, and never exposes server detail. */
fun messageFor(failure: ApiFailure): String = when (failure) {
    ApiFailure.NETWORK -> "Could not reach the server. Check the connection and try again."
    ApiFailure.UNAUTHORIZED -> "Wrong username or password."
    ApiFailure.FORBIDDEN -> "This account cannot enrol people."
    ApiFailure.SERVER -> "The server had a problem. Try again shortly."
    ApiFailure.NOT_FOUND,
    ApiFailure.CONFLICT,
    ApiFailure.REJECTED_REQUEST,
    ApiFailure.BAD_RESPONSE -> "Something went wrong. Try again, or ask your administrator."
}
