package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessFailureKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Failure-kind contract for account domain exceptions.
 *
 * <p>PIT reported each override as NO_COVERAGE: the routing contract is
 * unpinned without a reader.
 */
class AccountExceptionContractTest {

    @Test
    void duplicateIbanShouldExposeConflict() {
        var ex = new DuplicateIbanException("TR440006200000000000000123");
        assertThat(ex.getFailureKind()).isEqualTo(BusinessFailureKind.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_IBAN");
    }

    @Test
    void balanceLimitShouldExposeErrorCode() {
        var ex = new AccountBalanceLimitExceededException("error.limit", null, "Limit exceeded.");
        assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_BALANCE_LIMIT_EXCEEDED");
    }

    @Test
    void notFoundShouldExposeNotFoundKind() {
        assertThat(new AccountNotFoundException(7L).getFailureKind())
                .isEqualTo(BusinessFailureKind.NOT_FOUND);
        assertThat(new AccountNotFoundException("TR440006200000000000000123").getFailureKind())
                .isEqualTo(BusinessFailureKind.NOT_FOUND);
    }
}
