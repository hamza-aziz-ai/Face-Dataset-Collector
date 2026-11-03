#!/usr/bin/env python3
"""MOCK enrolment server for development only. Never deploy this.

Implements the provisional CCTV contract in docs/features/FEATURE-001-enrolment.md, using only the standard library.
Runs two sample clients on separate ports so that both deployment shapes get simulated: one client-dedicated, one central.

Privacy rules (docs/DECISIONS.md D-004):
- Binds to loopback only. The phone reaches it through `adb reverse`.
- Photos are checked and discarded immediately. They are never stored or written to disk.
- Logs carry method, path, and status only, never bodies, credentials, tokens, or identifiers.
- All accounts are fake. All state is in memory and is lost on restart.

Test runs start clean with POST /mock/reset.

Run: python tools/mock_server/mock_server.py
Then: adb reverse tcp:8081 tcp:8081 && adb reverse tcp:8082 tcp:8082
"""
import argparse
import copy
import email.parser
import email.policy
import hashlib
import json
import re
import secrets
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

BASE_POSES = ("front", "chin_up", "chin_down", "left_30", "right_30")
GLASSES_POSES = tuple(f"{pose}_glasses" for pose in BASE_POSES)
MAX_PHOTO_BYTES = 2_000_000
MAX_BODY_BYTES = 25 * 1024 * 1024
TOKEN_SECONDS = 8 * 60 * 60
CHECK_SECONDS = 2.0
SCENARIOS = {"normal", "reject", "server_error", "slow", "drop", "timeout", "poll_drop"}
REENROL_REASONS = {"appearance_changed", "poor_photos", "other"}
PERSON_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
WINDOWS_DEVICE = re.compile(r"(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?", re.IGNORECASE)
SLOW_SECONDS = 20
# Longer than the app's 30 s read timeout.
TIMEOUT_SECONDS = 35


def sample_client(client_id, client_name, destination, processor):
    return {
        "client_id": client_id,
        "client_name": client_name,
        "notice_version": "0.2-mock",
        "notice": {
            "version": "0.2-mock",
            "effective_date": "2026-11-01",
            "controller": client_name,
            **({"processor": processor} if processor else {}),
            "contact": {"name": "Sample Privacy Desk", "email": "privacy@example.com"},
            "sections": [
                {"heading": "What we collect", "body": "5 face photos, or 10 if you wear glasses."},
                {"heading": "Why", "body": f"To recognise you for attendance at {client_name}."},
                {"heading": "Where they go", "body": destination},
                {"heading": "Google", "body": "Google's on-device face detection sends Google technical usage data from this device, such as device model, app version, and timings. It does not send your photos."},
                {"heading": "How long", "body": "While you are enrolled. Deleted within 30 days after you withdraw or leave."},
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
        self.original = client
        self.lock = threading.Lock()
        self.sessions = {}
        self.reset()

    def reset(self):
        """Back to the sample roster, so test runs do not depend on each other. Sessions survive."""
        self.client = copy.deepcopy(self.original)
        self.consents = {}
        self.jobs = {}
        self.idempotency = {}
        self.scenario = "normal"


class Reject(Exception):
    def __init__(self, status, code, message=None, **extra):
        super().__init__(code)
        self.status = status
        self.body = {"error": code, "message": message or code, **extra}


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


def loggable_path(raw):
    """Path only: a query string could hold a person's name, and the last segment of these routes is a record ID."""
    return re.sub(r"(/api/v1/(?:enrolments|consents))/[^/]+$", r"\1/{id}", urlparse(raw).path)


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
        # A malformed request is logged before `path` and `command` exist.
        path = loggable_path(getattr(self, "path", ""))
        sys.stderr.write(f"{self.server.server_port} {getattr(self, 'command', '-')} {path}\n")

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
            # A real 500 comes after the upload arrives. Refusing with it unread can also stall a wireless adb tunnel.
            self.read_body()
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
        if route == ("POST", "/mock/reset"):
            with self.state.lock:
                self.state.reset()
            return self.reply(200, {})
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
        if route == ("POST", "/api/auth/token"):
            return self.login()
        if route == ("GET", "/api/v1/persons"):
            return self.persons()
        if route == ("POST", "/api/v1/persons"):
            return self.register_person()
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
        # Any role gets a token, as on CCTV. A viewer is refused later, with 403.
        expires_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() + TOKEN_SECONDS))
        self.reply(200, {"token": token, "role": user["role"], "expires_at": expires_at})

    def persons(self):
        self.session()
        params = parse_qs(urlparse(self.path).query)
        query = params.get("query", [""])[0].casefold()
        limit = int(params.get("limit", ["50"])[0])
        start = int(params.get("cursor", ["0"])[0])
        matches = [
            person for person in self.state.client["persons"]
            if any(query in (person.get(field) or "").casefold() for field in ("person_id", "name", "employee_code"))
        ]
        end = start + limit
        self.reply(200, {"persons": matches[start:end], "next_cursor": str(end) if end < len(matches) else None})

    def register_person(self):
        """CCTV 8c rules: the ID becomes a folder name on a Windows host, and is unique in any letter case."""
        self.enrolling_session()
        body = self.read_json()
        person_id = body.get("person_id")
        name = body.get("name")
        department = body.get("department")
        if not isinstance(person_id, str) or not PERSON_ID.fullmatch(person_id) or WINDOWS_DEVICE.fullmatch(person_id):
            raise Reject(422, "invalid_person_id")
        if not isinstance(name, str) or not 1 <= len(name.strip()) <= 120:
            raise Reject(422, "invalid_name")
        with self.state.lock:
            if any(person["person_id"].lower() == person_id.lower() for person in self.state.client["persons"]):
                raise Reject(409, "person_exists")
            self.state.client["persons"].append({
                "person_id": person_id, "name": name.strip(), "employee_code": None,
                "department": department.strip() if isinstance(department, str) and department.strip() else None,
                "enrolment_status": "none",
            })
        self.reply(201, {"person_id": person_id})

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
            # An open job under this consent is cancelled. Any earlier enrolment stays as it was (CCTV, provisional 8c).
            for job in self.state.jobs.values():
                if job["consent_id"] == consent_id and not job["finished"]:
                    job["finished"] = job["reject"] = True
                    self.person(job["person_id"])["enrolment_status"] = job["previous"]
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
        job_id, new = self.commit_job(key, digest, body)
        if new and self.state.scenario == "timeout":
            # The job is committed and the answer is late, so the client gives up with the outcome unknown.
            time.sleep(TIMEOUT_SECONDS)
        try:
            self.reply(202, {"job_id": job_id})
        except OSError:
            # The client already left, which is the point of the timeout scenario.
            self.close_connection = True

    def commit_job(self, key, digest, body):
        """Returns (job_id, new). One lock across the check and the create, so two uploads at once cannot both pass."""
        fields, files = parse_multipart(self.headers.get("Content-Type", ""), body)
        with self.state.lock:
            seen = self.state.idempotency.get(key)
            if seen is not None:
                if seen["digest"] != digest:
                    raise Reject(409, "idempotency_key_reused")
                return seen["job_id"], False

            check_pose_set(files)
            person_id = fields.get("person_id")
            person = self.person(person_id)
            consent = self.state.consents.get(fields.get("consent_id"))
            if consent is None or not consent["active"] or consent["person_id"] != person_id:
                raise Reject(422, "no_active_consent")
            reason = fields.get("reason")
            if reason is not None and reason not in REENROL_REASONS:
                raise Reject(422, "invalid_reason")
            if person["enrolment_status"] == "pending":
                # One open job per person. The same key was answered above, so this is a genuinely new attempt.
                open_job = [job_id for job_id, job in self.state.jobs.items() if job["person_id"] == person_id][-1]
                raise Reject(409, "enrolment_open", job_id=open_job)
            if person["enrolment_status"] == "enrolled" and reason is None:
                raise Reject(422, "reason_required")

            # WHY only names survive this point: the photos themselves are never kept.
            job_id = str(uuid.uuid4())
            self.state.jobs[job_id] = {
                "person_id": person_id, "consent_id": fields.get("consent_id"), "poses": sorted(files), "reason": reason, "previous": person["enrolment_status"],
                "reject": self.state.scenario == "reject", "ready_at": time.monotonic() + CHECK_SECONDS, "finished": False,
            }
            person["enrolment_status"] = "pending"
            self.state.idempotency[key] = {"digest": digest, "job_id": job_id}
            return job_id, True

    def job_status(self, job_id):
        self.session()
        if self.state.scenario == "poll_drop":
            # The job is safe and the network went away after the 202.
            self.close_connection = True
            self.connection.close()
            return
        with self.state.lock:
            job = self.state.jobs.get(job_id)
            if job is None:
                raise Reject(404, "job_not_found")
            if time.monotonic() < job["ready_at"]:
                return self.reply(200, {"status": "pending", "problems": []})
            # Once only: polling a finished job again must not overwrite a newer job's status.
            if not job["finished"]:
                job["finished"] = True
                # A rejected replacement leaves the old enrolment untouched.
                self.person(job["person_id"])["enrolment_status"] = job["previous"] if job["reject"] else "enrolled"
        if job["reject"]:
            # Plain-English problems, as CCTV sends them. A null pose is a problem with the whole set.
            problems = [
                {"pose": job["poses"][0], "problem": "the face is blurred (sharpness 14, below 40)"},
                {"pose": None, "problem": "does not match this person's other photos (0.41 is below 0.5)"},
            ]
            return self.reply(200, {"status": "rejected", "problems": problems})
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
