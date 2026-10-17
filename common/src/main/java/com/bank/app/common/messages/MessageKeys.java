package com.bank.app.common.messages;

/**
 * Single source of truth for i18n problem-message keys (the {@code error.*}
 * entries in {@code messages.properties} / {@code messages_tr.properties}).
 *
 * <p>Raw {@code "error.*"} string literals across bounded contexts were a
 * shotgun-surgery hotspot: renaming a key required touching every throw site
 * and handler with no compiler help. All production code must reference these
 * constants instead of literals; {@code MessageKeysContractTest} (app module)
 * fails the build when a constant no longer resolves in either bundle.
 *
 * <p>Placement is deliberate: a dependency-free shared-kernel package, so web
 * adapters can use it without violating
 * {@code controllersShouldNotContainBusinessLogic} (which bans
 * {@code ..domain.exception..} in {@code ..adapter.in.web..}), and domain
 * classes can use it without violating domain purity (no Spring, no
 * infrastructure, no adapters).
 *
 * <p>Test fixtures intentionally keep their own synthetic keys
 * ({@code error.test}, {@code error.unknown_key}, ...) to exercise the
 * unknown-key fallback path — those MUST NOT be added here.
 *
 * <p>NOTE: the string values below are the canonical literals and must stay
 * literals — do NOT rewrite them to {@code MessageKeys.X} self-references
 * (that would null every constant via self-assignment).
 */
public final class MessageKeys {

    private MessageKeys() {
    }

    // NOTE: literal values are intentional (see class Javadoc).
    public static final String ACCESS_DENIED = "error.access_denied";
    public static final String ACCOUNT_BALANCE_LIMIT_EXCEEDED = "error.account_balance_limit_exceeded";
    public static final String ACCOUNT_CLOSE_BALANCE_NOT_ZERO = "error.account_close_balance_not_zero";
    public static final String ACCOUNT_CLOSED = "error.account_closed";
    public static final String ACCOUNT_NOT_ACTIVE = "error.account_not_active";
    public static final String ACCOUNT_NOT_FOUND_IBAN = "error.account_not_found_iban";
    public static final String ACCOUNT_NOT_FOUND_ID = "error.account_not_found_id";
    public static final String ADMIN_REQUIRED = "error.admin_required";
    public static final String AUTHENTICATION_FAILED = "error.authentication_failed";
    public static final String CONCURRENT_REQUEST = "error.concurrent_request";
    public static final String DB_INTEGRITY_VIOLATION = "error.db_integrity_violation";
    public static final String DEMO_FUNDING_DISABLED = "error.demo_funding_disabled";
    public static final String DISTINCT_ACCOUNTS_REQUIRED = "error.distinct_accounts_required";
    public static final String DUPLICATE_IBAN = "error.duplicate_iban";
    public static final String GENERAL_INTERNAL_ERROR = "error.general_internal_error";
    public static final String IDEMPOTENCY_KEY_REQUIRED = "error.idempotency_key_required";
    public static final String IDEMPOTENCY_PAYLOAD_CONFLICT = "error.idempotency_payload_conflict";
    public static final String INSUFFICIENT_BALANCE = "error.insufficient_balance";
    public static final String INVALID_AMOUNT = "error.invalid_amount";
    public static final String INVALID_ARGUMENT = "error.invalid_argument";
    public static final String INVALID_CURRENCY = "error.invalid_currency";
    public static final String INVALID_ENUM_VALUE = "error.invalid_enum_value";
    public static final String INVALID_FORMAT = "error.invalid_format";
    public static final String LOGIN_REQUIRED = "error.login_required";
    public static final String METHOD_NOT_ALLOWED = "error.method_not_allowed";
    public static final String MFA_ALREADY_ENABLED = "error.mfa_already_enabled";
    public static final String MFA_CODE_INVALID = "error.mfa_code_invalid";
    public static final String MFA_NOT_ENROLLED = "error.mfa_not_enrolled";
    public static final String NOT_RESOURCE_OWNER = "error.not_resource_owner";
    public static final String OPTIMISTIC_LOCK_CONFLICT = "error.optimistic_lock_conflict";
    public static final String PASSWORD_RESET_TOKEN_INVALID = "error.password_reset_token_invalid";
    public static final String RATE_LIMIT_EXCEEDED = "error.rate_limit_exceeded";
    public static final String REFRESH_TOKEN_REUSE_DETECTED = "error.refresh_token_reuse_detected";
    public static final String REQUEST_BODY_TOO_LARGE = "error.request_body_too_large";
    public static final String RESOURCE_NOT_FOUND = "error.resource_not_found";
    public static final String SAME_ACCOUNT_TRANSFER = "error.same_account_transfer";
    public static final String SECURITY_BACKEND_UNAVAILABLE = "error.security_backend_unavailable";
    public static final String SESSION_NOT_FOUND = "error.session_not_found";
    public static final String TOO_MANY_FAILED_LOGIN_ATTEMPTS = "error.too_many_failed_login_attempts";
    public static final String TRANSFER_ALREADY_CANCELLED = "error.transfer_already_cancelled";
    public static final String TRANSFER_CANCELLATION_WINDOW_EXPIRED = "error.transfer_cancellation_window_expired";
    public static final String TRANSFER_NOT_CANCELLABLE = "error.transfer_not_cancellable";
    public static final String TRANSFER_NOT_FOUND = "error.transfer_not_found";
    public static final String TRANSFER_NOT_PENDING = "error.transfer_not_pending";
    public static final String UNAUTHORIZED = "error.unauthorized";
    public static final String UNIQUE_CONSTRAINT_VIOLATION = "error.unique_constraint_violation";
    public static final String UNSUPPORTED_MEDIA_TYPE = "error.unsupported_media_type";
    public static final String USERNAME_TAKEN = "error.username_taken";
    public static final String WEAK_PASSWORD = "error.weak_password";
}
