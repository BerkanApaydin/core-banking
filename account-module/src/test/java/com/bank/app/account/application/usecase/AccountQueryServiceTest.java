package com.bank.app.account.application.usecase;

import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountQueryPort;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountQueryServiceTest {

    @Mock
    private LoadAccountPort loadAccountPort;

    private AccountQueryPort accountQueryService;

    @BeforeEach
    void setUp() {
        accountQueryService = new AccountQueryService(loadAccountPort);
    }

    private Account account(Long id, Long userId, String iban, BigDecimal balance, AccountStatus status) {
        return new Account(id, userId, new Iban(iban), "Owner" + id, Money.of(balance, Currency.TRY), status);
    }

    @Test
    void shouldGetAccountInfoSuccessfully() {
        Account account = account(1L, 100L, "TR290006200000000000000111", new BigDecimal("500.00"), AccountStatus.ACTIVE);
        when(loadAccountPort.findById(1L)).thenReturn(Optional.of(account));

        AccountInfo info = accountQueryService.getAccountInfo(1L);

        assertEquals(1L, info.id());
        assertEquals(100L, info.userId());
        assertEquals("TRY", info.currency());
        assertEquals(AccountStatus.ACTIVE, info.status());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenAccountNotFound() {
        when(loadAccountPort.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountQueryService.getAccountInfo(99L));
    }

    @Test
    void shouldGetAccountInfoForTransferByIbanSuccessfully() {
        Account account = account(1L, 100L, "TR290006200000000000000111", new BigDecimal("500.00"), AccountStatus.ACTIVE);
        Iban iban = new Iban("TR290006200000000000000111");
        when(loadAccountPort.findByIban(iban)).thenReturn(Optional.of(account));

        AccountInfo info = accountQueryService.getAccountInfoForTransfer("TR290006200000000000000111");

        assertEquals(1L, info.id());
        assertEquals(100L, info.userId());
        assertEquals("TRY", info.currency());
        assertEquals(AccountStatus.ACTIVE, info.status());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenIbanNotFound() {
        Iban iban = new Iban("TR290006200000000000000999");
        when(loadAccountPort.findByIban(iban)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> accountQueryService.getAccountInfoForTransfer("TR290006200000000000000999"));
    }

    @Test
    void shouldThrowAccountNotFoundExceptionForTransferWhenIbanInvalid() {
        assertThrows(com.bank.app.common.exception.InvalidIbanException.class,
                () -> accountQueryService.getAccountInfoForTransfer("INVALID_IBAN"));
    }

    @Test
    void shouldGetIbansForAccountsSuccessfully() {
        Account account1 = account(1L, 100L, "TR290006200000000000000111", new BigDecimal("500.00"), AccountStatus.ACTIVE);
        Account account2 = account(2L, 100L, "TR290006200000000000000222", new BigDecimal("300.00"), AccountStatus.ACTIVE);

        when(loadAccountPort.findByIds(List.of(1L, 2L))).thenReturn(List.of(account1, account2));

        Map<Long, String> ibans = accountQueryService.getIbansForAccounts(List.of(1L, 2L));

        assertEquals(2, ibans.size());
        assertEquals("TR290006200000000000000111", ibans.get(1L));
        assertEquals("TR290006200000000000000222", ibans.get(2L));
    }

    @Test
    void shouldReturnEmptyMapWhenAccountIdsIsNull() {
        Map<Long, String> ibans = accountQueryService.getIbansForAccounts(null);

        assertTrue(ibans.isEmpty());
        verifyNoInteractions(loadAccountPort);
    }

    @Test
    void shouldReturnEmptyMapWhenAccountIdsIsEmpty() {
        Map<Long, String> ibans = accountQueryService.getIbansForAccounts(Collections.emptyList());

        assertTrue(ibans.isEmpty());
        verifyNoInteractions(loadAccountPort);
    }

    @Test
    void shouldGetAccountInfoWithSuspendedStatus() {
        Account account = account(1L, 100L, "TR290006200000000000000111", new BigDecimal("500.00"), AccountStatus.SUSPENDED);
        when(loadAccountPort.findById(1L)).thenReturn(Optional.of(account));

        AccountInfo info = accountQueryService.getAccountInfo(1L);

        assertEquals(AccountStatus.SUSPENDED, info.status());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenFindByIdsReturnsEmpty() {
        when(loadAccountPort.findById(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountQueryService.getAccountInfo(1L));
    }

    @Test
    void shouldGetAccountInfoForTransferWithSuspendedStatus() {
        Account account = account(1L, 100L, "TR290006200000000000000111", new BigDecimal("500.00"), AccountStatus.SUSPENDED);
        Iban iban = new Iban("TR290006200000000000000111");
        when(loadAccountPort.findByIban(iban)).thenReturn(Optional.of(account));

        AccountInfo info = accountQueryService.getAccountInfoForTransfer("TR290006200000000000000111");

        assertEquals(AccountStatus.SUSPENDED, info.status());
    }

    @Test
    void shouldNormalizeIbanWithSpacesWhenGettingInfoForTransfer() {
        Account account = account(1L, 100L, "TR290006200000000000000111", new BigDecimal("500.00"), AccountStatus.ACTIVE);
        Iban normalizedIban = new Iban("TR290006200000000000000111");
        when(loadAccountPort.findByIban(normalizedIban)).thenReturn(Optional.of(account));

        AccountInfo info = accountQueryService.getAccountInfoForTransfer("TR29 0006 2000 0000 0000 0001 11");

        assertEquals(1L, info.id());
        assertEquals("TRY", info.currency());
    }

    @Test
    void shouldGetIbansForAccountsWithMixedIdsAndFilterNulls() {
        Account account1 = account(1L, 100L, "TR290006200000000000000111", new BigDecimal("500.00"), AccountStatus.ACTIVE);

        when(loadAccountPort.findByIds(List.of(1L, 99L))).thenReturn(List.of(account1));

        Map<Long, String> ibans = accountQueryService.getIbansForAccounts(List.of(1L, 99L));

        assertEquals(1, ibans.size());
        assertEquals("TR290006200000000000000111", ibans.get(1L));
    }
}
