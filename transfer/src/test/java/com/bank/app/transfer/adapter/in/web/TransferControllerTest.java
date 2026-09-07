package com.bank.app.transfer.adapter.in.web;

import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.adapter.in.web.dto.TransferWebRequest;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock private PlaceTransferUseCase placeTransferUseCase;
    @Mock private CancelTransferUseCase cancelTransferUseCase;
    @Mock private GetTransferDetailQuery getTransferDetailQuery;
    @Mock private GetTransferHistoryQuery getTransferHistoryQuery;
    @Mock private GenerateTransferReportQuery generateTransferReportQuery;

    private TransferController controller;

    @BeforeEach
    void setUp() {
        controller = new TransferController(placeTransferUseCase, cancelTransferUseCase,
                getTransferDetailQuery, getTransferHistoryQuery, generateTransferReportQuery);
    }

    @Test
    void shouldCreateTransferWith201() {
        var webRequest = new TransferWebRequest("TR111", "TR222", new BigDecimal("100.00"), Currency.TRY);
        var expected = new TransferResponse(1L, TransferStatus.COMPLETED, new BigDecimal("100.00"),
                "TRY", LocalDateTime.now(), "TR111", "TR222", 10L, 20L);
        when(placeTransferUseCase.execute(any(TransferRequest.class))).thenReturn(expected);

        var entity = controller.transfer(webRequest);

        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertSame(expected, entity.getBody());
        verify(placeTransferUseCase).execute(any(TransferRequest.class));
    }

    @Test
    void shouldCancelTransferWith204() {
        var entity = controller.cancel(1L);

        assertEquals(HttpStatus.NO_CONTENT, entity.getStatusCode());
        verify(cancelTransferUseCase).execute(eq(1L));
    }

    @Test
    void shouldReturnDetailWith200() {
        var expected = new TransferDetailResponse(1L, 10L, 20L, new BigDecimal("100.00"),
                "TRY", TransferStatus.COMPLETED, LocalDateTime.now());
        when(getTransferDetailQuery.execute(eq(1L))).thenReturn(expected);

        var entity = controller.getDetail(1L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(expected, entity.getBody());
    }

    @Test
    void shouldReturnHistoryWith200() {
        var expected = PageResponse.<TransferResponse>of(List.of(), 0, 20, 0);
        when(getTransferHistoryQuery.execute(eq(1L), eq(0), eq(20))).thenReturn(expected);

        var entity = controller.getHistory(1L, 0, 20);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(expected, entity.getBody());
    }

    @Test
    void shouldReturnReportWith200() {
        var start = LocalDateTime.now().minusDays(1);
        var end = LocalDateTime.now();
        var expected = new TransferReportResponse(1L, 0, BigDecimal.ZERO, "TRY", List.of());
        when(generateTransferReportQuery.execute(any(ReportCriteria.class))).thenReturn(expected);

        var entity = controller.getReport(1L, start, end, 0, 100);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(expected, entity.getBody());
    }
}
