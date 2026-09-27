# Simulated starting balances

This project moves simulated balances only. No operation represents a real bank transfer or claim on real funds.

Local `dev`, `demo`, `test`, and `testcontainers` profiles permit a positive opening balance when a user creates an account. A hosted deployment should use `SPRING_PROFILES_ACTIVE=prod,simulation`: `prod` keeps its secret, secure-cookie, Redis and database settings while the additional `simulation` profile permits simulated opening balances. `prod` alone, including an accidental `prod,dev` combination, permits only a zero opening balance. The backend enforces this in `CreateAccountUseCaseImpl`; the browser reads `GET /api/v1/accounts/capabilities` to lock or unlock its opening-balance input. Client state is advisory, not an authorization boundary.

The existing create-account limit is 1,000,000,000.00 per account, with rate limiting and idempotency on account creation and an audit event in the same use case. This is a simulation seed, not a reconciled funding ledger. A hosted public demo should separately decide user/account quotas and data retention from expected traffic before exposing unrestricted self-registration.
