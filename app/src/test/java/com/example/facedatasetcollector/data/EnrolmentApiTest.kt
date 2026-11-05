package com.example.facedatasetcollector.data

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Bytes are synthetic. Real photos of people are never allowed in tests. */
class EnrolmentApiTest {
    private val server = MockWebServer()
    private val sleeps = mutableListOf<Long>()
    private lateinit var api: EnrolmentApi
    private val photos = Pose.setFor(wearsGlasses = false).associateWith { byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) }

    @Before
    fun start() {
        server.start()
        api = EnrolmentApi(server.url("/").toString().trimEnd('/'), sleep = { sleeps += it })
    }

    @After
    fun stop() {
        server.close()
    }

    private fun respond(code: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private fun dropConnection() {
        // After the request is read, so the app cannot know whether it landed: the case the same key exists for.
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
    }

    private fun signedIn() {
        api.token = "test-token"
    }

    @Test
    fun loginKeepsTokenInMemoryAndSignOutClearsIt() {
        respond(200, """{"token":"abc","role":"enrolment_operator","expires_at":"2026-11-03T20:00:00Z"}""")
        api.login("enrol.operator", "secret")
        assertEquals("/api/auth/token", server.takeRequest().url.encodedPath)
        assertEquals("abc", api.token)
        api.signOut()
        assertNull(api.token)
    }

    @Test
    fun badLoginIsUnauthorizedAndLeavesNoToken() {
        respond(401, """{"error":"bad_credentials"}""")
        val failure = assertThrows(ApiException::class.java) { api.login("enrol.operator", "wrong") }
        assertEquals(ApiFailure.UNAUTHORIZED, failure.failure)
        assertEquals("bad_credentials", failure.code)
        assertNull(api.token)
    }

    @Test
    fun serverInfoIsParsed() {
        respond(200, """{"client_id":"c1","client_name":"Client","notice_version":"1","notice_url":"/n"}""")
        assertEquals(ServerInfo("c1", "Client", "1", "/n"), api.serverInfo())
        respond(200, """{"client_id":"c1","client_name":"Client","notice_version":"1","notice_url":"/n","deletion_days":90}""")
        assertEquals(90, api.serverInfo().deletionDays)
    }

    @Test
    fun protectedCallsNeedAToken() {
        val failure = assertThrows(ApiException::class.java) { api.persons("a") }
        assertEquals(ApiFailure.UNAUTHORIZED, failure.failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun personsSendsBearerTokenAndEncodesTheQuery() {
        signedIn()
        respond(200, """{"persons":[{"person_id":"p1","name":"Sample","employee_code":"E1","department":"D","enrolment_status":"pending"},{"person_id":"p2","name":"Other","employee_code":null,"enrolment_status":"none"}],"next_cursor":"n2"}""")
        val page = api.persons("a b&c", cursor = "n1")
        val request = server.takeRequest()
        assertEquals("Bearer test-token", request.headers["Authorization"])
        assertEquals("a b&c", request.url.queryParameter("query"))
        assertEquals("50", request.url.queryParameter("limit"))
        assertEquals("n1", request.url.queryParameter("cursor"))
        assertEquals(
            PersonPage(
                listOf(
                    Person("p1", "Sample", "E1", "D", EnrolmentStatus.PENDING),
                    Person("p2", "Other", null, null, EnrolmentStatus.NONE),
                ),
                "n2",
            ),
            page,
        )
    }

    @Test
    fun registerSendsTheIdNameAndOptionalDepartment() {
        signedIn()
        respond(201, """{"person_id":"INST-7"}""")
        respond(201, """{"person_id":"INST-8"}""")
        assertEquals(Person("INST-7", "Sample Person", null, "Stores", EnrolmentStatus.NONE), api.registerPerson("INST-7", "Sample Person", "Stores"))
        api.registerPerson("INST-8", "Other", null)
        val first = server.takeRequest()
        assertEquals("/api/v1/persons", first.url.encodedPath)
        assertEquals("Bearer test-token", first.headers["Authorization"])
        val sent = JSONObject(first.body!!.utf8())
        assertEquals(listOf("INST-7", "Sample Person", "Stores"), listOf(sent.getString("person_id"), sent.getString("name"), sent.getString("department")))
        assertFalse(JSONObject(server.takeRequest().body!!.utf8()).has("department"))
    }

    @Test
    fun aTakenIdIsAConflictWithItsCode() {
        signedIn()
        respond(409, """{"error":"person_exists"}""")
        val failure = assertThrows(ApiException::class.java) { api.registerPerson("INST-7", "Sample", null) }
        assertEquals(ApiFailure.CONFLICT, failure.failure)
        assertEquals("person_exists", failure.code)
    }

    @Test
    fun noticeIsParsedForBothDeployments() {
        val sections = """"sections":[{"heading":"Why","body":"Attendance"}]"""
        respond(200, """{"version":"1","effective_date":"2026-11-01","controller":"Client","contact":{"name":"Desk","email":"a@example.com"},$sections}""")
        val dedicated = api.notice("/n")
        assertNull(dedicated.processor)
        assertNull(dedicated.contact.phone)

        respond(200, """{"version":"1","effective_date":"2026-11-01","controller":"Client","processor":"Operator","contact":{"name":"Desk","email":"a@example.com","phone":"123"},$sections}""")
        val central = api.notice("/n")
        assertEquals("Operator", central.processor)
        assertEquals("123", central.contact.phone)
        assertEquals(listOf(NoticeSection("Why", "Attendance")), central.sections)
    }

    @Test
    fun noticeOnAnotherServerIsNeverFetched() {
        listOf("https://evil.example.com/notice", "//evil.example.com/notice").forEach { url ->
            val failure = assertThrows(url, ApiException::class.java) { api.notice(url) }
            assertEquals(ApiFailure.BAD_RESPONSE, failure.failure)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun serverNameIsAlwaysOneShortLine() {
        val longName = "x".repeat(200)
        respond(200, """{"client_id":"c","client_name":"Acme\n\n\n$longName","notice_version":"1","notice_url":"/n"}""")
        val name = api.serverInfo().clientName
        assertFalse(name.contains("\n"))
        assertTrue(name.startsWith("Acme x"))
        assertEquals(80, name.length)
    }

    @Test
    fun withdrawingConsentSendsAnAuthorizedDelete() {
        signedIn()
        respond(202, """{"deletion_id":"d1"}""")
        api.withdrawConsent("c1")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/consents/c1", request.url.encodedPath)
        assertTrue(request.headers["Authorization"]!!.startsWith("Bearer "))
    }

    @Test
    fun removalSendsTheReasonAndReturnsTheDeletionId() {
        signedIn()
        respond(202, """{"deletion_id":"d1"}""")
        assertEquals("d1", api.removeEnrolment("INST.0477", RemovalReason.LEFT))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/persons/INST.0477/enrolment", request.url.encodedPath)
        assertEquals("left", request.url.queryParameter("reason"))
        assertTrue(request.headers["Authorization"]!!.startsWith("Bearer "))

        respond(409, """{"error":"deletion_pending"}""")
        val failure = assertThrows(ApiException::class.java) { api.createConsent("INST.0477", "1.2", "2026-11-05T00:00:00Z") }
        assertEquals(ApiFailure.CONFLICT, failure.failure)
        assertEquals("deletion_pending", failure.code)
    }

    @Test
    fun consentNeverSendsStaffOrClient() {
        signedIn()
        respond(201, """{"consent_id":"c9"}""")
        assertEquals("c9", api.createConsent("p1", "1", "2026-10-31T10:00:00Z"))
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("staff_attested_in_app"))
        assertFalse(body.contains("client"))
        assertFalse(body.contains("staff\""))
    }

    @Test
    fun uploadSendsOnePartPerPoseWithIdempotencyKey() {
        signedIn()
        respond(202, """{"job_id":"j1"}""")
        assertEquals("j1", api.upload("p1", "c1", photos, "key-1"))
        val request = server.takeRequest()
        assertEquals("key-1", request.headers["Idempotency-Key"])
        val body = request.body!!.utf8()
        Pose.setFor(wearsGlasses = false).forEach { pose ->
            assertTrue(pose.wireName, body.contains("name=\"${pose.wireName}\"; filename=\"${pose.wireName}.jpg\""))
        }
        assertFalse(body.contains("client_id"))
    }

    @Test
    fun uploadSendsAReasonOnlyForAReEnrolment() {
        signedIn()
        respond(202, """{"job_id":"j1"}""")
        api.upload("p1", "c1", photos)
        assertFalse(server.takeRequest().body!!.utf8().contains("name=\"reason\""))

        respond(202, """{"job_id":"j2"}""")
        api.upload("p1", "c1", photos, reason = ReEnrolReason.POOR_PHOTOS)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("name=\"reason\""))
        assertTrue(body.contains("poor_photos"))
    }

    @Test
    fun aManualRetrySendsIdenticalBytes() {
        signedIn()
        respond(202, """{"job_id":"j1"}""")
        respond(202, """{"job_id":"j1"}""")
        api.upload("p1", "c1", photos, "key-1")
        api.upload("p1", "c1", photos.entries.reversed().associate { it.key to it.value }, "key-1")
        assertEquals(server.takeRequest().body!!.utf8(), server.takeRequest().body!!.utf8())
    }

    @Test
    fun uploadRetriesWithTheSameKeyAfterAnUnknownOutcome() {
        signedIn()
        dropConnection()
        respond(500, """{"error":"server"}""")
        respond(202, """{"job_id":"j1"}""")

        assertEquals("j1", api.upload("p1", "c1", photos))

        val keys = List(3) { server.takeRequest().headers["Idempotency-Key"] }
        assertEquals(1, keys.toSet().size)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
    }

    @Test
    fun noRetryGoesOutAfterSignOut() {
        signedIn()
        respond(500, "{}")
        api = EnrolmentApi(server.url("/").toString().trimEnd('/'), sleep = { api.signOut() }).apply { token = "test-token" }
        val failure = assertThrows(ApiException::class.java) { api.upload("p1", "c1", photos) }
        assertEquals(ApiFailure.UNAUTHORIZED, failure.failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun uploadGivesUpAfterTheAttemptLimit() {
        signedIn()
        repeat(3) { respond(500, "{}") }
        val failure = assertThrows(ApiException::class.java) { api.upload("p1", "c1", photos) }
        assertEquals(ApiFailure.SERVER, failure.failure)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun uploadNeverRetriesARefusal() {
        signedIn()
        respond(422, """{"error":"no_active_consent"}""")
        val failure = assertThrows(ApiException::class.java) { api.upload("p1", "c1", photos) }
        assertEquals(ApiFailure.REJECTED_REQUEST, failure.failure)
        assertEquals("no_active_consent", failure.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun redirectsAreNotFollowed() {
        signedIn()
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/other")).build())
        val failure = assertThrows(ApiException::class.java) { api.job("j1") }
        assertEquals(ApiFailure.BAD_RESPONSE, failure.failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun awaitJobPollsUntilDone() {
        signedIn()
        respond(200, """{"status":"pending","problems":[]}""")
        respond(200, """{"status":"rejected","problems":[{"pose":"left_30","problem":"the face is blurred (sharpness 14, below 40)","code":"blurred"},{"pose":null,"problem":"missing photo"}]}""")
        val result = api.awaitJob("j1")
        assertEquals(JobStatus.REJECTED, result.status)
        assertEquals(listOf(PoseProblem("left_30", "the face is blurred (sharpness 14, below 40)", "blurred"), PoseProblem(null, "missing photo")), result.problems)
        assertEquals(listOf(2_000L), sleeps)
    }

    @Test
    fun awaitJobStopsAtTheLimit() {
        signedIn()
        repeat(30) { respond(200, """{"status":"pending","problems":[]}""") }
        assertEquals(JobStatus.PENDING, api.awaitJob("j1").status)
        assertEquals(30, server.requestCount)
    }

    @Test
    fun malformedResponseIsAFailureNotACrash() {
        respond(200, "not json")
        assertEquals(ApiFailure.BAD_RESPONSE, assertThrows(ApiException::class.java) { api.serverInfo() }.failure)
        respond(200, """{"status":"unknown","problems":[]}""")
        signedIn()
        assertEquals(ApiFailure.BAD_RESPONSE, assertThrows(ApiException::class.java) { api.job("j1") }.failure)
    }

    @Test
    fun poseSetsMatchTheContract() {
        assertEquals(5, Pose.setFor(wearsGlasses = false).size)
        assertEquals(10, Pose.setFor(wearsGlasses = true).size)
        assertEquals(listOf("front", "chin_up", "chin_down", "left_30", "right_30"), Pose.setFor(wearsGlasses = false).map { it.wireName })
    }
}
