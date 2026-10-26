package com.bank.app.user.adapter.out.persistence;

import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.EmailAddress;
import com.bank.app.user.domain.PhoneNumber;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.EncodedPassword;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.exception.UsernameAlreadyTakenException;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class UserPersistenceAdapterTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    private UserPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserPersistenceAdapter(userJpaRepository, new UserJpaMapper());
    }

    private void stubSaveEcho() {
        when(userJpaRepository.save(any(UserJpaEntity.class))).thenAnswer(invocation -> {
            UserJpaEntity entity = invocation.getArgument(0);
            return new UserJpaEntity(entity.getId() == null ? 42L : entity.getId(),
                    entity.getUsername(), entity.getPassword(), entity.getRole(),
                    entity.getEmail(), entity.getPhone(), entity.getVersion());
        });
    }

    @Test
    void shouldSaveUserSuccessfully() {
        stubSaveEcho();
        UserJpaEntity existingEntity = new UserJpaEntity();
        existingEntity.setId(1L);
        existingEntity.setVersion(0L);
        existingEntity.setUsername("testuser");
        existingEntity.setPassword("oldpassword");
        existingEntity.setRole(Role.ROLE_USER);
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

        User user = new User(new UserId(1L), "testuser", "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER, null, null, 0L);
        // Kills the NullReturnVals mutant: the saved aggregate must come back.
        var result = adapter.save(user);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(userJpaRepository).save(any(UserJpaEntity.class));
    }

    @Test
    void shouldFindUserByUsername() {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(1L);
        entity.setUsername("testuser");
        entity.setPassword("$2a$12$testpasswordhash00000000000000000000001");
        entity.setRole(Role.ROLE_USER);
        when(userJpaRepository.findByUsername("testuser")).thenReturn(Optional.of(entity));

        Optional<User> result = adapter.findByUsername("testuser");

        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        verify(userJpaRepository).findByUsername("testuser");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        when(userJpaRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        Optional<User> result = adapter.findByUsername("nonexistent");

        assertFalse(result.isPresent());
        verify(userJpaRepository).findByUsername("nonexistent");
    }

    @Test
    void shouldClassifyUserLookupDatabaseFailureAsBackendUnavailable() {
        when(userJpaRepository.findByUsername("testuser"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(AuthenticationBackendUnavailableException.class,
                () -> adapter.findByUsername("testuser"));
    }

    @Test
    void shouldFindUserByUsernameForUpdate() {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(1L);
        entity.setUsername("testuser");
        entity.setPassword("$2a$12$testpasswordhash00000000000000000000001");
        entity.setRole(Role.ROLE_USER);
        when(userJpaRepository.findByUsernameForUpdate("testuser")).thenReturn(Optional.of(entity));

        Optional<User> result = adapter.findByUsernameForUpdate("testuser");

        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        verify(userJpaRepository).findByUsernameForUpdate("testuser");
    }

    @Test
    void shouldClassifyLockedLookupFailureAsBackendUnavailable() {
        when(userJpaRepository.findByUsernameForUpdate("testuser"))
                .thenThrow(new DataAccessResourceFailureException("lock timeout"));

        assertThrows(AuthenticationBackendUnavailableException.class,
                () -> adapter.findByUsernameForUpdate("testuser"));
    }

    @Nested
    @DisplayName("save new user")
    class SaveNewUser {

        @Test
        @DisplayName("should create new entity when id is null")
        void shouldCreateNewEntityWhenIdIsNull() {
            stubSaveEcho();
            User user = new User(null, "newuser", "$2a$12$testhashedhash0000000000000000000000001", Role.ROLE_USER);

            adapter.save(user);

            ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
            verify(userJpaRepository).save(captor.capture());
            UserJpaEntity saved = captor.getValue();
            assertNull(saved.getId());
            assertEquals("newuser", saved.getUsername());
            assertEquals(Role.ROLE_USER, saved.getRole());
        }

        @Test
        @DisplayName("should map email and phone when present")
        void shouldMapEmailAndPhoneWhenPresent() {
            stubSaveEcho();
            User user = new User(null, "newuser", "$2a$12$testhashedhash0000000000000000000000001", Role.ROLE_USER,
                    new EmailAddress("test@example.com"), new PhoneNumber("+905551234567"));

            adapter.save(user);

            ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
            verify(userJpaRepository).save(captor.capture());
            assertEquals("test@example.com", captor.getValue().getEmail());
            assertEquals("+905551234567", captor.getValue().getPhone());
        }

        @Test
        @DisplayName("should leave email and phone null when not present")
        void shouldLeaveEmailAndPhoneNullWhenNotPresent() {
            stubSaveEcho();
            User user = new User(null, "newuser", "$2a$12$testhashedhash0000000000000000000000001", Role.ROLE_USER);

            adapter.save(user);

            ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
            verify(userJpaRepository).save(captor.capture());
            assertNull(captor.getValue().getEmail());
            assertNull(captor.getValue().getPhone());
        }
    }

    @Test
    void shouldReturnEmptyForNullUserIdWithoutQuerying() {
        assertTrue(adapter.findById(null).isEmpty());
        verifyNoInteractions(userJpaRepository);
    }

    @Test
    void shouldFindById() {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(1L);
        entity.setUsername("testuser");
        entity.setPassword("$2a$12$testpasswordhash00000000000000000000001");
        entity.setRole(Role.ROLE_USER);
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertTrue(adapter.findById(1L).isPresent());
        verify(userJpaRepository).findById(1L);
    }

    @Test
    void shouldClassifyFindByIdFailureAsBackendUnavailable() {
        when(userJpaRepository.findById(1L))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(AuthenticationBackendUnavailableException.class, () -> adapter.findById(1L));
    }

    @Nested
    @DisplayName("save existing user")
    class SaveExistingUser {

        @Test
        @DisplayName("should update entity when found")
        void shouldUpdateEntityWhenFound() {
            stubSaveEcho();
            UserJpaEntity existingEntity = new UserJpaEntity();
            existingEntity.setId(1L);
            existingEntity.setVersion(0L);
            existingEntity.setUsername("testuser");
            existingEntity.setPassword("oldpassword");
            existingEntity.setRole(Role.ROLE_USER);
            when(userJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

            User user = new User(new UserId(1L), "testuser", "$2a$12$testnewpasswordhash000000000000000000001", Role.ROLE_USER,
                    new EmailAddress("test@example.com"), new PhoneNumber("+905551234567"), 0L);
            adapter.save(user);

            ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
            verify(userJpaRepository).save(captor.capture());
            UserJpaEntity saved = captor.getValue();
            assertEquals("testuser", saved.getUsername());
            assertEquals("$2a$12$testnewpasswordhash000000000000000000001", saved.getPassword());
            assertEquals("test@example.com", saved.getEmail());
            assertEquals("+905551234567", saved.getPhone());
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrowWhenNotFound() {
            when(userJpaRepository.findById(99L)).thenReturn(Optional.empty());

            User user = new User(new UserId(99L), "nonexistent", "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER);
            assertThrows(IllegalArgumentException.class, () -> adapter.save(user));
        }

        @Test
        @DisplayName("should reject a stale aggregate version instead of overwriting")
        void shouldRejectStaleVersion() {
            UserJpaEntity existingEntity = new UserJpaEntity();
            existingEntity.setId(1L);
            existingEntity.setVersion(2L);
            when(userJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

            User stale = new User(new UserId(1L), "testuser",
                    "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER, null, null, 1L);

            // AV: a concurrent update (for example a token-version bump) must
            // not be silently overwritten by a stale in-memory aggregate.
            assertThrows(ObjectOptimisticLockingFailureException.class, () -> adapter.save(stale));
            verify(userJpaRepository, never()).save(any(UserJpaEntity.class));
        }

        @Test
        @DisplayName("should reject an aggregate without a version for an existing row")
        void shouldRejectMissingVersion() {
            UserJpaEntity existingEntity = new UserJpaEntity();
            existingEntity.setId(1L);
            existingEntity.setVersion(0L);
            when(userJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

            User unversioned = new User(new UserId(1L), "testuser",
                    "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER);

            assertThrows(ObjectOptimisticLockingFailureException.class, () -> adapter.save(unversioned));
            verify(userJpaRepository, never()).save(any(UserJpaEntity.class));
        }

        @Test
        @DisplayName("should set email and phone to null when not present")
        void shouldSetEmailAndPhoneToNullWhenNotPresent() {
            stubSaveEcho();
            UserJpaEntity existingEntity = new UserJpaEntity();
            existingEntity.setId(1L);
            existingEntity.setVersion(0L);
            existingEntity.setEmail("old@example.com");
            existingEntity.setPhone("+901234567890");
            when(userJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

            User user = new User(new UserId(1L), "testuser", "$2a$12$testpasswordhash00000000000000000000001", Role.ROLE_USER, null, null, 0L);
            adapter.save(user);

            ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
            verify(userJpaRepository).save(captor.capture());
            assertNull(captor.getValue().getEmail());
            assertNull(captor.getValue().getPhone());
        }
    }

    @Nested
    @DisplayName("concurrent duplicate translation (AV-4)")
    class ConcurrentDuplicate {

        @Test
        @DisplayName("should map unique-constraint violation to UsernameAlreadyTakenException")
        void shouldMapConcurrentDuplicateToConflict() {
            User user = User.create("newuser",
                    EncodedPassword.of("$2a$12$testpasswordhash00000000000000000000001"),
                    null, null, java.time.Clock.fixed(java.time.Instant.parse("2026-06-22T10:00:00Z"), java.time.ZoneOffset.UTC));
            when(userJpaRepository.save(any(UserJpaEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            UsernameAlreadyTakenException failure = assertThrows(
                    UsernameAlreadyTakenException.class, () -> adapter.save(user));
            assertTrue(failure.getMessage().contains("newuser"));
        }
    }

    @Test
    void shouldReadMfaEnabledProjection() {
        when(userJpaRepository.findMfaEnabled(1L)).thenReturn(Optional.of(true));

        Optional<Boolean> result = adapter.findMfaEnabledById(1L);

        assertTrue(result.isPresent());
        assertTrue(result.get());
        verify(userJpaRepository).findMfaEnabled(1L);
    }

    @Test
    void shouldReturnEmptyMfaProjectionForNullId() {
        assertTrue(adapter.findMfaEnabledById(null).isEmpty());
        verifyNoInteractions(userJpaRepository);
    }

    @Test
    void shouldClassifyMfaProjectionFailureAsBackendUnavailable() {
        when(userJpaRepository.findMfaEnabled(1L))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(AuthenticationBackendUnavailableException.class,
                () -> adapter.findMfaEnabledById(1L));
    }

    @Nested
    @DisplayName("findByUsername")
    class FindByUsername {

        @Test
        @DisplayName("should map email and phone when present in entity")
        void shouldMapEmailAndPhoneWhenPresent() {
            UserJpaEntity entity = new UserJpaEntity();
            entity.setId(1L);
            entity.setUsername("testuser");
            entity.setPassword("$2a$12$testpasswordhash00000000000000000000001");
            entity.setRole(Role.ROLE_USER);
            entity.setEmail("test@example.com");
            entity.setPhone("+905551234567");
            when(userJpaRepository.findByUsername("testuser")).thenReturn(Optional.of(entity));

            Optional<User> result = adapter.findByUsername("testuser");

            assertTrue(result.isPresent());
            assertEquals("test@example.com", result.get().getEmail().value());
            assertEquals("+905551234567", result.get().getPhone().value());
        }
    }
}
