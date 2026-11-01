#!/usr/bin/env python3
"""MOCK enrolment server for development only. Never deploy this.

Implements the provisional CCTV contract in docs/features/FEATURE-001-enrolment.md, using only the standard library.
Runs two sample clients on separate ports so that both deployment shapes get simulated: one client-dedicated, one central.

Privacy rules (docs/DECISIONS.md D-004):
- Binds to loopback only. The phone reaches it through `adb reverse`.
- Photos are checked and discarded immediately. They are never stored or written to disk.
- Logs carry method, path, and status only, never bodies, credentials, tokens, or identifiers.
- All accounts are fake. All state is in memory and is lost on restart.

Run: python tools/mock_server/mock_server.py
Then: adb reverse tcp:8081 tcp:8081 && adb reverse tcp:8082 tcp:8082
"""
import argparse
import email.parser
import email.policy
import hashlib
import json
import secrets
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

BASE_POSES = ("front", "chin_up", "chin_down", "left_30", "right_30")
GLASSES_POSES = tuple(f"{pose}_glasses" for pose in BASE_POSES)
MAX_PHOTO_BYTES = 2 * 1024 * 1024
MAX_BODY_BYTES = 25 * 1024 * 1024
TOKEN_SECONDS = 8 * 60 * 60
CHECK_SECONDS = 2.0
SCENARIOS = {"normal", "reject", "server_error", "slow", "drop"}
SLOW_SECONDS = 20


def sample_client(client_id, client_name, destination, processor):
    return {
        "client_id": client_id,
        "client_name": client_name,
        "notice_version": "0.1-mock",
        "notice": {
            "version": "0.1-mock",
            "effective_date": "2026-11-01",
            "controller": client_name,
            **({"processor": processor} if processor else {}),
            "contact": {"name": "Sample Privacy Desk", "email": "privacy@example.com"},
            "sections": [
                {"heading": "What we collect", "body": "5 face photos, or 10 if you wear glasses."},
                {"heading": "Why", "body": f"To recognise you for attendance at {client_name}."},
                {"heading": "Where they go", "body": destination},
                {"heading": "How long", "body": "While you are enrolled. Deleted when you withdraw or leave, within [GRACE PERIOD]."},
                {"heading": "Your choice", "body": "You can refuse. You can withdraw later through the contact below, and your photos and face data are then removed."},
            ],
        },
        "users": {
            "enrol.operator": {"password": "mock-only-password", "role": "enrolment_operator"},
            "client.admin": {"password": "mock-only-password", "role": "client_admin"},
            "viewer.user": {"password": "mock-only-password", "role": "viewer"},
        },
        "persons": [
            {"person_id": "p-0412", "name": "Sample Person A", "employee_code": "EMP-0412", "department": "Stores", "enrolment_status": "none"},
            {"person_id": "p-0418", "name": "Sample Person B", "employee_code": "EMP-0418", "department": "Dispatch", "enrolment_status": "enrolled"},
            {"person_id": "p-0431", "name": "Sample Person C", "employee_code": "EMP-0431", "enrolment_status": "none"},
        ],
    }


def dedicated_client():
    return sample_client(
        "mock-dedicated", "Sample Dedicated Client",
        "Sample Dedicated Client's attendance server, run by Sample Dedicated Client.",
        None,
    )


def central_client():
    return sample_client(
        "mock-central", "Sample Central Client",
        "A server run by [OPERATOR] on behalf of Sample Central Client.",
        "[OPERATOR]",
    )


class State:
    """All state for one sample client. Guarded by one lock because the server is threaded."""

    def __init__(self, client):
        self.client = client
        self.lock = threading.Lock()
        self.sessions = {}
        self.consents = {}
        self.jobs = {}
        self.idempotency = {}
        self.scenario = "normal"


class Reject(Exception):
    def __init__(self, status, code, message=None):
        super().__init__(code)
        self.status = status
        self.body = {"error": code, "message": message or code}


def parse_multipart(content_type, body):
    """Returns (fields, files). Assumes a size-checked body. The email parser replaces the cgi module removed in Python 3.13."""
    message = email.parser.BytesParser(policy=email.policy.HTTP).parsebytes(
        b"Content-Type: " + content_type.encode("latin-1") + b"\r\n\r\n" + body
    )
    if not message.is_multipart():
        raise Reject(400, "not_multipart")
    fields, files = {}, {}
    for part in message.iter_parts():
        name = part.get_param("name", header="content-disposition")
        if not name:
            raise Reject(400, "unnamed_part")
        payload = part.get_payload(decode=True) or b""
        if part.get_filename() is not None:
            if name in files:
                raise Reject(422, "duplicate_pose", name)
            files[name] = payload
        else:
            fields[name] = payload.decode("utf-8", "replace")
    return fields, files


def check_pose_set(files):
    names = set(files)
    base, glasses = set(BASE_POSES), set(GLASSES_POSES)
    if names not in (base, base | glasses):
        raise Reject(422, "invalid_pose_set", "All 5 base poses are required, and the 5 glasses poses are all or none.")
    for pose, data in files.items():
        if len(data) > MAX_PHOTO_BYTES:
            raise Reject(413, "photo_too_large", pose)
        if not data.startswith(b"\xff\xd8\xff"):
            raise Reject(422, "not_jpeg", pose)


class Handler(BaseHTTPRequestHandler):
    server_version = "MockEnrolment"
    state = None  # Set per server in make_server.

    def log_message(self, format, *args):
        # Path only: a query string could hold a person's name.
        sys.stderr.write(f"{self.server.server_port} {self.command} {urlparse(self.path).path}\n")

    def reply(self, status, body):
        data = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def read_body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length > MAX_BODY_BYTES:
            raise Reject(413, "body_too_large")
        return self.rfile.read(length)

    def read_json(self):
        try:
            return json.loads(self.read_body() or b"{}")
        except ValueError:
            raise Reject(400, "bad_json")

    def session(self):
        header = self.headers.get("Authorization", "")
        token = header[7:] if header.startswith("Bearer ") else ""
        session = self.state.sessions.get(token)
        if session is None or session["expires"] < time.monotonic():
            raise Reject(401, "unauthorized")
        return session

    def enrolling_session(self):
        session = self.session()
        if session["role"] == "viewer":
            raise Reject(403, "role_cannot_enrol")
        return session

    def person(self, person_id):
        for person in self.state.client["persons"]:
            if person["person_id"] == person_id:
                return person
        # Same answer as a cross-client ID: never confirm that an ID exists elsewhere.
        raise Reject(404, "person_not_found")

    def apply_scenario(self):
        """Simulates real-world server trouble. Returns True when the connection was dropped on purpose."""
        scenario = self.state.scenario
        if scenario == "server_error":
            raise Reject(500, "simulated_server_error")
        if scenario == "slow":
            time.sleep(SLOW_SECONDS)
        if scenario == "drop":
            self.close_connection = True
            self.connection.close()
            return True
        return False

    def dispatch(self):
        path = urlparse(self.path).path
        route = (self.command, path)
        if route == ("POST", "/mock/scenario"):
            return self.set_scenario()
        if route == ("GET", "/api/v1/server-info"):
            client = self.state.client
            return self.reply(200, {
                "client_id": client["client_id"],
                "client_name": client["client_name"],
                "notice_version": client["notice_version"],
                "notice_url": "/api/v1/notice",
            })
        if route == ("GET", "/api/v1/notice"):
            return self.reply(200, self.state.client["notice"])
        if route == ("POST", "/api/v1/auth/login"):
            return self.login()
        if route == ("GET", "/api/v1/persons"):
            return self.persons()
        if route == ("POST", "/api/v1/consents"):
            return self.create_consent()
        if self.command == "DELETE" and path.startswith("/api/v1/consents/"):
            return self.withdraw_consent(path.rsplit("/", 1)[1])
        if route == ("POST", "/api/v1/enrolments"):
            return self.create_enrolment()
        if self.command == "GET" and path.startswith("/api/v1/enrolments/"):
            return self.job_status(path.rsplit("/", 1)[1])
        raise Reject(404, "not_found")

    def set_scenario(self):
        scenario = self.read_json().get("scenario")
        if scenario not in SCENARIOS:
            raise Reject(400, "unknown_scenario")
        self.state.scenario = scenario
        self.reply(200, {"scenario": scenario})

    def login(self):
        body = self.read_json()
        user = self.state.client["users"].get(body.get("username"))
        supplied = str(body.get("password", "")).encode("utf-8")
        if user is None or not secrets.compare_digest(supplied, user["password"].encode("utf-8")):
            raise Reject(401, "bad_credentials")
        token = secrets.token_urlsafe(24)
        with self.state.lock:
            self.state.sessions[token] = {
                "username": body["username"], "role": user["role"], "expires": time.monotonic() + TOKEN_SECONDS,
            }
        self.reply(200, {"token": token})

    def persons(self):
        self.session()
        params = parse_qs(urlparse(self.path).query)
        query = params.get("query", [""])[0].casefold()
        limit = int(params.get("limit", ["50"])[0])
        start = int(params.get("cursor", ["0"])[0])
        matches = [
            person for person in self.state.client["persons"]
            if query in person["name"].casefold() or query in person["employee_code"].casefold()
        ]
        end = start + limit
        self.reply(200, {"persons": matches[start:end], "next_cursor": str(end) if end < len(matches) else None})

    def create_consent(self):
        session = self.enrolling_session()
        body = self.read_json()
        self.person(body.get("person_id"))
        if body.get("notice_version") != self.state.client["notice_version"]:
            raise Reject(422, "notice_version_superseded")
        if body.get("method") != "staff_attested_in_app" or not body.get("captured_at"):
            raise Reject(422, "bad_consent")
        consent_id = str(uuid.uuid4())
        with self.state.lock:
            self.state.consents[consent_id] = {
                "person_id": body["person_id"], "staff": session["username"], "active": True,
            }
        self.reply(201, {"consent_id": consent_id})

    def withdraw_consent(self, consent_id):
        self.enrolling_session()
        with self.state.lock:
            consent = self.state.consents.get(consent_id)
            if consent is None:
                raise Reject(404, "consent_not_found")
            consent["active"] = False
        self.reply(202, {"deletion_id": str(uuid.uuid4())})

    def create_enrolment(self):
        self.enrolling_session()
        key = self.headers.get("Idempotency-Key")
        if not key:
            raise Reject(400, "idempotency_key_required")
        if self.apply_scenario():
            return
        body = self.read_body()
        digest = hashlib.sha256(body).hexdigest()
        with self.state.lock:
            seen = self.state.idempotency.get(key)
        if seen is not None:
            if seen["digest"] != digest:
                raise Reject(409, "idempotency_key_reused")
            return self.reply(202, {"job_id": seen["job_id"]})

        fields, files = parse_multipart(self.headers.get("Content-Type", ""), body)
        check_pose_set(files)
        person_id = fields.get("person_id")
        self.person(person_id)
        with self.state.lock:
            consent = self.state.consents.get(fields.get("consent_id"))
        if consent is None or not consent["active"] or consent["person_id"] != person_id:
            raise Reject(422, "no_active_consent")

        # WHY only names survive this point: the photos themselves are never kept.
        poses = sorted(files)
        job_id = str(uuid.uuid4())
        self.person(person_id)["enrolment_status"] = "pending"
        with self.state.lock:
            self.state.jobs[job_id] = {
                "person_id": person_id, "poses": poses,
                "reject": self.state.scenario == "reject", "ready_at": time.monotonic() + CHECK_SECONDS,
            }
            self.state.idempotency[key] = {"digest": digest, "job_id": job_id}
        self.reply(202, {"job_id": job_id})

    def job_status(self, job_id):
        self.session()
        with self.state.lock:
            job = self.state.jobs.get(job_id)
        if job is None:
            raise Reject(404, "job_not_found")
        if time.monotonic() < job["ready_at"]:
            return self.reply(200, {"status": "pending", "problems": []})
        if job["reject"]:
            self.person(job["person_id"])["enrolment_status"] = "none"
            problems = [
                {"pose": job["poses"][0], "problem": "face_too_small"},
                {"pose": job["poses"][-1], "problem": "too_blurry"},
            ]
            return self.reply(200, {"status": "rejected", "problems": problems})
        self.person(job["person_id"])["enrolment_status"] = "enrolled"
        self.reply(200, {"status": "accepted", "problems": []})

    def handle_any(self):
        try:
            self.dispatch()
        except Reject as reject:
            # Unread body bytes would corrupt the next request on this connection.
            self.close_connection = True
            self.reply(reject.status, reject.body)

    do_GET = do_POST = do_DELETE = handle_any


def make_server(client, port):
    state = State(client)
    handler = type("BoundHandler", (Handler,), {"state": state})
    # Loopback only, by design. Never change this to 0.0.0.0.
    return ThreadingHTTPServer(("127.0.0.1", port), handler)


def main():
    parser = argparse.ArgumentParser(description="MOCK enrolment server. Development only.")
    parser.add_argument("--dedicated-port", type=int, default=8081)
    parser.add_argument("--central-port", type=int, default=8082)
    args = parser.parse_args()
    servers = [make_server(dedicated_client(), args.dedicated_port), make_server(central_client(), args.central_port)]
    for server in servers:
        threading.Thread(target=server.serve_forever, daemon=True).start()
    print(f"MOCK server. Dedicated: 127.0.0.1:{args.dedicated_port}. Central: 127.0.0.1:{args.central_port}. Fake data only.")
    threading.Event().wait()


if __name__ == "__main__":
    main()
