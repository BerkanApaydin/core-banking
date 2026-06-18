package com.bank.app.audit.infrastructure.persistence;

import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogAdapterTest {

    @Mock private SpringDataAuditLogRepository springDataRepo;

    private AuditLogAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AuditLogAdapter(springDataRepo);
    }

    @Test
    @SuppressWarnings("null")
    void shouldSaveAuditLogSuccessfully() {
        LocalDateTime timestamp = LocalDateTime.now();
        AuditLog domainLog = new AuditLog(null, "user123", AuditAction.TRANSFER_EXECUTED, "Details here", timestamp);
        AuditLogJpaEntity savedEntity = new AuditLogJpaEntity(1L, "user123", AuditAction.TRANSFER_EXECUTED, "Details here", timestamp);

        when(springDataRepo.save(any(AuditLogJpaEntity.class))).thenReturn(savedEntity);

        AuditLog result = adapter.save(domainLog);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("user123", result.getUsername());
        assertEquals(AuditAction.TRANSFER_EXECUTED, result.getAction());
        assertEquals("Details here", result.getDetails());
        assertEquals(timestamp, result.getTimestamp());

        ArgumentCaptor<AuditLogJpaEntity> captor = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
        verify(springDataRepo).save(captor.capture());

        AuditLogJpaEntity captured = captor.getValue();
        assertEquals("user123", captured.getUsername());
        assertEquals(AuditAction.TRANSFER_EXECUTED, captured.getAction());
        assertEquals("Details here", captured.getDetails());
        assertEquals(timestamp, captured.getTimestamp());
    }
}
