package com.bank.app.transfer.infrastructure.web;

import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.*;
import com.bank.app.transfer.application.port.in.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransferController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlaceTransferUseCase placeTransferPort;

    @MockitoBean
    private CancelTransferUseCase cancelTransferPort;

    @MockitoBean
    private GetTransferDetailQuery getTransferDetailPort;

    @MockitoBean
    private GetTransferHistoryQuery getTransferHistoryPort;

    @MockitoBean
    private GenerateTransferReportQuery generateTransferReportPort;

    @Test
    void shouldPlaceTransferAndReturn201() throws Exception {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111", "TR290006200000000000000222",
                new BigDecimal("200.00"), Money.Currency.TRY);
        TransferResponse response = new TransferResponse(10L, "COMPLETED", new BigDecimal("200.00"),
                "TRY", LocalDateTime.now(), "TR290006200000000000000111",
                "TR290006200000000000000222", 1L, 2L);

        when(placeTransferPort.execute(any(TransferRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(200.00));
    }

    @Test
    void shouldReturn400WhenTransferRequestIsInvalid() throws Exception {
        TransferRequest request = new TransferRequest("", "", null, null);

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCancelTransferAndReturn204() throws Exception {
        doNothing().when(cancelTransferPort).execute(10L);

        mockMvc.perform(post("/api/v1/transfers/10/cancel"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldGetTransferDetail() throws Exception {
        TransferDetailResponse response = new TransferDetailResponse(
                1L, 10L, 20L, new BigDecimal("150.00"),
                "TRY", "COMPLETED", LocalDateTime.now());

        when(getTransferDetailPort.execute(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/transfers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.amount").value(150.00));
    }

    @Test
    void shouldGetTransferHistory() throws Exception {
        TransferResponse t = new TransferResponse(10L, "COMPLETED", new BigDecimal("200.00"),
                "TRY", LocalDateTime.now(), "TR1", "TR2", 1L, 2L);
        PagedResponse<TransferResponse> paged = new PagedResponse<>(List.of(t), 0, 20, 1);

        when(getTransferHistoryPort.execute(eq(1L), anyInt(), anyInt())).thenReturn(paged);

        mockMvc.perform(get("/api/v1/transfers/history/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10L))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void shouldGetTransferReport() throws Exception {
        TransferReportResponse report = new TransferReportResponse(1L, 2,
                new BigDecimal("350.00"), "TRY", List.of());

        when(generateTransferReportPort.execute(any(ReportCriteria.class))).thenReturn(report);

        mockMvc.perform(get("/api/v1/transfers/report")
                        .param("accountId", "1")
                        .param("startDate", "2025-01-01T00:00:00")
                        .param("endDate", "2025-01-10T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1L))
                .andExpect(jsonPath("$.totalVolume").value(350.00));
    }
}
