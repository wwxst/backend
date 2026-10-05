"""Run the backend gate against a new MySQL database and a private Redis process."""

import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import json
import math
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener
import uuid
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "target" / "quality-gate"
NO_WINDOW = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def free_port():
    with socket.socket() as connection:
        connection.bind(("127.0.0.1", 0))
        return connection.getsockname()[1]


def redis_command(port, *arguments):
    # RESP uses a fresh connection so concurrent callers do not share socket state.
    parts = [str(value).encode() for value in arguments]
    payload = f"*{len(parts)}\r\n".encode()
    payload += b"".join(f"${len(part)}\r\n".encode() + part + b"\r\n" for part in parts)
    with socket.create_connection(("127.0.0.1", port), timeout=3) as connection:
        connection.sendall(payload)
        with connection.makefile("rb") as stream:
            header = stream.readline()
            require(bool(header), "Redis returned an empty protocol response")
            kind, value = header[:1], header[1:-2]
            if kind == b"-":
                raise RuntimeError("Redis command failed")
            if kind == b"$":
                length = int(value)
                if length == -1:
                    return None
                body = stream.read(length)
                require(len(body) == length and stream.read(2) == b"\r\n", "Incomplete Redis response")
                return body.decode()
            if kind == b":":
                return int(value)
            require(kind == b"+", "Unsupported Redis response type")
            return value.decode()


class BackendGate:
    def __init__(self, args, workspace):
        self.args = args
        self.workspace = Path(workspace)
        self.database = "web_project_quality_" + uuid.uuid4().hex[:12]
        self.redis_port = free_port()
        self.http_port = free_port()
        while self.http_port == self.redis_port:
            self.http_port = free_port()
        self.coverage_port = free_port()
        while self.coverage_port in (self.redis_port, self.http_port):
            self.coverage_port = free_port()
        self.env = os.environ.copy()
        self.env.update(JAVA_HOME=str(args.java_home), DB_URL=self.jdbc_url,
                        ATLAS_MYSQL_TEST_URL=self.jdbc_url, REDIS_HOST="127.0.0.1",
                        REDIS_PORT=str(self.redis_port), REDIS_DATABASE="0", REDIS_PASSWORD="",
                        JWT_SECRET=base64.b64encode(secrets.token_bytes(32)).decode())
        self.env["MYSQL_PWD"] = self.env.get("DB_PASSWORD", "123456")
        self.redis = None
        self.app = None
        self.created_database = False
        self.sensitive = [self.env["JWT_SECRET"]]
        self.report = {"scenarios": [], "pressure": [], "performance_scope": "local baseline, no production SLA",
                       "verification_scope": "automated verification; standards checks and reviews recorded separately"}
        self.http = build_opener(ProxyHandler({}))

    @property
    def jdbc_url(self):
        return (f"jdbc:mysql://{self.args.db_host}:{self.args.db_port}/{self.database}"
                "?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true")

    def sql(self, statement, select_database=True):
        command = [str(self.args.mysql_client), "--host=" + self.args.db_host,
                   "--port=" + str(self.args.db_port), "--user=" + self.env.get("DB_USERNAME", "root"),
                   "--default-character-set=utf8mb4", "--batch", "--skip-column-names"]
        if select_database:
            command += ["--database=" + self.database]
        result = subprocess.run(command, input=statement, text=True, encoding="utf-8",
                                capture_output=True, env=self.env, timeout=20, creationflags=NO_WINDOW)
        require(result.returncode == 0, "MySQL operation failed; credentials and SQL details withheld")
        return result.stdout.strip()

    def start_redis(self):
        with (self.workspace / "redis.log").open("ab") as log:
            self.redis = subprocess.Popen(
                [str(self.args.redis_server), "--bind", "127.0.0.1", "--port", str(self.redis_port),
                 "--save", "", "--appendonly", "no", "--dir", str(self.workspace)],
                stdout=log, stderr=subprocess.STDOUT, creationflags=NO_WINDOW)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            require(self.redis.poll() is None, "Private Redis process exited during startup")
            try:
                if redis_command(self.redis_port, "PING") == "PONG":
                    return
            except OSError:
                pass  # Readiness polling only; test operations do not hide connection failures.
            time.sleep(0.1)
        raise TimeoutError("Private Redis did not become ready")

    def maven(self, *goals, log_name):
        log_path = self.workspace / log_name
        with log_path.open("w", encoding="utf-8") as log:
            result = subprocess.run([str(ROOT / "mvnw.cmd"), *goals], cwd=ROOT, env=self.env,
                                    stdout=log, stderr=subprocess.STDOUT, timeout=240, creationflags=NO_WINDOW)
        require(result.returncode == 0, f"Maven failed; inspect {log_path}")

    def request(self, method, path, payload=None, token=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = Request(f"http://127.0.0.1:{self.http_port}" + path,
                          data=None if payload is None else json.dumps(payload).encode(),
                          headers=headers, method=method)
        started = time.perf_counter()
        try:
            with self.http.open(request, timeout=10) as response:
                status, body = response.status, response.read()
        except HTTPError as response:
            status, body = response.code, response.read()
        return status, json.loads(body), (time.perf_counter() - started) * 1000

    def check(self, name, method, path, payload=None, token=None, status=200, code=200):
        actual_status, body, _ = self.request(method, path, payload, token)
        require((actual_status, body.get("code")) == (status, code),
                f"{name}: unexpected HTTP/business status {actual_status}/{body.get('code')}")
        self.report["scenarios"].append({"name": name, "passed": True})
        data = body.get("data")
        if isinstance(data, dict):
            self.sensitive.extend(data[key] for key in ("accessToken", "refreshToken") if key in data)
        return data

    def issue_code(self, target, scene, token=None, target_type="EMAIL"):
        self.check("send code " + scene, "POST", "/api/user/auth/send-code",
                   {"target": target, "targetType": target_type, "scene": scene}, token)
        key = f"auth:code:{scene.lower()}:{target_type.lower()}:{target}"
        code = redis_command(self.redis_port, "GET", key)
        require(code is not None, "Sent code was not stored")
        self.sensitive.extend([target, code])
        return code

    def login(self, target, password, target_type="EMAIL"):
        pair = self.check("password login", "POST", "/api/user/auth/login",
                          {"target": target, "targetType": target_type, "password": password})
        self.sensitive.extend(pair.values())
        return pair

    def start_app(self):
        agent = (Path.home() / ".m2/repository/org/jacoco/org.jacoco.agent/0.8.15/"
                 "org.jacoco.agent-0.8.15-runtime.jar")
        require(agent.is_file(), "JaCoCo agent was not resolved by Maven")
        command = [str(self.args.java_home / "bin/java.exe"),
                   f"-javaagent:{agent}=output=tcpserver,address=127.0.0.1,port={self.coverage_port}",
                   "-jar", str(ROOT / "target/backend-0.0.1-SNAPSHOT.jar"),
                   f"--server.port={self.http_port}", "--server.address=127.0.0.1",
                   "--spring.data.redis.timeout=1s", "--spring.data.redis.connect-timeout=1s"]
        with (self.workspace / "application.log").open("wb") as log:
            self.app = subprocess.Popen(command, cwd=ROOT, env=self.env, stdout=log,
                                        stderr=subprocess.STDOUT, creationflags=NO_WINDOW)
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            require(self.app.poll() is None, "Application exited during startup; inspect application.log")
            try:
                if self.request("GET", "/api/sys-user/auth/me")[0] == 401:
                    return
            except (URLError, OSError):
                pass
            time.sleep(0.2)
        raise TimeoutError("Application did not become ready")

    def smoke(self):
        password = "Quality-" + secrets.token_hex(8)
        self.sensitive.append(password)
        self.password = password
        sys_pair = self.check("system login", "POST", "/api/sys-user/auth/login",
                              {"username": "admin", "password": "123456"})
        self.sensitive.extend(sys_pair.values())
        self.sys_token = sys_pair["accessToken"]
        self.check("system profile", "GET", "/api/sys-user/auth/me", token=self.sys_token)
        self.check("wrong system password", "POST", "/api/sys-user/auth/login",
                   {"username": "admin", "password": "wrong"}, status=401, code=40101)
        self.check("unknown system account", "POST", "/api/sys-user/auth/login",
                   {"username": "missing", "password": "wrong"}, status=401, code=40101)
        for path in ("sys-users", "users", "auth-channels"):
            self.check("system " + path, "GET", "/api/sys-user/" + path, token=self.sys_token)
        self.check("system refresh", "POST", "/api/sys-user/auth/refresh",
                   {"refreshToken": sys_pair["refreshToken"]})
        self.check("refresh cannot access resources", "GET", "/api/sys-user/auth/me",
                   token=sys_pair["refreshToken"], status=401, code=40102)
        self.check("access cannot refresh", "POST", "/api/sys-user/auth/refresh",
                   {"refreshToken": self.sys_token}, status=401, code=40104)
        self.check("empty paging", "GET", "/api/sys-user/users?page=", token=self.sys_token,
                   status=400, code=40000)
        for channel in ("phone", "email"):
            self.check("channel update", "PUT", "/api/sys-user/auth-channels/" + channel,
                       {"enabled": True, "codeEnabled": True}, self.sys_token)
        self.channel_checks()
        self.sql("UPDATE sys_user SET status=0 WHERE username='admin'")
        try:
            self.check("disabled system profile", "GET", "/api/sys-user/auth/me", token=self.sys_token,
                       status=403, code=40302)
            self.check("disabled system login", "POST", "/api/sys-user/auth/login",
                       {"username": "admin", "password": "123456"}, status=403, code=40302)
            self.check("disabled system refresh", "POST", "/api/sys-user/auth/refresh",
                       {"refreshToken": sys_pair["refreshToken"]}, status=401, code=40104)
        finally:
            self.sql("UPDATE sys_user SET status=1 WHERE username='admin'")
        self.check("system logout", "POST", "/api/sys-user/auth/logout", {}, self.sys_token)
        self.check("idempotent system logout", "POST", "/api/sys-user/auth/logout", {}, self.sys_token)
        self.check("system logout blocks refresh", "POST", "/api/sys-user/auth/refresh",
                   {"refreshToken": sys_pair["refreshToken"]}, status=401, code=40104)
        self.sql("INSERT INTO sys_user (username,password,nickname) "
                 "SELECT 'quality-system',password,'quality' FROM sys_user WHERE username='admin'")
        deleted_sys_pair = self.check("temporary system login", "POST", "/api/sys-user/auth/login",
                                      {"username": "quality-system", "password": "123456"})
        self.sql("DELETE FROM sys_user WHERE username='quality-system'")
        self.check("deleted system profile", "GET", "/api/sys-user/auth/me",
                   token=deleted_sys_pair["accessToken"], status=401, code=40102)
        self.check("deleted system refresh", "POST", "/api/sys-user/auth/refresh",
                   {"refreshToken": deleted_sys_pair["refreshToken"]}, status=401, code=40104)

        self.email = "quality-" + uuid.uuid4().hex + "@example.com"
        code = self.issue_code(self.email, "REGISTER")
        pair = self.check("email registration", "POST", "/api/user/auth/register",
                          {"target": self.email, "targetType": "EMAIL", "code": code, "password": password})
        self.sensitive.extend(pair.values())
        token = pair["accessToken"]
        self.check("user profile", "GET", "/api/user/me", token=token)
        self.list_checks()
        self.admin_user_edit_checks(token, sys_pair["refreshToken"])
        self.admin_user_status_checks(pair, sys_pair["refreshToken"])
        self.check("role isolation", "GET", "/api/sys-user/users", token=token, status=403, code=40301)
        self.check("profile update", "PUT", "/api/user/me", {"nickname": "quality", "avatar": None}, token)
        self.check("idempotent profile", "PUT", "/api/user/me", {"nickname": "quality", "avatar": None}, token)
        self.login(self.email, password)
        self.check("wrong password", "POST", "/api/user/auth/login",
                   {"target": self.email, "targetType": "EMAIL", "password": "wrong"}, status=401, code=40101)
        self.check("bcrypt byte boundary", "POST", "/api/user/auth/login",
                   {"target": self.email, "targetType": "EMAIL", "password": "中" * 25}, status=400, code=40000)
        changed_password = "Changed-" + secrets.token_hex(8)
        self.sensitive.append(changed_password)
        code = self.issue_code(self.email, "RESET_PASSWORD")
        self.check("change password", "PUT", "/api/user/me/password",
                   {"target": self.email, "targetType": "EMAIL", "code": code, "newPassword": changed_password}, token)
        self.check("old refresh after change", "POST", "/api/user/auth/refresh",
                   {"refreshToken": pair["refreshToken"]}, status=401, code=40104)
        self.check("old password rejected after change", "POST", "/api/user/auth/login",
                   {"target": self.email, "targetType": "EMAIL", "password": password}, status=401, code=40101)
        changed_pair = self.login(self.email, changed_password)
        # Reset the test-only send lock to verify another password flow without a one-minute sleep.
        redis_command(self.redis_port, "DEL", f"auth:code:lock:reset_password:email:{self.email}")
        code = self.issue_code(self.email, "RESET_PASSWORD")
        self.check("anonymous reset", "POST", "/api/user/auth/reset-password",
                   {"target": self.email, "targetType": "EMAIL", "code": code, "newPassword": password})
        self.check("old refresh after reset", "POST", "/api/user/auth/refresh",
                   {"refreshToken": changed_pair["refreshToken"]}, status=401, code=40104)
        self.check("old password rejected after reset", "POST", "/api/user/auth/login",
                   {"target": self.email, "targetType": "EMAIL", "password": changed_password}, status=401, code=40101)
        pair = self.login(self.email, password)
        self.user_pair = pair
        token = pair["accessToken"]

        phone = "139" + str(secrets.randbelow(100000000)).zfill(8)
        old_code = self.issue_code(self.email, "REBIND_OLD", token)
        new_code = self.issue_code(phone, "REBIND_NEW", token, "PHONE")
        self.check("phone rebind", "PUT", "/api/user/me/phone",
                   {"oldTarget": self.email, "oldTargetType": "EMAIL", "oldCode": old_code,
                    "newPhone": phone, "newCode": new_code}, token)
        new_email = "rebind-" + uuid.uuid4().hex + "@example.com"
        old_code = self.issue_code(phone, "REBIND_OLD", token, "PHONE")
        new_code = self.issue_code(new_email, "REBIND_NEW", token)
        self.check("email rebind", "PUT", "/api/user/me/email",
                   {"oldTarget": phone, "oldTargetType": "PHONE", "oldCode": old_code,
                    "newEmail": new_email, "newCode": new_code}, token)
        self.email = new_email
        self.user_pair = self.login(self.email, password)
        no_password_email = "no-password-" + uuid.uuid4().hex + "@example.com"
        code = self.issue_code(no_password_email, "REGISTER")
        no_password_pair = self.check("passwordless registration", "POST", "/api/user/auth/register",
                                      {"target": no_password_email, "targetType": "EMAIL", "code": code})
        self.sensitive.extend(no_password_pair.values())
        self.check("passwordless password rejection", "POST", "/api/user/auth/login",
                   {"target": no_password_email, "targetType": "EMAIL", "password": password},
                   status=401, code=40101)
        self.account_state_checks(no_password_email, no_password_pair)
        phone = "138" + str(secrets.randbelow(100000000)).zfill(8)
        code = self.issue_code(phone, "REGISTER", target_type="PHONE")
        phone_pair = self.check("phone registration", "POST", "/api/user/auth/register",
                                {"target": phone, "targetType": "PHONE", "code": code, "password": password})
        profile = self.check("phone profile", "GET", "/api/user/me", token=phone_pair["accessToken"])
        require(profile["phone"] == phone and profile["email"] is None, "Phone registration mapped wrong contact")
        self.check("logout", "POST", "/api/user/auth/logout", {}, no_password_pair["accessToken"])
        self.check("idempotent logout", "POST", "/api/user/auth/logout", {}, no_password_pair["accessToken"])
        self.check("logout blocks refresh", "POST", "/api/user/auth/refresh",
                   {"refreshToken": no_password_pair["refreshToken"]}, status=401, code=40104)
        self.check("access remains valid until expiry", "GET", "/api/user/me",
                   token=no_password_pair["accessToken"])
        self.sql("DELETE FROM user_account WHERE id=" + str(int(profile["id"])))
        self.check("deleted user profile", "GET", "/api/user/me", token=phone_pair["accessToken"],
                   status=401, code=40102)
        self.check("deleted user refresh", "POST", "/api/user/auth/refresh",
                   {"refreshToken": phone_pair["refreshToken"]}, status=401, code=40104)

    def admin_user_status_checks(self, pair, admin_refresh):
        profile = self.check("status target profile", "GET", "/api/user/me", token=pair["accessToken"])
        user_id = int(profile["id"])
        path = f"/api/sys-user/users/{user_id}/status"
        protected_query = ("SELECT user_no,phone,email,nickname,password,password_version,avatar,created_at "
                           f"FROM user_account WHERE id={user_id}")
        before = self.sql(protected_query)
        for name, token, status, code in (
                ("anonymous cannot change status", None, 401, 40102),
                ("user cannot change status", pair["accessToken"], 403, 40301),
                ("refresh cannot change status", admin_refresh, 401, 40102)):
            self.check(name, "PATCH", path, {"status": 0}, token, status=status, code=code)
        for value in (None, -1, 2):
            self.check("invalid user status", "PATCH", path, {"status": value}, self.sys_token,
                       status=400, code=40000)
        self.check("missing user status target", "PATCH", "/api/sys-user/users/9223372036854775807/status",
                   {"status": 0}, self.sys_token, status=400, code=40000)
        self.check("admin disables user", "PATCH", path, {"status": 0}, self.sys_token)
        self.check("disabled profile after admin action", "GET", "/api/user/me", token=pair["accessToken"],
                   status=403, code=40303)
        self.check("disabled refresh after admin action", "POST", "/api/user/auth/refresh",
                   {"refreshToken": pair["refreshToken"]}, status=401, code=40104)
        self.check("disabled password login after admin action", "POST", "/api/user/auth/login",
                   {"target": self.email, "targetType": "EMAIL", "password": self.password},
                   status=403, code=40303)
        for user_status in (0, 1):
            barrier = threading.Barrier(8)

            def change_status(_):
                barrier.wait(timeout=10)
                return self.request("PATCH", path, {"status": user_status}, self.sys_token)

            with ThreadPoolExecutor(max_workers=8) as executor:
                results = list(executor.map(change_status, range(8)))
            require(all((status, body["code"]) == (200, 200) for status, body, _ in results),
                    "Concurrent repeated status writes failed")
            require(self.sql(f"SELECT status FROM user_account WHERE id={user_id}") == str(user_status),
                    "Status writes saved an incorrect state")
            require(self.sql(protected_query) == before, "Status writes changed other account fields")
            listed = self.check("status filter matches saved state", "GET",
                                f"/api/sys-user/users?keyword={profile['userNo']}&status={user_status}",
                                token=self.sys_token)
            require(any(row["id"] == user_id and row["status"] == user_status for row in listed["records"]),
                    "Status change missing from filtered list")
            self.report["scenarios"].append({"name": f"8 concurrent status={user_status} writes", "passed": True})
        self.check("enabled profile restored", "GET", "/api/user/me", token=pair["accessToken"])
        self.login(self.email, self.password)

    def admin_user_edit_checks(self, user_token, refresh_token):
        suffix = uuid.uuid4().hex[:12]
        first_email = "edit-first-" + suffix + "@example.com"
        second_email = "edit-second-" + suffix + "@example.com"
        new_email = "edited-" + suffix + "@example.com"
        race_email = "edit-race-" + suffix + "@example.com"
        phone = "139" + str(secrets.randbelow(100000000)).zfill(8)
        self.sensitive.extend([first_email, second_email, new_email, race_email, phone])
        self.sql("INSERT INTO user_account (user_no,email,password,password_version,nickname,avatar,status) "
                 "SELECT 'edit-first-" + suffix + "','" + first_email + "',password,7,'before','/avatar.png',0 "
                 "FROM user_account WHERE email='" + self.email + "'")
        self.sql("INSERT INTO user_account (user_no,email,nickname) "
                 "VALUES ('edit-second-" + suffix + "','" + second_email + "','second')")
        first_id = int(self.sql("SELECT id FROM user_account WHERE email='" + first_email + "'"))
        second_id = int(self.sql("SELECT id FROM user_account WHERE email='" + second_email + "'"))
        path = "/api/sys-user/users/" + str(first_id)
        protected_query = ("SELECT user_no,password,password_version,avatar,status,created_at FROM user_account "
                           "WHERE id=" + str(first_id))
        protected_before = self.sql(protected_query)
        payload = {"nickname": " edited ", "phone": " ", "email": " " + new_email.upper() + " "}
        self.check("user cannot edit user", "PUT", path, payload, user_token, status=403, code=40301)
        self.check("anonymous cannot edit user", "PUT", path, payload, status=401, code=40102)
        self.check("refresh cannot edit user", "PUT", path, payload, refresh_token, status=401, code=40102)
        for label in ("admin edits disabled user", "repeated admin edit succeeds"):
            edited = self.check(label, "PUT", path, payload, self.sys_token)
            require(edited["id"] == first_id and edited["nickname"] == "edited" and edited["phone"] is None
                    and edited["email"] == new_email and edited["status"] == 0,
                    "Admin edit returned incorrect normalized data")
            require("password" not in edited and "passwordVersion" not in edited, "Admin edit exposed credentials")
        require(self.sql(protected_query) == protected_before, "Admin edit changed protected fields")
        for label, invalid in (
                ("admin edit needs one contact", {"nickname": "edited", "phone": " ", "email": None}),
                ("admin edit needs nickname", {"nickname": " ", "email": new_email}),
                ("admin edit nickname length", {"nickname": "x" * 51, "email": new_email}),
                ("admin edit phone format", {"nickname": "edited", "phone": "123"}),
                ("admin edit email format", {"nickname": "edited", "email": "broken"}),
                ("admin edit email length", {"nickname": "edited", "email": "x" * 100 + "@example.com"})):
            self.check(label, "PUT", path, invalid, self.sys_token, status=400, code=40000)
        self.check("admin edit missing account", "PUT", "/api/sys-user/users/9223372036854775807",
                   payload, self.sys_token, status=400, code=40000)
        self.check("admin edit duplicate email", "PUT", path,
                   {"nickname": "should-not-change", "phone": phone, "email": second_email},
                   self.sys_token, status=400, code=40017)
        require(self.sql("SELECT nickname,email FROM user_account WHERE id=" + str(first_id)) == "edited\t" + new_email,
                "Contact conflict partially changed user")
        self.check("admin can keep only phone", "PUT", path,
                   {"nickname": "edited", "phone": " " + phone + " ", "email": None}, self.sys_token)
        self.check("admin edit duplicate phone", "PUT", "/api/sys-user/users/" + str(second_id),
                   {"nickname": "second", "phone": phone, "email": second_email},
                   self.sys_token, status=400, code=40017)
        barrier = threading.Barrier(2)

        def edit_once(account_id):
            barrier.wait(timeout=10)
            return self.request("PUT", "/api/sys-user/users/" + str(account_id),
                                {"nickname": "race", "phone": None, "email": race_email}, self.sys_token)

        with ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(edit_once, (first_id, second_id)))
        require(sum(status == 200 for status, _, _ in results) == 1, "Concurrent admin edits need one winner")
        require(all((status, body["code"]) in ((200, 200), (400, 40017)) for status, body, _ in results),
                "Concurrent admin edits returned unexpected response")
        require(self.sql("SELECT COUNT(*) FROM user_account WHERE email='" + race_email + "'") == "1",
                "Concurrent admin edits violated contact uniqueness")
        require(self.sql("SELECT COUNT(*) FROM user_account WHERE id IN (" + str(first_id) + ","
                         + str(second_id) + ") AND phone IS NULL AND email IS NULL") == "0",
                "Concurrent admin edits removed all login contacts")
        self.report["scenarios"].append({"name": "concurrent admin contact edits: one winner", "passed": True})

    def channel_checks(self):
        path = "/api/sys-user/auth-channels/"
        self.check("unknown channel", "PUT", path + "invalid", {"enabled": True, "codeEnabled": True},
                   self.sys_token, status=400, code=40000)
        self.check("disable email", "PUT", path + "email", {"enabled": False, "codeEnabled": True}, self.sys_token)
        try:
            self.check("cannot disable both channels", "PUT", path + "phone",
                       {"enabled": False, "codeEnabled": True}, self.sys_token, status=400, code=40018)
            self.check("disabled channel rejects code", "POST", "/api/user/auth/send-code",
                       {"target": "disabled@example.com", "targetType": "EMAIL", "scene": "REGISTER"},
                       status=400, code=40010)
        finally:
            self.check("restore email", "PUT", path + "email", {"enabled": True, "codeEnabled": True}, self.sys_token)
        self.check("disable email codes", "PUT", path + "email",
                   {"enabled": True, "codeEnabled": False}, self.sys_token)
        try:
            self.check("disabled code function", "POST", "/api/user/auth/send-code",
                       {"target": "disabled@example.com", "targetType": "EMAIL", "scene": "REGISTER"},
                       status=400, code=40011)
        finally:
            self.check("restore email codes", "PUT", path + "email",
                       {"enabled": True, "codeEnabled": True}, self.sys_token)

    def list_checks(self):
        for resource in ("users", "sys-users"):
            path = "/api/sys-user/" + resource
            data = self.check("populated " + resource, "GET", path + "?keyword=%20%20&status=1", token=self.sys_token)
            require(data["total"] > 0 and data["records"], "Populated list omitted records")
            require(all("password" not in row and "passwordVersion" not in row for row in data["records"]),
                    "List returned password data")
            data = self.check("unmatched " + resource, "GET", path + "?keyword=does-not-exist&status=0",
                              token=self.sys_token)
            require(data["total"] == 0 and data["records"] == [], "Unmatched search returned records")
            self.check("paging upper limit " + resource, "GET", path + "?pageSize=101", token=self.sys_token,
                       status=400, code=40000)

    def account_state_checks(self, email, pair):
        # This email is generated by the harness, so it contains no SQL metacharacters.
        self.sql("UPDATE user_account SET status=0 WHERE email='" + email + "'")
        try:
            self.check("disabled user profile", "GET", "/api/user/me", token=pair["accessToken"],
                       status=403, code=40303)
            self.check("disabled user refresh", "POST", "/api/user/auth/refresh",
                       {"refreshToken": pair["refreshToken"]}, status=401, code=40104)
            self.check("disabled user login", "POST", "/api/user/auth/login",
                       {"target": email, "targetType": "EMAIL", "code": "123456"}, status=403, code=40303)
        finally:
            self.sql("UPDATE user_account SET status=1 WHERE email='" + email + "'")

    def concurrent_http(self):
        code = self.issue_code(self.email, "LOGIN")
        barrier = threading.Barrier(16)

        def login_once(_):
            barrier.wait(timeout=10)
            return self.request("POST", "/api/user/auth/login",
                                {"target": self.email, "targetType": "EMAIL", "code": code})

        with ThreadPoolExecutor(max_workers=16) as executor:
            results = list(executor.map(login_once, range(16)))
        require(sum(status == 200 for status, _, _ in results) == 1, "Concurrent code login had multiple winners")
        require(all((status, body["code"]) in ((200, 200), (400, 40014)) for status, body, _ in results),
                "Concurrent code login returned an unexpected error")
        self.report["scenarios"].append({"name": "16 concurrent HTTP code logins: one winner", "passed": True})

        target = "send-race-" + uuid.uuid4().hex + "@example.com"
        self.sensitive.append(target)
        barrier = threading.Barrier(16)

        def send_once(_):
            barrier.wait(timeout=10)
            return self.request("POST", "/api/user/auth/send-code",
                                {"target": target, "targetType": "EMAIL", "scene": "REGISTER"})

        with ThreadPoolExecutor(max_workers=16) as executor:
            results = list(executor.map(send_once, range(16)))
        require(sum(status == 200 for status, _, _ in results) == 1, "Concurrent code sends had multiple winners")
        require(all((status, body["code"]) in ((200, 200), (400, 40012)) for status, body, _ in results),
                "Concurrent code sends returned an unexpected error")
        require(0 < redis_command(self.redis_port, "TTL", "auth:code:register:email:" + target) <= 300,
                "Code did not have the expected expiry")
        code = redis_command(self.redis_port, "GET", "auth:code:register:email:" + target)
        self.sensitive.append(code)
        self.report["scenarios"].append({"name": "16 concurrent HTTP code sends: one winner", "passed": True})
        barrier = threading.Barrier(16)

        def register_once(_):
            barrier.wait(timeout=10)
            return self.request("POST", "/api/user/auth/register",
                                {"target": target, "targetType": "EMAIL", "code": code})

        with ThreadPoolExecutor(max_workers=16) as executor:
            results = list(executor.map(register_once, range(16)))
        require(sum(status == 200 for status, _, _ in results) == 1, "Concurrent registration had multiple winners")
        require(all((status, body["code"]) in ((200, 200), (400, 40014), (400, 40015))
                    for status, body, _ in results), "Concurrent registration returned an unexpected error")
        require(self.sql("SELECT COUNT(*) FROM user_account WHERE email='" + target + "'") == "1",
                "Concurrent registration created unexpected rows")
        self.report["scenarios"].append({"name": "16 concurrent HTTP registrations: one account", "passed": True})

        target = "quota-" + uuid.uuid4().hex + "@example.com"
        for index in range(10):
            redis_command(self.redis_port, "DEL", f"auth:code:lock:register:email:{target}")
            self.check("daily quota allowed " + str(index + 1), "POST", "/api/user/auth/send-code",
                       {"target": target, "targetType": "EMAIL", "scene": "REGISTER"})
        redis_command(self.redis_port, "DEL", f"auth:code:lock:register:email:{target}")
        self.check("daily quota rejects eleventh", "POST", "/api/user/auth/send-code",
                   {"target": target, "targetType": "EMAIL", "scene": "REGISTER"}, status=400, code=40013)

    def process_metrics(self):
        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             f"Get-Process -Id {self.app.pid} | Select-Object CPU,WorkingSet64 | ConvertTo-Json -Compress"],
            text=True, capture_output=True, timeout=10, creationflags=NO_WINDOW)
        require(result.returncode == 0, "Cannot collect Java process resource metrics")
        return json.loads(result.stdout)

    def pressure(self):
        # Baseline contract fixed before load: zero unexpected errors and a working recovery probe.
        self.report["baseline_contract"] = {"workers": [1, 8, 24], "seconds_per_stage": self.args.seconds,
                                            "operation_mix": "40% profile, 40% refresh, 20% password login",
                                            "unexpected_error_rate": 0, "recovery_http_status": 200,
                                            "latency_sla": None}
        self.report["warmup_requests"] = 20
        for _ in range(20):
            self.check("load warmup", "GET", "/api/user/me", token=self.user_pair["accessToken"])
        for workers in (1, 8, 24):
            before = self.process_metrics()
            start = time.perf_counter()
            deadline = start + self.args.seconds

            def worker(worker_index):
                timings, errors = [], 0
                operation = worker_index
                while time.perf_counter() < deadline:
                    if operation % 5 < 2:
                        status, body, elapsed = self.request("GET", "/api/user/me",
                                                             token=self.user_pair["accessToken"])
                    elif operation % 5 < 4:
                        status, body, elapsed = self.request("POST", "/api/user/auth/refresh",
                                                             {"refreshToken": self.user_pair["refreshToken"]})
                    else:
                        status, body, elapsed = self.request("POST", "/api/user/auth/login",
                                                             {"target": self.email, "targetType": "EMAIL",
                                                              "password": self.password})
                    errors += (status, body.get("code")) != (200, 200)
                    timings.append(elapsed)
                    operation += 1
                return timings, errors

            with ThreadPoolExecutor(max_workers=workers) as executor:
                results = list(executor.map(worker, range(workers)))
            duration = time.perf_counter() - start
            after = self.process_metrics()
            timings = sorted(value for values, _ in results for value in values)
            errors = sum(value for _, value in results)
            require(bool(timings), "Pressure stage did not send requests")
            info = dict(line.split(":", 1) for line in redis_command(self.redis_port, "INFO", "memory").splitlines()
                        if ":" in line and not line.startswith("#"))
            metric = {"workers": workers, "requests": len(timings), "duration_seconds": round(duration, 2),
                      "requests_per_second": round(len(timings) / duration, 2), "unexpected_errors": errors,
                      "p95_ms": round(timings[math.ceil(len(timings) * .95) - 1], 2),
                      "p99_ms": round(timings[math.ceil(len(timings) * .99) - 1], 2),
                      "java_cpu_seconds": round(after["CPU"] - before["CPU"], 2),
                      "java_working_set_bytes": after["WorkingSet64"],
                      "redis_used_memory_bytes": int(info["used_memory"]),
                      "mysql_threads_connected": int(self.sql("SHOW STATUS LIKE 'Threads_connected'").split()[-1])}
            self.report["pressure"].append(metric)
            print("Pressure baseline:", json.dumps(metric), flush=True)
            require(errors == 0, "Pressure stage had unexpected responses")
            self.check("post-load recovery", "GET", "/api/user/me", token=self.user_pair["accessToken"])

    def fault_checks(self):
        token = self.user_pair["accessToken"]
        self.sql("UPDATE sys_config SET config_value='invalid' WHERE config_key='auth.email.enabled'")
        try:
            self.check("invalid channel is server failure", "GET", "/api/sys-user/auth-channels",
                       token=self.sys_token, status=500, code=50000)
        finally:
            self.sql("UPDATE sys_config SET config_value='true' WHERE config_key='auth.email.enabled'")
        self.redis.terminate()
        self.redis.wait(timeout=10)
        self.redis = None
        self.check("Redis outage rejects refresh", "POST", "/api/user/auth/refresh",
                   {"refreshToken": self.user_pair["refreshToken"]}, status=500, code=50000)
        self.start_redis()
        self.user_pair = self.login(self.email, self.password)
        self.check("Redis recovery", "GET", "/api/user/me", token=token)
        text = (self.workspace / "application.log").read_text(encoding="utf-8", errors="replace")
        require(not any(value in text for value in self.sensitive), "Application log exposed a sensitive value")
        self.report["log_sensitive_scan"] = "passed"
        self.report["intentional_fault_responses"] = 2

    def stop_app(self):
        if self.app is not None and self.app.poll() is None:
            # Dump while the JVM is alive; Windows process termination does not run JVM shutdown hooks.
            self.maven("org.jacoco:jacoco-maven-plugin:0.8.15:dump", "-Djacoco.address=127.0.0.1",
                       f"-Djacoco.port={self.coverage_port}", "-Djacoco.append=true", "-Djacoco.retryCount=0",
                       log_name="coverage-dump.log")
            self.app.terminate()
            self.app.wait(timeout=15)

    def run(self):
        schema = (ROOT / "web_project.sql").read_text(encoding="utf-8")
        self.sql("CREATE DATABASE " + self.database +
                 " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci", select_database=False)
        self.created_database = True
        schema = schema[schema.index("USE web_project;"):]
        schema = schema.replace("USE web_project;", "USE " + self.database + ";")
        self.sql(schema, select_database=False)
        self.start_redis()
        print("Private dependencies ready; starting Maven verification", flush=True)
        self.maven("clean", "verify", log_name="verify.log")
        REPORT_DIR.mkdir(parents=True, exist_ok=True)
        suites = [ET.parse(path).getroot() for path in (ROOT / "target/surefire-reports").glob("TEST-*.xml")]
        self.report["junit"] = {key: sum(int(suite.get(key, 0)) for suite in suites)
                                for key in ("tests", "failures", "errors", "skipped")}
        require(self.report["junit"]["tests"] > 0, "No JUnit test results were generated")
        require(self.report["junit"]["failures"] == 0 and self.report["junit"]["errors"] == 0,
                "Required JUnit tests failed")
        require(self.report["junit"]["skipped"] == 0, "Required integration tests were skipped")
        print("Maven verification:", self.report["junit"], flush=True)
        self.start_app()
        self.smoke()
        self.concurrent_http()
        self.pressure()
        self.fault_checks()
        self.stop_app()
        self.maven("org.jacoco:jacoco-maven-plugin:0.8.15:report", log_name="coverage.log")
        coverage = ET.parse(ROOT / "target/site/jacoco/jacoco.xml").getroot()
        self.report["coverage"] = {counter.get("type"): {"covered": int(counter.get("covered")),
                                                        "missed": int(counter.get("missed"))}
                                   for counter in coverage.findall("counter")}
        self.report["status"] = "passed"

    def cleanup(self):
        if self.app is not None and self.app.poll() is None:
            self.app.terminate()
            self.app.wait(timeout=15)
        if self.redis is not None and self.redis.poll() is None:
            self.redis.terminate()
            self.redis.wait(timeout=10)
        if self.created_database:
            self.sql("DROP DATABASE " + self.database, select_database=False)
        REPORT_DIR.mkdir(parents=True, exist_ok=True)
        for name in ("verify.log", "application.log", "coverage.log", "coverage-dump.log", "redis.log"):
            path = self.workspace / name
            if path.exists():
                (REPORT_DIR / name).write_bytes(path.read_bytes())
        (REPORT_DIR / "results.json").write_text(json.dumps(self.report, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--mysql-client", type=Path, required=True)
    parser.add_argument("--redis-server", type=Path, required=True)
    parser.add_argument("--db-host", default="127.0.0.1")
    parser.add_argument("--db-port", type=int, default=3306)
    parser.add_argument("--seconds", type=int, default=10)
    args = parser.parse_args()
    require(1 <= args.seconds <= 60, "Each local baseline stage must last between 1 and 60 seconds")
    with tempfile.TemporaryDirectory(prefix="atlas-quality-") as workspace:
        gate = BackendGate(args, workspace)
        try:
            gate.run()
        except Exception as exception:
            gate.report["status"] = "failed"
            gate.report["failure_type"] = type(exception).__name__
            raise
        finally:
            gate.cleanup()
    print("Backend automated verification passed; reports:", REPORT_DIR, flush=True)


if __name__ == "__main__":
    main()
