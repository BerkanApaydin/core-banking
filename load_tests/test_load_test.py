"""Deterministic checks for the capacity test's acceptance oracle."""

import contextlib
import io
import sys
import threading
import types
import unittest
from unittest.mock import patch

try:
    import requests  # noqa: F401
except ModuleNotFoundError:
    # The real load-test container installs requests. These offline tests only
    # exercise response classification and do not issue HTTP calls.
    stub = types.ModuleType("requests")
    stub.exceptions = types.SimpleNamespace(Timeout=TimeoutError, ConnectionError=ConnectionError)
    sys.modules["requests"] = stub

from load_tests import load_test


class CapacityOracleTest(unittest.TestCase):
    def _run_capacity(self, outcome):
        output = io.StringIO()
        with patch.object(load_test, "CAPACITY_TEST_CONCURRENCY_STEPS", [1]), \
                patch.object(load_test, "make_worker", return_value=outcome), \
                patch.object(load_test.time, "sleep"), \
                contextlib.redirect_stdout(output):
            passed = load_test.find_max_throughput_under_latency([{"token": "test"}], 200)
        return passed, output.getvalue()

    def test_client_errors_are_never_successful_throughput(self):
        passed, output = self._run_capacity(lambda index: (400, 1, "POST /transfers"))
        self.assertFalse(passed)
        self.assertIn("success=0.00%", output)
        self.assertIn("FAIL (Errors)", output)

    def test_tail_latency_fails_when_mean_is_below_target(self):
        passed, output = self._run_capacity(
            lambda index: (200, 500 if index >= 189 else 10, "GET /accounts"))
        self.assertFalse(passed)
        self.assertIn("p95=500ms", output)
        self.assertIn("FAIL (p95 Latency)", output)

    def test_success_requires_expected_status_for_operation(self):
        self.assertFalse(load_test._is_success("POST /transfers", 200))
        self.assertTrue(load_test._is_success("POST /transfers", 201))
        self.assertTrue(load_test._is_success("POST /transfers/{id}/cancel", 204))
        self.assertFalse(load_test._is_success("GET /accounts", 400))

    def test_account_bootstrap_reads_page_content(self):
        response = types.SimpleNamespace(json=lambda: {"content": [{"id": 7}]})
        self.assertEqual([{"id": 7}], load_test._page_items(response))

    def test_missing_transfer_fixture_is_not_relabelled_as_successful_get(self):
        user = {"token": "test", "account_ids": [1], "account_ibans": ["only-one"],
                "transfer_ids": [], "cancellable_transfer_ids": [],
                "transfer_ids_lock": threading.Lock()}
        with patch.object(load_test.random, "random", return_value=load_test.THRESHOLDS[7] + 0.001):
            status, _, endpoint = load_test.make_worker([user])(0)
        self.assertEqual(("SKIP", "POST /transfers"), (status, endpoint))


if __name__ == "__main__":
    unittest.main()
