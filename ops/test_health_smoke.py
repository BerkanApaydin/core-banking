"""Exercise smoke-check outcomes against a local HTTP server, without banking writes."""

import argparse
from contextlib import redirect_stdout
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import threading
import unittest

from ops.health_smoke import MAX_BODY_BYTES, base_url, main, run_checks, timeout_seconds


class HealthSmokeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.routes = {}
        cls.requests = []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                cls.requests.append((self.command, self.path, self.headers.get("Authorization")))
                status, content_type, body, headers = cls.routes.get(
                    self.path, (404, "application/json", b'{"status":404}', {}))
                if status == 0:
                    self.close_connection = True
                    return
                self.send_response(status)
                self.send_header("Content-Type", content_type)
                for key, value in headers.items():
                    self.send_header(key, value)
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                pass

        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)

    def setUp(self):
        self.routes.clear()
        self.requests.clear()
        for path in ("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"):
            self.routes[path] = (200, "application/vnd.spring-boot.actuator.v3+json", b'{"status":"UP"}', {})
        self.routes["/api/v1/accounts"] = (401, "application/problem+json", b'{"status":401}', {})

    def test_success_requires_health_probes_and_anonymous_access_denial(self):
        results = run_checks(self.url, require_probes=True)
        self.assertEqual(4, len(results))
        self.assertTrue(all(result.passed for result in results))
        self.assertTrue(all(method == "GET" and auth is None for method, _, auth in self.requests))

    def test_down_global_health_fails_even_when_probes_are_up(self):
        for status in (200, 503):
            with self.subTest(http_status=status):
                self.routes["/actuator/health"] = (status, "application/json", b'{"status":"DOWN"}', {})
                results = run_checks(self.url, require_probes=True)
                self.assertFalse(results[0].passed)
                self.assertTrue(results[1].passed)
                self.assertTrue(results[2].passed)

    def test_optional_probes_are_not_silently_skipped_when_required(self):
        del self.routes["/actuator/health/readiness"]
        self.assertTrue(all(result.passed for result in run_checks(self.url)))
        self.assertFalse(all(result.passed for result in run_checks(self.url, require_probes=True)))

    def test_redirect_is_rejected_without_following_its_target(self):
        self.routes["/actuator/health"] = (302, "text/html", b"", {"Location": "/login"})
        results = run_checks(self.url)
        self.assertFalse(results[0].passed)
        self.assertNotIn("/login", [path for _, path, _ in self.requests])

    def test_html_malformed_and_oversized_json_cannot_pass_health(self):
        for content_type, body in (("text/html", b"<html>UP</html>"),
                                   ("application/json", b"{"),
                                   ("application/json", b"[]"),
                                   ("application/json", b" " * (MAX_BODY_BYTES + 1))):
            with self.subTest(content_type=content_type, body_length=len(body)):
                self.routes["/actuator/health"] = (200, content_type, body, {})
                self.assertFalse(run_checks(self.url)[0].passed)

    def test_anonymously_readable_account_api_fails(self):
        self.routes["/api/v1/accounts"] = (200, "application/json", b'{"content":[]}', {})
        self.assertFalse(run_checks(self.url)[-1].passed)

    def test_connection_closed_without_response_is_a_failed_check(self):
        self.routes["/actuator/health"] = (0, "", b"", {})
        result = run_checks(self.url)[0]
        self.assertFalse(result.passed)
        self.assertIn("Request/response error", result.detail)

    def test_cli_returns_failure_and_machine_readable_results(self):
        self.routes["/actuator/health"] = (503, "application/json", b'{"status":"DOWN"}', {})
        output = io.StringIO()
        with redirect_stdout(output):
            result = main([self.url, "--json"])
        self.assertEqual(1, result)
        self.assertFalse(json.loads(output.getvalue())["passed"])

    def test_cli_returns_zero_only_for_success(self):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(0, main([self.url, "--require-probes"]))

    def test_rejects_credentials_and_unbounded_timeouts(self):
        for value in ("file:///tmp/health", "http://user:secret@localhost", "http://localhost/?token=secret"):
            with self.subTest(url=value), self.assertRaises(argparse.ArgumentTypeError):
                base_url(value)
        for value in ("0", "-1", "inf", "nan", "text"):
            with self.subTest(timeout=value), self.assertRaises(argparse.ArgumentTypeError):
                timeout_seconds(value)


if __name__ == "__main__":
    unittest.main()
