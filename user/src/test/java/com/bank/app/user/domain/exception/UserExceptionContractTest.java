package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessFailureKind;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Failure-kind contract for user domain exceptions.
 *
 * <p>PIT reported each override as NO_COVERAGE: no test ever reads the
 * failure kind or wire code, so the routing contract is unpinned.
 */
class UserExceptionContractTest {

    @Test
    void usernameTakenShouldExposeConflict() {
        var ex = new UsernameAlreadyTakenException("alice");
        assertThat(ex.getFailureKind()).isEqualTo(BusinessFailureKind.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("USERNAME_TAKEN");
    }

    @Test
    void weakPasswordShouldExposeRuleViolation() {
        var ex = new WeakPasswordException(List.of("too short", "no digit"));
        assertThat(ex.getFailureKind()).isEqualTo(BusinessFailureKind.RULE_VIOLATION);
        assertThat(ex.getErrorCode()).isEqualTo("WEAK_PASSWORD");
    }

    @Test
    void refreshReuseShouldExposeAuthenticationFailed() {
        var ex = new RefreshTokenReuseException();
        assertThat(ex.getFailureKind()).isEqualTo(BusinessFailureKind.AUTHENTICATION_FAILED);
        assertThat(ex.getErrorCode()).isEqualTo("REFRESH_TOKEN_REUSE_DETECTED");
    }

    @Test
    void userNotFoundShouldExposeErrorCode() {
        var ex = new UserNotFoundException("alice");
        assertThat(ex.getFailureKind()).isEqualTo(BusinessFailureKind.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("USER_NOT_FOUND");
    }
}
