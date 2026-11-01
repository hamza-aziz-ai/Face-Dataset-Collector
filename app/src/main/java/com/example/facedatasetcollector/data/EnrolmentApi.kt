package com.example.facedatasetcollector.data

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ServerInfo(val clientId: String, val clientName: String, val noticeVersion: String, val noticeUrl: String)
data class NoticeSection(val heading: String, val body: String)
data class NoticeContact(val name: String, val email: String, val phone: String?)

/** [processor] is set only on a central server, where the operator runs it for the client. */
data class Notice(
    val version: String,
    val effectiveDate: String,
    val controller: String,
    val processor: String?,
    val contact: NoticeContact,
    val sections: List<NoticeSection>,
)

enum class EnrolmentStatus { NONE, PENDING, ENROLLED }
data class Person(val personId: String, val name: String, val employeeCode: String, val department: String?, val status: EnrolmentStatus)
data class PersonPage(val persons: List<Person>, val nextCursor: String?)
data class PoseProblem(val pose: String, val problem: String)

enum class JobStatus { PENDING, ACCEPTED, REJECTED }
data class JobResult(val status: JobStatus, val problems: List<PoseProblem>)

/** [NETWORK] means the outcome is unknown: the request may have reached the server. */
enum class ApiFailure { NETWORK, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, REJECTED_REQUEST, SERVER, BAD_RESPONSE }

class ApiException(val failure: ApiFailure, val code: String? = null) : Exception(code ?: failure.name)

/**
 * Client for the provisional CCTV contract in docs/features/FEATURE-001-enrolment.md. All calls block, so call them off the main thread.
 *
 * Never logs bodies, tokens, or identifiers. The client (tenant) is never sent: the server takes it from the login.
 */
class EnrolmentApi(
    private val baseUrl: String,
    private val http: OkHttpClient = defaultHttpClient(),
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    /** Held in memory only. Lost when the process dies, by design (docs/DATA.md). */
    @Volatile
    var token: String? = null

    fun serverInfo(): ServerInfo {
        val json = send(request("/api/v1/server-info").get().build())
        return parsed {
            ServerInfo(json.getString("client_id"), json.getString("client_name"), json.getString("notice_version"), json.getString("notice_url"))
        }
    }

    fun notice(noticeUrl: String): Notice {
        val json = send(request(noticeUrl).get().build())
        return parsed {
            val sections = json.getJSONArray("sections")
            val contact = json.getJSONObject("contact")
            Notice(
                json.getString("version"),
                json.getString("effective_date"),
                json.getString("controller"),
                json.optionalString("processor"),
                NoticeContact(contact.getString("name"), contact.getString("email"), contact.optionalString("phone")),
                List(sections.length()) { index ->
                    val section = sections.getJSONObject(index)
                    NoticeSection(section.getString("heading"), section.getString("body"))
                },
            )
        }
    }

    fun login(username: String, password: String) {
        val body = JSONObject().put("username", username).put("password", password)
        val json = send(request("/api/v1/auth/login").post(body.toRequestBody()).build())
        token = parsed { json.getString("token") }
    }

    fun signOut() {
        token = null
    }

    fun persons(query: String, cursor: String? = null): PersonPage {
        val url = "$baseUrl/api/v1/persons".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply { if (cursor != null) addQueryParameter("cursor", cursor) }
            .build()
        val json = send(authorized(Request.Builder().url(url)).get().build())
        return parsed {
            val people = json.getJSONArray("persons")
            PersonPage(
                List(people.length()) { index ->
                    val person = people.getJSONObject(index)
                    Person(
                        person.getString("person_id"), person.getString("name"), person.getString("employee_code"),
                        person.optionalString("department"), enrolmentStatus(person.getString("enrolment_status")),
                    )
                },
                json.optionalString("next_cursor"),
            )
        }
    }

    /** @param capturedAt ISO-8601 UTC time. Staff identity is deliberately absent: the server takes it from the session. */
    fun createConsent(personId: String, noticeVersion: String, capturedAt: String): String {
        val body = JSONObject()
            .put("person_id", personId)
            .put("notice_version", noticeVersion)
            .put("captured_at", capturedAt)
            .put("method", "staff_attested_in_app")
        val json = send(authorized(request("/api/v1/consents")).post(body.toRequestBody()).build())
        return parsed { json.getString("consent_id") }
    }

    /**
     * Uploads one set and retries only when the outcome is unknown or the server failed. Every retry reuses the same key, so a request that did land cannot create a second job.
     *
     * @return the job id. After this returns the server holds the photos, so the caller may discard them (202 is durable).
     */
    fun upload(personId: String, consentId: String, photos: Map<Pose, ByteArray>, key: String = UUID.randomUUID().toString()): String {
        val parts = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("person_id", personId)
            .addFormDataPart("consent_id", consentId)
        photos.forEach { (pose, bytes) ->
            parts.addFormDataPart(pose.wireName, "${pose.wireName}.jpg", bytes.toRequestBody(JPEG))
        }
        val call = authorized(request("/api/v1/enrolments")).header("Idempotency-Key", key).post(parts.build()).build()

        var attempt = 1
        while (true) {
            try {
                return send(call).let { json -> parsed { json.getString("job_id") } }
            } catch (e: ApiException) {
                val retryable = e.failure == ApiFailure.NETWORK || e.failure == ApiFailure.SERVER
                if (!retryable || attempt >= MAX_UPLOAD_ATTEMPTS) throw e
                sleep(BACKOFF_MS shl (attempt - 1))
                attempt++
            }
        }
    }

    fun job(jobId: String): JobResult {
        val json = send(authorized(request("/api/v1/enrolments/$jobId")).get().build())
        return parsed {
            val problems = json.getJSONArray("problems")
            JobResult(
                when (val status = json.getString("status")) {
                    "pending" -> JobStatus.PENDING
                    "accepted" -> JobStatus.ACCEPTED
                    "rejected" -> JobStatus.REJECTED
                    else -> throw ApiException(ApiFailure.BAD_RESPONSE, status)
                },
                List(problems.length()) { index ->
                    val problem = problems.getJSONObject(index)
                    PoseProblem(problem.getString("pose"), problem.getString("problem"))
                },
            )
        }
    }

    /** Polls until the job finishes or the limit passes, then returns PENDING. The result is still on the server, so giving up here loses nothing. */
    fun awaitJob(jobId: String): JobResult {
        repeat(MAX_POLLS) {
            val result = job(jobId)
            if (result.status != JobStatus.PENDING) return result
            sleep(POLL_MS)
        }
        return JobResult(JobStatus.PENDING, emptyList())
    }

    private fun request(path: String) = Request.Builder().url(baseUrl + path)

    private fun authorized(builder: Request.Builder): Request.Builder {
        val current = token ?: throw ApiException(ApiFailure.UNAUTHORIZED)
        return builder.headers(Headers.headersOf("Authorization", "Bearer $current"))
    }

    private fun send(request: Request): JSONObject {
        val (status, text) = try {
            http.newCall(request).execute().use { it.code to it.body.string() }
        } catch (_: IOException) {
            throw ApiException(ApiFailure.NETWORK)
        }
        if (status in 200..299) return parsed { JSONObject(text) }
        val code = try {
            JSONObject(text).optString("error").ifEmpty { null }
        } catch (_: JSONException) {
            null
        }
        throw ApiException(failureFor(status), code)
    }

    private fun <T> parsed(block: () -> T): T = try {
        block()
    } catch (_: JSONException) {
        throw ApiException(ApiFailure.BAD_RESPONSE)
    }

    private fun JSONObject.toRequestBody(): RequestBody = toString().toRequestBody(JSON)

    /** optString would turn a missing or JSON-null value into an empty or "null" string. */
    private fun JSONObject.optionalString(key: String): String? = if (isNull(key)) null else getString(key)

    private fun enrolmentStatus(wire: String) = when (wire) {
        "none" -> EnrolmentStatus.NONE
        "pending" -> EnrolmentStatus.PENDING
        "enrolled" -> EnrolmentStatus.ENROLLED
        else -> throw ApiException(ApiFailure.BAD_RESPONSE, wire)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        val JPEG = "image/jpeg".toMediaType()

        // ponytail: fixed values, tune from measured upload and server check times.
        const val MAX_UPLOAD_ATTEMPTS = 3
        const val BACKOFF_MS = 1_000L
        const val POLL_MS = 2_000L
        const val MAX_POLLS = 30
        const val PAGE_SIZE = 50

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // A redirect could move face photos to a host staff never saw.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        fun failureFor(status: Int) = when (status) {
            401 -> ApiFailure.UNAUTHORIZED
            403 -> ApiFailure.FORBIDDEN
            404 -> ApiFailure.NOT_FOUND
            409 -> ApiFailure.CONFLICT
            in 500..599 -> ApiFailure.SERVER
            in 300..399 -> ApiFailure.BAD_RESPONSE
            else -> ApiFailure.REJECTED_REQUEST
        }
    }
}
