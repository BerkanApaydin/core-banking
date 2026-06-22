package com.bank.app.common.outbox;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxLockRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    @Mock
    private TypedQuery<OutboxEventJpaEntity> typedQuery;

    private OutboxLockRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OutboxLockRepository();
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
        ReflectionTestUtils.setField(repository, "useSkipLocked", true);
    }

    @Test
    void shouldFindAndLockWithSkipLocked() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        when(entityManager.createNativeQuery(anyString(), eq(OutboxEventJpaEntity.class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of(entity));

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, -1);

        assertEquals(1, result.size());
        assertSame(entity, result.getFirst());
        verify(entityManager).createNativeQuery(anyString(), eq(OutboxEventJpaEntity.class));
        verify(nativeQuery).setParameter("limit", 10);
        verify(nativeQuery, never()).setParameter(eq("partition"), anyInt());
    }

    @Test
    void shouldFindAndLockWithSkipLockedAndPartition() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        when(entityManager.createNativeQuery(anyString(), eq(OutboxEventJpaEntity.class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of(entity));

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(5, 1);

        assertEquals(1, result.size());
        verify(nativeQuery).setParameter("limit", 5);
        verify(nativeQuery).setParameter("partition", 1);
    }

    @Test
    void shouldFindAndLockWithoutSkipLocked() {
        ReflectionTestUtils.setField(repository, "useSkipLocked", false);
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        when(entityManager.createQuery(anyString(), eq(OutboxEventJpaEntity.class))).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.setLockMode(any())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(entity));

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, -1);

        assertEquals(1, result.size());
        verify(entityManager).createQuery(anyString(), eq(OutboxEventJpaEntity.class));
        verify(typedQuery).setMaxResults(10);
        verify(typedQuery).setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void shouldFindAndLockWithoutSkipLockedAndPartition() {
        ReflectionTestUtils.setField(repository, "useSkipLocked", false);
        when(entityManager.createQuery(anyString(), eq(OutboxEventJpaEntity.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.setLockMode(any())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of());

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, 2);

        assertTrue(result.isEmpty());
        verify(typedQuery).setParameter("partition", 2);
    }

    @Test
    void shouldReturnEmptyListWhenNoResults() {
        when(entityManager.createNativeQuery(anyString(), eq(OutboxEventJpaEntity.class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, -1);

        assertTrue(result.isEmpty());
    }
}
