"""Checks the mock against the provisional contract. Run: python -m unittest discover -s tools/mock_server"""
import http.client
import json
import threading
import time
import unittest
import uuid

import mock_server

# Starts with the JPEG magic bytes and holds no image. No real photos are ever used in tests.
FAKE_JPEG = b"\xff\xd8\xff\xe0synthetic-not-a-photo"
BOUNDARY = "mockboundary"


def multipart(person_id, consent_id, poses, data=FAKE_JPEG, reason=None):
    parts = []
    fields = [("person_id", person_id), ("consent_id", consent_id)] + ([("reason", reason)] if reason else [])
    for name, value in fields:
        parts.append(f'--{BOUNDARY}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    for pose in poses:
        head = f'--{BOUNDARY}\r\nContent-Disposition: form-data; name="{pose}"; filename="{pose}.jpg"\r\nContent-Type: image/jpeg\r\n\r\n'
        parts.append(head.encode() + data + b"\r\n")
    parts.append(f"--{BOUNDARY}--\r\n".encode())
    return b"".join(parts)


class MockServerTest(unittest.TestCase):
    def setUp(self):
        self.server = mock_server.make_server(mock_server.dedicated_client(), 0)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        self.state = self.server.RequestHandlerClass.state
        self.port = self.server.server_port
        mock_server.CHECK_SECONDS = 0

    def call(self, method, path, body=None, token=None, headers=None, timeout=5):
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=timeout)
        self.addCleanup(connection.close)
        sent = dict(headers or {})
        if token:
            sent["Authorization"] = f"Bearer {token}"
        if isinstance(body, dict):
            body = json.dumps(body).encode()
        connection.request(method, path, body=body, headers=sent)
        response = connection.getresponse()
        return response.status, json.loads(response.read() or b"{}")

    def login(self, username="enrol.operator"):
        status, body = self.call("POST", "/api/auth/token", {"username": username, "password": "mock-only-password"})
        self.assertEqual(200, status)
        return body["token"]

    def consent(self, token, person_id="p-0412"):
        status, body = self.call("POST", "/api/v1/consents", {
            "person_id": person_id, "notice_version": "0.2-mock",
            "captured_at": "2026-10-31T10:00:00Z", "method": "staff_attested_in_app",
        }, token)
        self.assertEqual(201, status)
        return body["consent_id"]

    def upload(self, token, consent_id, poses=mock_server.BASE_POSES, key=None, person_id="p-0412", data=FAKE_JPEG, reason=None):
        return self.call("POST", "/api/v1/enrolments", multipart(person_id, consent_id, poses, data, reason), token, {
            "Content-Type": f"multipart/form-data; boundary={BOUNDARY}",
            "Idempotency-Key": key or str(uuid.uuid4()),
        })

    def test_server_info_identifies_the_client(self):
        status, body = self.call("GET", "/api/v1/server-info")
        self.assertEqual(200, status)
        self.assertEqual("mock-dedicated", body["client_id"])
        self.assertEqual("0.2-mock", body["notice_version"])

    def test_bad_password_and_missing_token_are_refused(self):
        status, _ = self.call("POST", "/api/auth/token", {"username": "enrol.operator", "password": "wrong"})
        self.assertEqual(401, status)
        self.assertEqual(401, self.call("GET", "/api/v1/persons")[0])

    def test_token_reply_has_role_and_expiry(self):
        status, body = self.call("POST", "/api/auth/token", {"username": "viewer.user", "password": "mock-only-password"})
        self.assertEqual(200, status)
        self.assertEqual("viewer", body["role"])
        self.assertRegex(body["expires_at"], r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")

    def test_register_person(self):
        token = self.login()
        status, body = self.call("POST", "/api/v1/persons", {"person_id": "INST-77.a_b", "name": "  Sample Person D ", "department": " "}, token)
        self.assertEqual((201, "INST-77.a_b"), (status, body["person_id"]))
        _, body = self.call("GET", "/api/v1/persons?query=inst-77", token=token)
        self.assertEqual([{"person_id": "INST-77.a_b", "name": "Sample Person D", "employee_code": None, "department": None, "enrolment_status": "none"}], body["persons"])
        status, body = self.call("POST", "/api/v1/persons", {"person_id": "inst-77.A_B", "name": "Someone"}, token)
        self.assertEqual((409, "person_exists"), (status, body["error"]))

    def test_register_person_refuses_unsafe_ids_and_names(self):
        token = self.login()
        for person_id in [".hidden", "..", "-x", "CON", "con.txt", "COM1", "a b", "a/b", "x" * 65, "", 7]:
            status, body = self.call("POST", "/api/v1/persons", {"person_id": person_id, "name": "N"}, token)
            self.assertEqual((422, "invalid_person_id"), (status, body["error"]), person_id)
        for name in ["", "   ", "n" * 121, None]:
            status, body = self.call("POST", "/api/v1/persons", {"person_id": "ok1", "name": name}, token)
            self.assertEqual((422, "invalid_name"), (status, body["error"]), name)
        self.assertEqual(201, self.call("POST", "/api/v1/persons", {"person_id": "x" * 64, "name": "n" * 120}, token)[0])
        status, body = self.call("POST", "/api/v1/persons", {"person_id": "v1", "name": "N"}, self.login("viewer.user"))
        self.assertEqual((403, "role_cannot_enrol"), (status, body["error"]))

    def test_viewer_cannot_enrol(self):
        token = self.login("viewer.user")
        status, body = self.call("POST", "/api/v1/consents", {"person_id": "p-0412"}, token)
        self.assertEqual((403, "role_cannot_enrol"), (status, body["error"]))

    def test_roster_search_and_unknown_person(self):
        token = self.login()
        status, body = self.call("GET", "/api/v1/persons?query=0418", token=token)
        self.assertEqual(200, status)
        self.assertEqual(["p-0418"], [person["person_id"] for person in body["persons"]])
        status, _ = self.call("POST", "/api/v1/consents", {"person_id": "other-client"}, token)
        self.assertEqual(404, status)

    def test_roster_pages_and_status(self):
        token = self.login()
        _, first = self.call("GET", "/api/v1/persons?query=&limit=2", token=token)
        self.assertEqual(2, len(first["persons"]))
        self.assertEqual("2", first["next_cursor"])
        _, second = self.call("GET", "/api/v1/persons?query=&limit=2&cursor=2", token=token)
        self.assertEqual(["p-0431"], [person["person_id"] for person in second["persons"]])
        self.assertIsNone(second["next_cursor"])
        self.assertNotIn("department", second["persons"][0])

    def test_status_is_pending_during_checks(self):
        mock_server.CHECK_SECONDS = 30
        token = self.login()
        self.upload(token, self.consent(token))
        _, body = self.call("GET", "/api/v1/persons?query=0412", token=token)
        self.assertEqual("pending", body["persons"][0]["enrolment_status"])

    def test_notice_discloses_google_metrics(self):
        headings = [section["heading"] for section in self.call("GET", "/api/v1/notice")[1]["sections"]]
        self.assertIn("Google", headings)

    def test_notice_names_a_processor_only_when_central(self):
        self.assertNotIn("processor", self.call("GET", "/api/v1/notice")[1])
        self.assertIn("processor", mock_server.central_client()["notice"])

    def test_full_enrolment_is_accepted(self):
        token = self.login()
        status, body = self.upload(token, self.consent(token))
        self.assertEqual(202, status)
        status, body = self.call("GET", f"/api/v1/enrolments/{body['job_id']}", token=token)
        self.assertEqual((200, "accepted"), (status, body["status"]))

    def test_glasses_poses_are_all_or_none(self):
        token = self.login()
        consent_id = self.consent(token)
        self.assertEqual(202, self.upload(token, consent_id, mock_server.BASE_POSES + mock_server.GLASSES_POSES)[0])
        status, body = self.upload(token, consent_id, mock_server.BASE_POSES + mock_server.GLASSES_POSES[:2])
        self.assertEqual((422, "invalid_pose_set"), (status, body["error"]))

    def test_photo_limits(self):
        token = self.login()
        consent_id = self.consent(token)
        self.assertEqual("not_jpeg", self.upload(token, consent_id, data=b"not a jpeg")[1]["error"])
        oversized = FAKE_JPEG + b"x" * mock_server.MAX_PHOTO_BYTES
        self.assertEqual(413, self.upload(token, consent_id, data=oversized)[0])

    def test_upload_needs_active_consent_for_that_person(self):
        token = self.login()
        consent_id = self.consent(token)
        self.assertEqual("no_active_consent", self.upload(token, consent_id, person_id="p-0431")[1]["error"])
        self.assertEqual(202, self.call("DELETE", f"/api/v1/consents/{consent_id}", token=token)[0])
        self.assertEqual("no_active_consent", self.upload(token, consent_id)[1]["error"])

    def test_idempotency(self):
        token = self.login()
        consent_id = self.consent(token)
        key = str(uuid.uuid4())
        first = self.upload(token, consent_id, key=key)
        self.assertEqual(first, self.upload(token, consent_id, key=key))
        changed = self.upload(token, consent_id, key=key, data=FAKE_JPEG + b"changed")
        self.assertEqual((409, "idempotency_key_reused"), (changed[0], changed[1]["error"]))

    def test_rejected_job_reports_problems_and_keeps_consent_valid(self):
        token = self.login()
        consent_id = self.consent(token)
        self.call("POST", "/mock/scenario", {"scenario": "reject"})
        _, body = self.upload(token, consent_id)
        status, body = self.call("GET", f"/api/v1/enrolments/{body['job_id']}", token=token)
        self.assertEqual("rejected", body["status"])
        self.assertIn(None, [problem["pose"] for problem in body["problems"]])
        self.call("POST", "/mock/scenario", {"scenario": "normal"})
        self.assertEqual(202, self.upload(token, consent_id)[0])

    def test_photos_are_not_kept(self):
        token = self.login()
        self.upload(token, self.consent(token))
        self.assertNotIn(FAKE_JPEG.decode("latin-1"), repr(self.state.jobs) + repr(self.state.idempotency))

    def test_server_error_scenario(self):
        token = self.login()
        consent_id = self.consent(token)
        self.call("POST", "/mock/scenario", {"scenario": "server_error"})
        self.assertEqual(500, self.upload(token, consent_id)[0])

    def status_of(self, token, query):
        return self.call("GET", f"/api/v1/persons?query={query}", token=token)[1]["persons"][0]["enrolment_status"]

    def test_enrolled_person_needs_a_valid_reason(self):
        token = self.login()
        consent_id = self.consent(token, "p-0418")
        self.assertEqual("reason_required", self.upload(token, consent_id, person_id="p-0418")[1]["error"])
        self.assertEqual("invalid_reason", self.upload(token, consent_id, person_id="p-0418", reason="bored")[1]["error"])
        self.assertEqual(202, self.upload(token, consent_id, person_id="p-0418", reason="poor_photos")[0])
        self.assertEqual(["poor_photos"], [job["reason"] for job in self.state.jobs.values()])

    def test_rejected_re_enrolment_leaves_the_old_enrolment(self):
        token = self.login()
        self.call("POST", "/mock/scenario", {"scenario": "reject"})
        _, body = self.upload(token, self.consent(token, "p-0418"), person_id="p-0418", reason="other")
        self.assertEqual("pending", self.status_of(token, "0418"))
        self.assertEqual("rejected", self.call("GET", f"/api/v1/enrolments/{body['job_id']}", token=token)[1]["status"])
        self.assertEqual("enrolled", self.status_of(token, "0418"))

    def test_one_open_job_per_person(self):
        mock_server.CHECK_SECONDS = 30
        token = self.login()
        consent_id = self.consent(token)
        key = str(uuid.uuid4())
        first = self.upload(token, consent_id, key=key)
        self.assertEqual(first, self.upload(token, consent_id, key=key))
        status, body = self.upload(token, consent_id)
        self.assertEqual((409, "enrolment_open", first[1]["job_id"]), (status, body["error"], body["job_id"]))

    def test_open_job_is_the_newest_one(self):
        token = self.login()
        consent_id = self.consent(token, "p-0418")
        _, old = self.upload(token, consent_id, person_id="p-0418", reason="other")
        self.call("GET", f"/api/v1/enrolments/{old['job_id']}", token=token)
        mock_server.CHECK_SECONDS = 30
        _, new = self.upload(token, consent_id, person_id="p-0418", reason="other")
        self.assertEqual(new["job_id"], self.upload(token, consent_id, person_id="p-0418", reason="other")[1]["job_id"])

    def test_timeout_commits_one_job_and_a_retry_finds_it(self):
        self.addCleanup(setattr, mock_server, "TIMEOUT_SECONDS", mock_server.TIMEOUT_SECONDS)
        mock_server.TIMEOUT_SECONDS = 1
        token = self.login()
        consent_id = self.consent(token)
        self.call("POST", "/mock/scenario", {"scenario": "timeout"})
        key = str(uuid.uuid4())
        headers = {"Content-Type": f"multipart/form-data; boundary={BOUNDARY}", "Idempotency-Key": key}
        with self.assertRaises(TimeoutError):
            self.call("POST", "/api/v1/enrolments", multipart("p-0412", consent_id, mock_server.BASE_POSES), token, headers, timeout=0.3)
        self.assertEqual(202, self.upload(token, consent_id, key=key)[0])
        self.assertEqual(1, len(self.state.jobs))

    def test_polling_an_old_job_keeps_the_new_one_open(self):
        token = self.login()
        consent_id = self.consent(token, "p-0418")
        _, old = self.upload(token, consent_id, person_id="p-0418", reason="other")
        self.call("GET", f"/api/v1/enrolments/{old['job_id']}", token=token)
        mock_server.CHECK_SECONDS = 30
        self.upload(token, consent_id, person_id="p-0418", reason="other")
        self.call("GET", f"/api/v1/enrolments/{old['job_id']}", token=token)
        self.assertEqual("pending", self.status_of(token, "0418"))

    def test_two_uploads_at_once_make_one_job(self):
        mock_server.CHECK_SECONDS = 30
        token = self.login()
        for _ in range(10):
            self.state.reset()
            consent_id = self.consent(token)
            results = []
            threads = [threading.Thread(target=lambda: results.append(self.upload(token, consent_id)[0])) for _ in range(2)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            self.assertEqual([202, 409], sorted(results))

    def test_logs_hide_record_ids(self):
        self.assertEqual("/api/v1/enrolments/{id}", mock_server.loggable_path("/api/v1/enrolments/abc-123"))
        self.assertEqual("/api/v1/consents/{id}", mock_server.loggable_path("/api/v1/consents/abc-123"))
        self.assertEqual("/api/v1/persons", mock_server.loggable_path("/api/v1/persons?query=name"))

    def test_poll_drop_keeps_the_job(self):
        token = self.login()
        _, body = self.upload(token, self.consent(token))
        self.call("POST", "/mock/scenario", {"scenario": "poll_drop"})
        with self.assertRaises((ConnectionError, http.client.HTTPException)):
            self.call("GET", f"/api/v1/enrolments/{body['job_id']}", token=token)
        self.assertIn(body["job_id"], self.state.jobs)

    def test_withdrawal_cancels_the_open_job_and_keeps_the_old_enrolment(self):
        mock_server.CHECK_SECONDS = 30
        token = self.login()
        consent_id = self.consent(token, "p-0418")
        self.upload(token, consent_id, person_id="p-0418", reason="other")
        self.assertEqual("pending", self.status_of(token, "0418"))
        self.assertEqual(202, self.call("DELETE", f"/api/v1/consents/{consent_id}", token=token)[0])
        self.assertEqual("enrolled", self.status_of(token, "0418"))

    def test_reset_restores_the_roster(self):
        token = self.login()
        self.upload(token, self.consent(token))
        self.assertEqual(200, self.call("POST", "/mock/reset", {})[0])
        self.assertEqual("none", self.status_of(token, "0412"))
        self.assertFalse(self.state.jobs)

    def test_job_is_pending_until_checked(self):
        mock_server.CHECK_SECONDS = 30
        token = self.login()
        _, body = self.upload(token, self.consent(token))
        self.assertEqual("pending", self.call("GET", f"/api/v1/enrolments/{body['job_id']}", token=token)[1]["status"])


if __name__ == "__main__":
    unittest.main()
