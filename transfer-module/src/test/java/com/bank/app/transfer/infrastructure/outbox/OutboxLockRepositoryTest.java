package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.common.outbox.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxLockRepositoryTest {

    @Mock
    private EntityManager entityManager;

    private OutboxLockRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OutboxLockRepository();
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
    }

    @Test
    void shouldUseSkipLockedQueryWithoutPartition() {
        ReflectionTestUtils.setField(repository, "useSkipLocked", true);

        Query nativeQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString(), eq(OutboxEventJpaEntity.class)))
                .thenReturn(nativeQuery);
        when(nativeQuery.setParameter("limit", 10)).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, -1);

        assertTrue(result.isEmpty());
        verify(entityManager).createNativeQuery(contains("FOR UPDATE SKIP LOCKED"), eq(OutboxEventJpaEntity.class));
        verify(nativeQuery).setParameter("limit", 10);
        verify(nativeQuery, never()).setParameter(eq("partition"), anyInt());
    }

    @Test
    void shouldUseSkipLockedQueryWithPartition() {
        ReflectionTestUtils.setField(repository, "useSkipLocked", true);

        Query nativeQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString(), eq(OutboxEventJpaEntity.class)))
                .thenReturn(nativeQuery);
        when(nativeQuery.setParameter("limit", 5)).thenReturn(nativeQuery);
        when(nativeQuery.setParameter("partition", 2)).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(5, 2);

        assertTrue(result.isEmpty());
        verify(entityManager).createNativeQuery(contains("AND partition = :partition"), eq(OutboxEventJpaEntity.class));
        verify(nativeQuery).setParameter("limit", 5);
        verify(nativeQuery).setParameter("partition", 2);
    }

    @Test
    void shouldUsePessimisticWriteQueryWithoutPartition() {
        ReflectionTestUtils.setField(repository, "useSkipLocked", false);

        TypedQuery<OutboxEventJpaEntity> typedQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(OutboxEventJpaEntity.class)))
                .thenReturn(typedQuery);
        when(typedQuery.setMaxResults(10)).thenReturn(typedQuery);
        when(typedQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of());

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, -1);

        assertTrue(result.isEmpty());
        verify(entityManager).createQuery(argThat((String q) -> !q.contains("partition")), eq(OutboxEventJpaEntity.class));
        verify(typedQuery).setMaxResults(10);
        verify(typedQuery).setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void shouldUsePessimisticWriteQueryWithPartition() {
        ReflectionTestUtils.setField(repository, "useSkipLocked", false);

        TypedQuery<OutboxEventJpaEntity> typedQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(OutboxEventJpaEntity.class)))
                .thenReturn(typedQuery);
        when(typedQuery.setMaxResults(3)).thenReturn(typedQuery);
        when(typedQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(typedQuery);
        when(typedQuery.setParameter("partition", 1)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of());

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(3, 1);

        assertTrue(result.isEmpty());
        verify(entityManager).createQuery(contains("AND e.partition = :partition"), eq(OutboxEventJpaEntity.class));
        verify(typedQuery).setParameter("partition", 1);
        verify(typedQuery).setMaxResults(3);
        verify(typedQuery).setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }

}
