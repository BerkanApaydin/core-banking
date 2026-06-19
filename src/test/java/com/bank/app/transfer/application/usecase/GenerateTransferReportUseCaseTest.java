package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.transfer.application.port.AccountOperationsPort.AccountInfo;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.common.security.SecurityContextAdapter;
import com.bank.app.common.security.port.SecurityContextPort;
import com.bank.app.common.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateTransferReportUseCaseTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private AccountOperationsPort accountOperationsPort;
    private SecurityContextPort securityContextPort;
    private GenerateTransferReportUseCase generateTransferReportUseCase;

    @BeforeEach
    void setUp() {
        securityContextPort = new SecurityContextAdapter();
        generateTransferReportUseCase = new GenerateTransferReportUseCase(loadTransferPort, accountOperationsPort,
                securityContextPort);

        // Set default authenticated user context using CustomUserDetails
        CustomUserDetails principal = new CustomUserDetails(100L, "test_user", "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null,
                Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGenerateReportSuccessfully() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(info);
        when(accountOperationsPort.getIbansForAccounts(anySet())).thenReturn(Map.of(
                1L, "TR290006200000000000000111",
                2L, "TR290006200000000000000222",
                3L, "TR290006200000000000000333"));

        Transfer t1 = new Transfer(10L, 1L, 2L, Money.of("100.00", Money.Currency.TRY), TransferStatus.COMPLETED,
                start.plusDays(1));
        Transfer t2 = new Transfer(11L, 1L, 3L, Money.of("250.00", Money.Currency.TRY), TransferStatus.COMPLETED,
                start.plusDays(2));

        List<Transfer> transfers = Arrays.asList(t1, t2);

        when(loadTransferPort.findHistoryBetween(1L, start, end, 0, 100)).thenReturn(transfers);

        TransferReportResponse response = generateTransferReportUseCase.execute(criteria);

        assertNotNull(response);
        assertEquals(1L, response.accountId());
        assertEquals(2, response.totalTransfersCount());
        assertEquals(new BigDecimal("350.00"), response.totalVolume());
        assertEquals("TRY", response.currency());
        assertEquals(2, response.transfers().size());
        assertEquals(10L, response.transfers().get(0).id());
        assertEquals(11L, response.transfers().get(1).id());
    }

    @Test
    void shouldGenerateEmptyReportWhenNoTransfersFound() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(info);
        when(accountOperationsPort.getIbansForAccounts(anySet())).thenReturn(Map.of());

        when(loadTransferPort.findHistoryBetween(1L, start, end, 0, 100))
                .thenReturn(Collections.emptyList());

        TransferReportResponse response = generateTransferReportUseCase.execute(criteria);

        assertNotNull(response);
        assertEquals(1L, response.accountId());
        assertEquals(0, response.totalTransfersCount());
        assertEquals(BigDecimal.ZERO, response.totalVolume());
        assertEquals("TRY", response.currency());
        assertTrue(response.transfers().isEmpty());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNotOwnerOfAccount() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(info);

        // Set up authentication for user ID 999 (not owner of sender account 100)
        CustomUserDetails principal = new CustomUserDetails(999L, "other_user", "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null,
                Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("Bu hesabın raporunu oluşturma yetkiniz yok.", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenStartDateIsAfterEndDate() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("Başlangıç tarihi bitiş tarihinden sonra olamaz.", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDateRangeExceeds12Months() {
        LocalDateTime start = LocalDateTime.now().minusMonths(13);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("Rapor aralığı en fazla 12 ay olabilir.", exception.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenCriteriaIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> generateTransferReportUseCase.execute(null));
        assertEquals("Criteria null olamaz", exception.getMessage());
    }
}
