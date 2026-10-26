package com.bank.app.account.application.usecase;

import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.common.domain.exception.InvalidIbanException;
import com.bank.app.account.application.dto.AccountInfo;
import com.bank.app.account.application.port.in.GetAccountInfoQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.common.domain.Iban;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class GetAccountInfoQueryImplTest {

    @Mock
    private LoadAccountPort loadAccountPort;

    private GetAccountInfoQuery getAccountInfoQuery;

    @BeforeEach
    void setUp() {
        getAccountInfoQuery = new GetAccountInfoQueryImpl(loadAccountPort);
    }

    private AccountInfo info(Long id, Long userId, String currency, String status) {
        return new AccountInfo(id, userId, currency, status);
    }

    @Test
    void shouldGetAccountInfoSuccessfully() {
        when(loadAccountPort.findInfoById(1L)).thenReturn(Optional.of(info(1L, 100L, "TRY", "ACTIVE")));

        AccountInfo info = getAccountInfoQuery.getAccountInfo(1L);

        assertEquals(1L, info.id());
        assertEquals(100L, info.userId());
        assertEquals("TRY", info.currency());
        assertEquals("ACTIVE", info.status());
        verify(loadAccountPort, never()).findById(any());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenAccountNotFound() {
        when(loadAccountPort.findInfoById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> getAccountInfoQuery.getAccountInfo(99L));
    }

    @Test
    void shouldGetAccountInfoForTransferByIbanSuccessfully() {
        Iban iban = new Iban("TR770006200000000000000111");
        when(loadAccountPort.findInfoByIban(iban)).thenReturn(Optional.of(info(1L, 100L, "TRY", "ACTIVE")));

        AccountInfo info = getAccountInfoQuery.getAccountInfoForTransfer("TR770006200000000000000111");

        assertEquals(1L, info.id());
        assertEquals(100L, info.userId());
        assertEquals("TRY", info.currency());
        assertEquals("ACTIVE", info.status());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenIbanNotFound() {
        Iban iban = new Iban("TR600006200000000000000999");
        when(loadAccountPort.findInfoByIban(iban)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> getAccountInfoQuery.getAccountInfoForTransfer("TR600006200000000000000999"));
    }

    @Test
    void shouldThrowAccountNotFoundExceptionForTransferWhenIbanInvalid() {
        assertThrows(InvalidIbanException.class,
                () -> getAccountInfoQuery.getAccountInfoForTransfer("INVALID_IBAN"));
    }

    @Test
    void shouldGetIbansForAccountsSuccessfully() {
        when(loadAccountPort.findIbansByIds(List.of(1L, 2L))).thenReturn(Map.of(
                1L, "TR770006200000000000000111", 2L, "TR870006200000000000000222"));

        Map<Long, String> ibans = getAccountInfoQuery.getIbansForAccounts(List.of(1L, 2L));

        assertEquals(2, ibans.size());
        assertEquals("TR770006200000000000000111", ibans.get(1L));
        assertEquals("TR870006200000000000000222", ibans.get(2L));
        verify(loadAccountPort, never()).findByIds(any());
    }

    @Test
    void shouldReturnEmptyMapWhenAccountIdsIsNull() {
        Map<Long, String> ibans = getAccountInfoQuery.getIbansForAccounts(null);

        assertTrue(ibans.isEmpty());
        verifyNoInteractions(loadAccountPort);
    }

    @Test
    void shouldReturnEmptyMapWhenAccountIdsIsEmpty() {
        Map<Long, String> ibans = getAccountInfoQuery.getIbansForAccounts(Collections.emptyList());

        assertTrue(ibans.isEmpty());
        verifyNoInteractions(loadAccountPort);
    }

    @Test
    void shouldGetAccountInfoWithSuspendedStatus() {
        when(loadAccountPort.findInfoById(1L)).thenReturn(Optional.of(info(1L, 100L, "TRY", "SUSPENDED")));

        AccountInfo info = getAccountInfoQuery.getAccountInfo(1L);

        assertEquals("SUSPENDED", info.status());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenFindByIdsReturnsEmpty() {
        when(loadAccountPort.findInfoById(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> getAccountInfoQuery.getAccountInfo(1L));
    }

    @Test
    void shouldGetAccountInfoForTransferWithSuspendedStatus() {
        Iban iban = new Iban("TR770006200000000000000111");
        when(loadAccountPort.findInfoByIban(iban)).thenReturn(Optional.of(info(1L, 100L, "TRY", "SUSPENDED")));

        AccountInfo info = getAccountInfoQuery.getAccountInfoForTransfer("TR770006200000000000000111");

        assertEquals("SUSPENDED", info.status());
    }

    @Test
    void shouldNormalizeIbanWithSpacesWhenGettingInfoForTransfer() {
        Iban normalizedIban = new Iban("TR770006200000000000000111");
        when(loadAccountPort.findInfoByIban(normalizedIban))
                .thenReturn(Optional.of(info(1L, 100L, "TRY", "ACTIVE")));

        AccountInfo info = getAccountInfoQuery.getAccountInfoForTransfer("TR77 0006 2000 0000 0000 0001 11");

        assertEquals(1L, info.id());
        assertEquals("TRY", info.currency());
    }

    @Test
    void shouldGetIbansForAccountsWithMixedIds() {
        when(loadAccountPort.findIbansByIds(List.of(1L, 99L)))
                .thenReturn(Map.of(1L, "TR770006200000000000000111"));

        Map<Long, String> ibans = getAccountInfoQuery.getIbansForAccounts(List.of(1L, 99L));

        assertEquals(1, ibans.size());
        assertEquals("TR770006200000000000000111", ibans.get(1L));
    }
}
