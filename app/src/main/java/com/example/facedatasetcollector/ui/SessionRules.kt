package com.example.facedatasetcollector.ui

import com.example.facedatasetcollector.data.ApiFailure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The device stays bound to the client it was set up for. Any difference means staff must set up again. */
fun isSameClient(expected: String, actual: String): Boolean = expected == actual

/** ISO-8601 in UTC. SimpleDateFormat because java.time needs API 26 and minSdk is 23. */
fun utcTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))

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
