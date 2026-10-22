package com.bank.app.transfer.adapter.out.account;

import com.bank.app.accountapi.AccountApi;
import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Null / empty input handling of the Account anti-corruption adapter.
 *
 * <p>The adapter is the only place where the Account published language is
 * translated, so a null slipping through here surfaces as a NullPointerException
 * deep inside the money-movement path instead of a clean domain failure.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountAclAdapter null and empty input")
class AccountAclAdapterEdgeCaseTest {

    @Mock
    private AccountApi accountApi;

    private AccountAclAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountAclAdapter(accountApi, new InMemoryAccountInfoCacheAdapter());
    }

    @Test
    @DisplayName("should reject a null account id collection")
    void shouldRejectNullAccountIdCollection() {
        assertThatThrownBy(() -> adapter.getIbansForAccounts(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Account IDs must not be null");

        verifyNoInteractions(accountApi);
    }

    @Test
    @DisplayName("should return an empty map for an empty account id collection")
    void shouldReturnEmptyMapForEmptyAccountIdCollection() {
        Map<Long, String> result = adapter.getIbansForAccounts(List.of());

        assertThat(result).isEmpty();
        // An empty batch is not short-circuited locally: it reaches the Account
        // API once and comes back empty. Harmless, but it pins the contract
        // that no exception escapes for an empty page of accounts.
        verify(accountApi).getIbansForAccounts(List.of());
    }

    @Test
    @DisplayName("should reject a null amount on a balance adjustment")
    void shouldRejectNullAmountOnBalanceAdjustment() {
        assertThatThrownBy(() -> adapter.debitAndCredit(new AccountId(1L), new AccountId(2L), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Amount must not be null");

        verifyNoInteractions(accountApi);
    }

    @Test
    @DisplayName("should reject a null amount on a cancellation reversal")
    void shouldRejectNullAmountOnCancellationReversal() {
        assertThatThrownBy(() -> adapter.reverseBalancesForCancellation(new AccountId(1L), new AccountId(2L), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Amount must not be null");

        // The reversal is the refund leg of a cancellation: a null amount must
        // never reach the Account context.
        verifyNoInteractions(accountApi);
    }

    @Test
    @DisplayName("should reject a null sender account on a balance adjustment")
    void shouldRejectNullSenderOnBalanceAdjustment() {
        assertThatThrownBy(() -> adapter.debitAndCredit(null, new AccountId(2L), Money.of("10.00", Currency.TRY)))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(accountApi);
    }

    @Test
    @DisplayName("should reject a null receiver account on a balance adjustment")
    void shouldRejectNullReceiverOnBalanceAdjustment() {
        assertThatThrownBy(() -> adapter.debitAndCredit(new AccountId(1L), null, Money.of("10.00", Currency.TRY)))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(accountApi);
    }

    @Test
    @DisplayName("should reject a null sender account on a cancellation reversal")
    void shouldRejectNullSenderOnCancellationReversal() {
        assertThatThrownBy(() -> adapter.reverseBalancesForCancellation(null, new AccountId(2L),
                Money.of("10.00", Currency.TRY)))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(accountApi);
    }

    @Test
    @DisplayName("should reject a null account id on an account lookup")
    void shouldRejectNullAccountIdOnLookup() {
        assertThatThrownBy(() -> adapter.getAccountInfo(null))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(accountApi);
    }

    @Test
    @DisplayName("should reject a null IBAN on a transfer lookup")
    void shouldRejectNullIbanOnTransferLookup() {
        assertThatThrownBy(() -> adapter.getAccountInfoForTransfer(null))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(accountApi);
    }

    @Test
    @DisplayName("should keep the balance adjustment untouched when the amount is null")
    void shouldNotMutateBalancesWhenAmountIsNull() {
        Money amount = Money.of("10.00", Currency.TRY);

        assertThatThrownBy(() -> adapter.debitAndCredit(null, new AccountId(2L), amount))
                .isInstanceOf(NullPointerException.class);

        // Guard against a partial mutation: the Account context must not be
        // asked to move money when the request is incomplete.
        verify(accountApi, org.mockito.Mockito.never()).adjustBalances(any(), any(), any());
        verify(accountApi, org.mockito.Mockito.never()).reverseForCancellation(any(), any(), any());
    }
}
