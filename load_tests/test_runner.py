"""Offline contract and acceptance checks for the load-test runner."""

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
    # The real load-test container installs requests. These offline tests mock
    # HTTP calls and never issue requests to a banking deployment.
    stub = types.ModuleType("requests")
    stub.exceptions = types.SimpleNamespace(Timeout=TimeoutError, ConnectionError=ConnectionError)
    sys.modules["requests"] = stub

from load_tests import runner


class LoadRunnerTest(unittest.TestCase):
    def test_account_bootstrap_uses_current_request_fields_and_server_iban(self):
        response = types.SimpleNamespace(
            status_code=201, json=lambda: {"id": 7, "iban": "TR-server-generated"})
        with patch.object(runner.requests, "post", return_value=response, create=True) as post:
            account = runner._create_account("token", "Load User", 50000.0)
        self.assertEqual("TR-server-generated", account["iban"])
        self.assertEqual(
            {"ownerName": "Load User", "initialBalance": 50000.0, "currency": "TRY"},
            post.call_args.kwargs["json"])
        self.assertIn("Idempotency-Key", post.call_args.kwargs["headers"])
        self.assertNotIn("X-Forwarded-For", post.call_args.kwargs["headers"])

    def test_workload_account_create_uses_same_contract(self):
        user = {"token": "token"}
        response = types.SimpleNamespace(status_code=201)
        create_roll = (runner.THRESHOLDS[8] + runner.THRESHOLDS[9]) / 2
        with patch.object(runner.random, "random", return_value=create_roll), \
                patch.object(runner.requests, "request", return_value=response, create=True) as request:
            status, _, endpoint = runner.make_worker([user])(3)
        self.assertEqual((201, "POST /accounts"), (status, endpoint))
        self.assertEqual(
            {"ownerName": "Load Test User 3", "initialBalance": 100.0, "currency": "TRY"},
            request.call_args.kwargs["json"])
        self.assertIn("Idempotency-Key", request.call_args.kwargs["headers"])
        self.assertNotIn("X-Forwarded-For", request.call_args.kwargs["headers"])

    def test_workload_requires_simulated_funding_before_account_writes(self):
        for enabled in (False, True):
            response = types.SimpleNamespace(
                status_code=200, json=lambda: {"initialFundingEnabled": enabled})
            with self.subTest(enabled=enabled), \
                    patch.object(runner.requests, "get", return_value=response, create=True):
                if enabled:
                    runner._require_workload_target("token")
                else:
                    with self.assertRaisesRegex(RuntimeError, "simulated opening balances"):
                        runner._require_workload_target("token")

    def test_rate_limit_mode_does_not_start_workload(self):
        with patch.object(runner, "check_rate_limiting", return_value=True) as rate_test, \
                patch.object(runner, "bootstrap_users") as bootstrap:
            self.assertEqual(0, runner.main(["--mode", "rate-limit"]))
        rate_test.assert_called_once_with()
        bootstrap.assert_not_called()

    def test_failed_consistency_aborts_before_load_phases(self):
        user = {"token": "token"}
        with patch.object(runner, "NUM_LOAD_USERS", 1), \
                patch.object(runner, "user_pool", [user]), \
                patch.object(runner, "bootstrap_users"), \
                patch.object(runner, "bootstrap_context"), \
                patch.object(runner, "check_concurrency_and_idempotency", return_value=False), \
                patch.object(runner, "run_load_phases") as load, \
                patch.object(runner, "find_max_throughput_under_latency") as capacity, \
                contextlib.redirect_stdout(io.StringIO()), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, runner.main(["--mode", "workload"]))
        load.assert_not_called()
        capacity.assert_not_called()

    def _run_capacity(self, outcome):
        output = io.StringIO()
        with patch.object(runner, "CAPACITY_TEST_CONCURRENCY_STEPS", [1]), \
                patch.object(runner, "make_worker", return_value=outcome), \
                patch.object(runner.time, "sleep"), \
                contextlib.redirect_stdout(output):
            passed = runner.find_max_throughput_under_latency([{"token": "test"}], 200)
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
        self.assertFalse(runner._is_success("POST /transfers", 200))
        self.assertTrue(runner._is_success("POST /transfers", 201))
        self.assertTrue(runner._is_success("POST /transfers/{id}/cancel", 204))
        self.assertFalse(runner._is_success("GET /accounts", 400))

    def test_account_bootstrap_reads_page_content(self):
        response = types.SimpleNamespace(json=lambda: {"content": [{"id": 7}]})
        self.assertEqual([{"id": 7}], runner._page_items(response))

    def test_missing_transfer_fixture_is_not_relabelled_as_successful_get(self):
        user = {"token": "test", "account_ids": [1], "account_ibans": ["only-one"],
                "transfer_ids": [], "cancellable_transfer_ids": [],
                "transfer_ids_lock": threading.Lock()}
        with patch.object(runner.random, "random", return_value=runner.THRESHOLDS[7] + 0.001):
            status, _, endpoint = runner.make_worker([user])(0)
        self.assertEqual(("SKIP", "POST /transfers"), (status, endpoint))

    def _transfer_user(self, ibans, cancellable=()):
        return {"token": "token", "account_ids": [1, 2], "account_ibans": list(ibans),
                "transfer_ids": [], "cancellable_transfer_ids": list(cancellable),
                "transfer_ids_lock": threading.Lock()}

    def test_transfer_worker_sends_idempotency_key(self):
        # Backend rejects transfer placement without Idempotency-Key (409);
        # the load worker must always send one.
        user = self._transfer_user(["TR1", "TR2"])
        response = types.SimpleNamespace(status_code=201, json=lambda: {"id": 9})
        transfer_roll = (runner.THRESHOLDS[7] + runner.THRESHOLDS[8]) / 2
        with patch.object(runner.random, "random", return_value=transfer_roll), \
                patch.object(runner.requests, "request", return_value=response, create=True) as request:
            status, _, endpoint = runner.make_worker([user])(0)
        self.assertEqual((201, "POST /transfers"), (status, endpoint))
        body = request.call_args.kwargs["json"]
        self.assertEqual("TR1", body["senderIban"])
        self.assertEqual("TR2", body["receiverIban"])
        self.assertEqual("TRY", body["currency"])
        self.assertGreater(body["amount"], 0)
        headers = request.call_args.kwargs["headers"]
        self.assertIn("Idempotency-Key", headers)
        self.assertTrue(headers["Authorization"].startswith("Bearer "))
        self.assertEqual([9], user["transfer_ids"])
        self.assertEqual([9], user["cancellable_transfer_ids"])

    def test_transfer_worker_skips_without_two_ibans(self):
        user = self._transfer_user(["only-one"])
        transfer_roll = (runner.THRESHOLDS[7] + runner.THRESHOLDS[8]) / 2
        with patch.object(runner.random, "random", return_value=transfer_roll), \
                patch.object(runner.requests, "request", create=True) as request:
            status, _, endpoint = runner.make_worker([user])(0)
        self.assertEqual(("SKIP", "POST /transfers"), (status, endpoint))
        request.assert_not_called()

    def test_cancel_worker_sends_idempotency_key(self):
        # Backend rejects cancellation without Idempotency-Key (409); the load
        # worker must always send one, and a 204 consumes the cancellable id.
        user = self._transfer_user(["TR1", "TR2"], cancellable=[5])
        response = types.SimpleNamespace(status_code=204)
        cancel_roll = (runner.THRESHOLDS[9] + runner.THRESHOLDS[10]) / 2
        with patch.object(runner.random, "random", return_value=cancel_roll), \
                patch.object(runner.requests, "request", return_value=response, create=True) as request:
            status, _, endpoint = runner.make_worker([user])(0)
        self.assertEqual((204, "POST /transfers/{id}/cancel"), (status, endpoint))
        self.assertTrue(request.call_args.args[1].endswith("/api/v1/transfers/5/cancel"))
        self.assertIn("Idempotency-Key", request.call_args.kwargs["headers"])
        self.assertEqual([], user["cancellable_transfer_ids"])

    def test_cancel_worker_skips_without_cancellable_transfer(self):
        user = self._transfer_user(["TR1", "TR2"])
        cancel_roll = (runner.THRESHOLDS[9] + runner.THRESHOLDS[10]) / 2
        with patch.object(runner.random, "random", return_value=cancel_roll), \
                patch.object(runner.requests, "request", create=True) as request:
            status, _, endpoint = runner.make_worker([user])(0)
        self.assertEqual(("SKIP", "POST /transfers/{id}/cancel"), (status, endpoint))
        request.assert_not_called()


if __name__ == "__main__":
    unittest.main()
