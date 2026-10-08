package com.bank.app.transfer.adapter.in.web;

import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.adapter.in.web.dto.TransferWebRequest;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.dto.TransferReportTotalsResponse;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportTotalsQuery;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.transfer.application.port.in.GenerateTransferReportWithTotalsQuery;
import com.bank.app.transfer.domain.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice coverage for the transfer web adapter (PIT: the whole controller was
 * uncovered because the MockMvc tests live in the app module). Standalone
 * MockMvc keeps it in-module and fast; validation edge cases stay in the app
 * WebMvc tests. Both split controllers (command + query) are registered so
 * route coverage matches the former fat controller.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class TransferControllersTest {

    @Mock private PlaceTransferUseCase placeTransferUseCase;
    @Mock private CancelTransferUseCase cancelTransferUseCase;
    @Mock private GetTransferDetailQuery getTransferDetailQuery;
    @Mock private GetTransferHistoryQuery getTransferHistoryQuery;
    @Mock private GenerateTransferReportQuery generateTransferReportQuery;
    @Mock private GenerateTransferReportTotalsQuery generateTransferReportTotalsQuery;
    @Mock private GenerateTransferReportWithTotalsQuery generateTransferReportWithTotalsQuery;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new TransferCommandController(placeTransferUseCase, cancelTransferUseCase),
                new TransferQueryController(getTransferDetailQuery,
                        getTransferHistoryQuery, generateTransferReportQuery,
                        generateTransferReportTotalsQuery, generateTransferReportWithTotalsQuery)).build();
    }

    @Test
    void shouldCreateTransferWithLocation() throws Exception {
        TransferResponse response = new TransferResponse(10L, TransferStatus.COMPLETED,
                new BigDecimal("200.00"), "TRY", LocalDateTime.of(2026, 9, 1, 12, 0),
                "TR770006200000000000000111", "TR870006200000000000000222", 1L, 2L);
        when(placeTransferUseCase.execute(any(TransferRequest.class))).thenReturn(response);

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferWebRequest(
                                "TR770006200000000000000111", "TR870006200000000000000222",
                                new BigDecimal("200.00"), Currency.TRY))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/transfers/10"))
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void shouldCancelTransfer() throws Exception {
        mockMvc.perform(post("/transfers/42/cancel"))
                .andExpect(status().isNoContent());

        verify(cancelTransferUseCase).execute(42L);
    }

    @Test
    void shouldReturnTransferDetail() throws Exception {
        when(getTransferDetailQuery.execute(7L)).thenReturn(new TransferDetailResponse(
                7L, 1L, 2L, new BigDecimal("50.00"), "TRY", TransferStatus.COMPLETED,
                LocalDateTime.of(2026, 9, 2, 10, 0)));

        mockMvc.perform(get("/transfers/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(50.00));
    }

    @Test
    void shouldReturnHistoryPage() throws Exception {
        TransferResponse item = new TransferResponse(10L, TransferStatus.COMPLETED,
                new BigDecimal("10.00"), "TRY", LocalDateTime.of(2026, 9, 1, 12, 0),
                "TR770006200000000000000111", "TR870006200000000000000222", 1L, 2L);
        when(getTransferHistoryQuery.execute(1L, 0, 20))
                .thenReturn(PageResponse.of(List.of(item), 0, 20, 1L));

        mockMvc.perform(get("/transfers/history/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10));
    }

    @Test
    void shouldReturnReportWithEtagAndHonorIfNoneMatch() throws Exception {
        TransferReportResponse report = new TransferReportResponse(1L, 0, BigDecimal.ZERO,
                "TRY", List.of(), false, null, null, null, null);
        when(generateTransferReportQuery.execute(any(ReportCriteria.class))).thenReturn(report);

        String etag = mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void shouldAcceptKeysetCursorOnReport() throws Exception {
        TransferReportResponse report = new TransferReportResponse(1L, 0, BigDecimal.ZERO,
                "TRY", List.of(), true, "2026-09-01T01:00:00", 11L, null, null);
        when(generateTransferReportQuery.execute(any(ReportCriteria.class))).thenReturn(report);

        mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .param("cursorCreatedAt", "2026-09-01T00:00:00")
                        .param("cursorId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void shouldRejectPartialCursorOnReport() {
        // A single cursor half must fail fast (ReportCriteria requires both or
        // neither). Kills the NegateConditionals mutants on the
        // (cursorCreatedAt != null || cursorId != null) branch: an || -> &&
        // mutant would silently fall back to the offset path and return 200.
        assertThatThrownBy(() -> mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .param("cursorCreatedAt", "2026-09-01T00:00:00"))
                .andExpect(status().isOk()))
                .hasStackTraceContaining("cursorCreatedAt and cursorId must both be set");

        assertThatThrownBy(() -> mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .param("cursorId", "10"))
                .andExpect(status().isOk()))
                .hasStackTraceContaining("cursorCreatedAt and cursorId must both be set");
    }

    @Test
    void shouldRejectPartialCursorOnCombinedReport() {
        assertThatThrownBy(() -> mockMvc.perform(get("/transfers/report/combined")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .param("cursorCreatedAt", "2026-09-01T00:00:00"))
                .andExpect(status().isOk()))
                .hasStackTraceContaining("cursorCreatedAt and cursorId must both be set");

        assertThatThrownBy(() -> mockMvc.perform(get("/transfers/report/combined")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .param("cursorId", "10"))
                .andExpect(status().isOk()))
                .hasStackTraceContaining("cursorCreatedAt and cursorId must both be set");
    }

    @Test
    void shouldReturnOkWhenEtagDoesNotMatch() throws Exception {
        TransferReportResponse report = new TransferReportResponse(1L, 0, BigDecimal.ZERO,
                "TRY", List.of(), false, null, null, null, null);
        when(generateTransferReportQuery.execute(any(ReportCriteria.class))).thenReturn(report);

        mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .header("If-None-Match", "\"stale-etag\""))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void shouldAcceptKeysetCursorOnCombinedReport() throws Exception {
        TransferReportResponse combined = new TransferReportResponse(1L, 2,
                new BigDecimal("300.00"), "TRY", List.of(), true, "2026-09-01T01:00:00", 11L, 2L,
                new BigDecimal("300.00"));
        when(generateTransferReportWithTotalsQuery.execute(any(ReportCriteria.class)))
                .thenReturn(combined);

        mockMvc.perform(get("/transfers/report/combined")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .param("cursorCreatedAt", "2026-09-01T00:00:00")
                        .param("cursorId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true));

        var criteriaCaptor = ArgumentCaptor.forClass(ReportCriteria.class);
        verify(generateTransferReportWithTotalsQuery).execute(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue().page()).isZero();
    }

    @Test
    void shouldReturnReportTotals() throws Exception {
        when(generateTransferReportTotalsQuery.execute(any(ReportCriteria.class)))
                .thenReturn(new TransferReportTotalsResponse(1L, 5L, new BigDecimal("500.00"), "TRY"));

        mockMvc.perform(get("/transfers/report/totals")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransferCount").value(5));
    }

    @Test
    void shouldReturnCombinedReportWithTotals() throws Exception {
        TransferReportResponse combined = new TransferReportResponse(1L, 2,
                new BigDecimal("300.00"), "TRY", List.of(), false, null, null, 2L,
                new BigDecimal("300.00"));
        when(generateTransferReportWithTotalsQuery.execute(any(ReportCriteria.class)))
                .thenReturn(combined);

        mockMvc.perform(get("/transfers/report/combined")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalVolume").value(300.00));
    }

    @Test
    void shouldUseCombinedQueryWhenIncludeTotalsTrue() throws Exception {
        TransferReportResponse combined = new TransferReportResponse(1L, 2,
                new BigDecimal("300.00"), "TRY", List.of(), false, null, null, 2L,
                new BigDecimal("300.00"));
        when(generateTransferReportWithTotalsQuery.execute(any(ReportCriteria.class)))
                .thenReturn(combined);

        mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .param("includeTotals", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));

        verify(generateTransferReportWithTotalsQuery).execute(any(ReportCriteria.class));
        verify(generateTransferReportQuery, org.mockito.Mockito.never())
                .execute(any(ReportCriteria.class));
    }

    @Test
    void shouldHonorWildcardIfNoneMatch() throws Exception {
        TransferReportResponse report = new TransferReportResponse(1L, 0, BigDecimal.ZERO,
                "TRY", List.of(), false, null, null, null, null);
        when(generateTransferReportQuery.execute(any(ReportCriteria.class))).thenReturn(report);

        mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .header("If-None-Match", "*"))
                .andExpect(status().isNotModified())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void shouldChangeEtagWhenItemContentDiffersButCountsMatch() throws Exception {
        // Same account/count/volume/currency/cursor, different transfer IDs:
        // the old header-only ETag would emit a false 304 (stale content).
        TransferResponse first = new TransferResponse(10L, TransferStatus.COMPLETED,
                new BigDecimal("10.00"), "TRY", LocalDateTime.of(2026, 9, 1, 12, 0),
                "TR770006200000000000000111", "TR870006200000000000000222", 1L, 2L);
        TransferResponse second = new TransferResponse(11L, TransferStatus.COMPLETED,
                new BigDecimal("10.00"), "TRY", LocalDateTime.of(2026, 9, 1, 12, 0),
                "TR770006200000000000000111", "TR870006200000000000000222", 1L, 2L);
        TransferReportResponse pageOne = new TransferReportResponse(1L, 1, new BigDecimal("10.00"),
                "TRY", List.of(first), false, null, null, null, null);
        TransferReportResponse pageTwo = new TransferReportResponse(1L, 1, new BigDecimal("10.00"),
                "TRY", List.of(second), false, null, null, null, null);
        when(generateTransferReportQuery.execute(any(ReportCriteria.class)))
                .thenReturn(pageOne, pageTwo);

        String etagOne = mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        String etagTwo = mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        assertThat(etagOne).isNotNull();
        assertThat(etagTwo).isNotNull().isNotEqualTo(etagOne);
    }

    @Test
    void shouldChangeEtagWhenTotalsMoveButPageIsIdentical() throws Exception {
        // includeTotals/combined responses: same page, different whole-range
        // aggregates must not share a validator.
        TransferReportResponse withoutTotalsShift = new TransferReportResponse(1L, 1,
                new BigDecimal("10.00"), "TRY", List.of(), false, null, null, 1L,
                new BigDecimal("10.00"));
        TransferReportResponse withTotalsShift = new TransferReportResponse(1L, 1,
                new BigDecimal("10.00"), "TRY", List.of(), false, null, null, 2L,
                new BigDecimal("20.00"));
        when(generateTransferReportWithTotalsQuery.execute(any(ReportCriteria.class)))
                .thenReturn(withoutTotalsShift, withTotalsShift);

        String etagOne = mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .param("includeTotals", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        String etagTwo = mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00")
                        .param("includeTotals", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        assertThat(etagOne).isNotNull();
        assertThat(etagTwo).isNotNull().isNotEqualTo(etagOne);
    }

    @Test
    void shouldProduceStableEtagAcrossVolumeScaleVariants() throws Exception {
        TransferReportResponse scaleOne = new TransferReportResponse(1L, 1, new BigDecimal("10.0"),
                "TRY", List.of(), false, null, null, null, null);
        TransferReportResponse scaleTwo = new TransferReportResponse(1L, 1, new BigDecimal("10.00"),
                "TRY", List.of(), false, null, null, null, null);
        when(generateTransferReportQuery.execute(any(ReportCriteria.class)))
                .thenReturn(scaleOne, scaleTwo);

        String first = mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        String second = mockMvc.perform(get("/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2026-09-01T00:00:00")
                        .param("endDate", "2026-09-02T00:00:00"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        assertThat(first).isNotNull().matches("W/\"report-[0-9a-f]{16}\"");
        assertThat(second).isEqualTo(first);
    }
}
