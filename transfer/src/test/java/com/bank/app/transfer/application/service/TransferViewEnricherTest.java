package com.bank.app.transfer.application.service;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferViewEnricherTest {

    @Mock
    private AccountAclPort accountAclPort;

    private TransferViewEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new TransferViewEnricher(accountAclPort);
    }

    @Test
    void shouldBatchLoadIbansForAllParticipants() {
        Transfer t1 = new Transfer(10L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, LocalDateTime.now());
        Transfer t2 = new Transfer(11L, 2L, 3L, Money.of("50.00", Currency.TRY),
                TransferStatus.COMPLETED, LocalDateTime.now());
        when(accountAclPort.getIbansForAccounts(Set.of(1L, 2L, 3L)))
                .thenReturn(Map.of(1L, "IBAN-1", 2L, "IBAN-2", 3L, "IBAN-3"));

        List<TransferResponse> responses = enricher.enrich(List.of(t1, t2));

        assertEquals(2, responses.size());
        verify(accountAclPort, times(1)).getIbansForAccounts(any());
    }

    @Test
    void shouldReturnEmptyListWithoutFailing() {
        when(accountAclPort.getIbansForAccounts(any())).thenReturn(Map.of());

        List<TransferResponse> responses = enricher.enrich(List.of());

        assertTrue(responses.isEmpty());
    }

    @Test
    void shouldRejectNullDependencies() {
        assertThrows(NullPointerException.class, () -> new TransferViewEnricher(null));
        assertThrows(NullPointerException.class, () -> enricher.enrich(null));
        assertThrows(NullPointerException.class, () -> enricher.loadIbans(null));
    }
}
