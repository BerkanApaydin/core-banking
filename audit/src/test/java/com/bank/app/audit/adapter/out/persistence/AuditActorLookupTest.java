package com.bank.app.audit.adapter.out.persistence;

import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The per-identity admin view is only as trustworthy as the link between the
 * written row and the actor-filtered query: a row written WITH an actor must
 * be found by that actor's query (and keep its identity on the way out), a
 * row written WITHOUT one must not show up under any identity.
 *
 * <p>The repository is stubbed with the same predicate the database applies
 * ({@code actor_user_id = ?}), so the row set behaves like a real query.
 */
@ExtendWith(MockitoExtension.class)
class AuditActorLookupTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2026, 6, 22, 10, 0);

    @Mock
    private AuditLogJpaRepository repository;

    private AuditLogPersistenceAdapter adapter;

    private final List<AuditLogJpaEntity> storedRows = List.of(
            new AuditLogJpaEntity(1L, "alice", AuditAction.TRANSFER_EXECUTED, "Transfer 7 executed",
                    TIMESTAMP, 7L),
            new AuditLogJpaEntity(2L, "system", AuditAction.LOGIN_FAILED, "Login failed",
                    TIMESTAMP, null));

    @BeforeEach
    void setUp() {
        adapter = new AuditLogPersistenceAdapter(repository, new AuditLogJpaMapper());
    }

    private void stubActorFilteredQuery() {
        when(repository.findByActorUserIdOrderByTimestampDescIdDesc(any(), any()))
                .thenAnswer(invocation -> {
                    Long actorUserId = invocation.getArgument(0);
                    return storedRows.stream()
                            .filter(row -> actorUserId.equals(row.getActorUserId()))
                            .toList();
                });
    }

    @Test
    void shouldFindTheEntryThatWasWrittenWithThatActor() {
        stubActorFilteredQuery();

        List<AuditLog> found = adapter.findByActor(7L, 50);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(1L);
        // The identity must survive the mapping, otherwise the actor-filtered
        // view returns rows that no longer say who they belong to.
        assertThat(found.get(0).getActorUserId()).isEqualTo(7L);
        assertThat(found.get(0).getUsername()).isEqualTo("alice");
        // The predicate parameter is the identity, never the display username:
        // filtering by username would miss a renamed user and could attribute
        // rows across users that share a name.
        verify(repository).findByActorUserIdOrderByTimestampDescIdDesc(eq(7L), any());
    }

    @Test
    void shouldNotReturnAnEntryThatWasWrittenWithoutAnActor() {
        // A row without an actor belongs to no identity: it must never be
        // attributed to somebody, not even as a fallback.
        stubActorFilteredQuery();

        List<AuditLog> found = adapter.findByActor(7L, 50);

        assertThat(found).extracting(AuditLog::getId).doesNotContain(2L);
        assertThat(found).extracting(AuditLog::getActorUserId).doesNotContainNull();
    }

    @Test
    void shouldReturnNothingForAnActorWithoutEntries() {
        stubActorFilteredQuery();

        List<AuditLog> found = adapter.findByActor(99L, 50);

        assertThat(found).isEmpty();
    }

    @Test
    void shouldKeepTheActorIdentityOfARowWrittenThroughTheDomainModel() {
        // A row built with an actor (the event-driven write path) must round
        // trip through the entity and back with its identity intact.
        AuditLog written = new AuditLog(null, "bob", AuditAction.LOGIN_SUCCEEDED, "Logged in",
                TIMESTAMP, 42L);

        AuditLogJpaMapper mapper = new AuditLogJpaMapper();
        AuditLog readBack = mapper.toDomain(mapper.toJpaEntity(written));

        assertThat(readBack.getActorUserId()).isEqualTo(42L);
        assertThat(readBack.getUsername()).isEqualTo("bob");
    }

    @Test
    void shouldNotAttributeARowWrittenWithoutAnActorToAnyIdentity() {
        AuditLog writtenWithoutActor = new AuditLog(null, "system", AuditAction.LOGIN_FAILED,
                "Login failed", TIMESTAMP);

        AuditLogJpaMapper mapper = new AuditLogJpaMapper();
        AuditLog readBack = mapper.toDomain(mapper.toJpaEntity(writtenWithoutActor));

        assertThat(readBack.getActorUserId()).isNull();
    }
}
