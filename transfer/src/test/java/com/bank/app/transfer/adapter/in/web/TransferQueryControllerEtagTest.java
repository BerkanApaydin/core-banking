package com.bank.app.transfer.adapter.in.web;

import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportTotalsQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportWithTotalsQuery;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ETag contract of the transfer detail endpoint.
 *
 * <p>The detail validator is a weak ETag over identity + optimistic-lock
 * version. A versionless aggregate (a pending row materialized before
 * persist) falls back to status + createdAt, so the validator is never empty
 * and a persist round-trip still invalidates a stale client copy.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferQueryController detail ETag")
class TransferQueryControllerEtagTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 2, 10, 0);

    @Mock
    private GetTransferDetailQuery getTransferDetailQuery;
    @Mock
    private GetTransferHistoryQuery getTransferHistoryQuery;
    @Mock
    private GenerateTransferReportQuery generateTransferReportQuery;
    @Mock
    private GenerateTransferReportTotalsQuery generateTransferReportTotalsQuery;
    @Mock
    private GenerateTransferReportWithTotalsQuery generateTransferReportWithTotalsQuery;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TransferQueryController(
                getTransferDetailQuery, getTransferHistoryQuery, generateTransferReportQuery,
                generateTransferReportTotalsQuery, generateTransferReportWithTotalsQuery)).build();
    }

    private static TransferDetailResponse detail(Long version, TransferStatus status) {
        return new TransferDetailResponse(7L, 1L, 2L, new BigDecimal("50.00"), "TRY",
                status, CREATED_AT, version);
    }

    @Test
    @DisplayName("should fall back to status and createdAt when the version is null")
    void shouldFallBackToStatusAndCreatedAtWhenVersionIsNull() {
        String etag = TransferQueryController.etagForDetail(detail(null, TransferStatus.COMPLETED));

        assertThat(etag).isEqualTo("W/\"transfer-7-COMPLETED-2026-09-02T10:00\"");
    }

    @Test
    @DisplayName("should never produce an empty validator for a versionless transfer")
    void shouldNeverProduceEmptyValidatorForVersionlessTransfer() {
        String etag = TransferQueryController.etagForDetail(detail(null, TransferStatus.PENDING));

        // An empty validator would let a proxy cache one client's money
        // response under another client's cache key.
        assertThat(etag).isNotBlank();
        assertThat(etag).contains("transfer-7");
    }

    @Test
    @DisplayName("should keep the validator stable when the version does not change")
    void shouldKeepValidatorStableWhenVersionDoesNotChange() {
        String first = TransferQueryController.etagForDetail(detail(5L, TransferStatus.COMPLETED));
        String second = TransferQueryController.etagForDetail(detail(5L, TransferStatus.COMPLETED));

        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo("W/\"transfer-7-5\"");
    }

    @Test
    @DisplayName("should change the validator when the version changes")
    void shouldChangeValidatorWhenVersionChanges() {
        String before = TransferQueryController.etagForDetail(detail(5L, TransferStatus.COMPLETED));
        String after = TransferQueryController.etagForDetail(detail(6L, TransferStatus.COMPLETED));

        // A version bump means the row changed: the stale validator must not
        // answer 304 for the new state.
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("should change the versionless validator when the status changes")
    void shouldChangeVersionlessValidatorWhenStatusChanges() {
        String before = TransferQueryController.etagForDetail(detail(null, TransferStatus.PENDING));
        String after = TransferQueryController.etagForDetail(detail(null, TransferStatus.COMPLETED));

        // The fallback carries the status, so a versionless transfer that
        // changed state still invalidates the client's cached copy.
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("should serve the versionless validator and honour it as If-None-Match")
    void shouldServeAndHonourTheVersionlessValidator() throws Exception {
        when(getTransferDetailQuery.execute(7L)).thenReturn(detail(null, TransferStatus.COMPLETED));

        String etag = mockMvc.perform(get("/transfers/7"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn().getResponse().getHeader("ETag");

        assertThat(etag).isEqualTo("W/\"transfer-7-COMPLETED-2026-09-02T10:00\"");

        mockMvc.perform(get("/transfers/7").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag));
    }
}
