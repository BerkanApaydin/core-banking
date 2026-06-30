package com.bank.app.user.adapter.out.persistence;

import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.EmailAddress;
import com.bank.app.user.domain.PhoneNumber;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class UserJpaAdapterTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    private UserPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserPersistenceAdapter(userJpaRepository, new UserJpaMapper());
    }

    @Test
    void shouldSaveUserSuccessfully() {
        UserJpaEntity existingEntity = new UserJpaEntity();
        existingEntity.setId(1L);
        existingEntity.setUsername("testuser");
        existingEntity.setPassword("oldpassword");
        existingEntity.setRole("ROLE_USER");
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

        User user = new User(new UserId(1L), "testuser", "password", Role.ROLE_USER);
        adapter.save(user);

        verify(userJpaRepository).save(any(UserJpaEntity.class));
    }

    @Test
    void shouldFindUserByUsername() {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(1L);
        entity.setUsername("testuser");
        entity.setPassword("password");
        entity.setRole("ROLE_USER");
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

    @Nested
    @DisplayName("save new user")
    class SaveNewUser {

        @Test
        @DisplayName("should create new entity when id is null")
        void shouldCreateNewEntityWhenIdIsNull() {
            User user = new User(null, "newuser", "hashed", Role.ROLE_USER);

            adapter.save(user);

            ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
            verify(userJpaRepository).save(captor.capture());
            UserJpaEntity saved = captor.getValue();
            assertNull(saved.getId());
            assertEquals("newuser", saved.getUsername());
            assertEquals("ROLE_USER", saved.getRole());
        }

        @Test
        @DisplayName("should map email and phone when present")
        void shouldMapEmailAndPhoneWhenPresent() {
            User user = new User(null, "newuser", "hashed", Role.ROLE_USER,
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
            User user = new User(null, "newuser", "hashed", Role.ROLE_USER);

            adapter.save(user);

            ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
            verify(userJpaRepository).save(captor.capture());
            assertNull(captor.getValue().getEmail());
            assertNull(captor.getValue().getPhone());
        }
    }

    @Nested
    @DisplayName("save existing user")
    class SaveExistingUser {

        @Test
        @DisplayName("should update entity when found")
        void shouldUpdateEntityWhenFound() {
            UserJpaEntity existingEntity = new UserJpaEntity();
            existingEntity.setId(1L);
            existingEntity.setUsername("testuser");
            existingEntity.setPassword("oldpassword");
            existingEntity.setRole("ROLE_USER");
            when(userJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

            User user = new User(new UserId(1L), "testuser", "newpassword", Role.ROLE_USER,
                    new EmailAddress("test@example.com"), new PhoneNumber("+905551234567"));
            adapter.save(user);

            ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
            verify(userJpaRepository).save(captor.capture());
            UserJpaEntity saved = captor.getValue();
            assertEquals("testuser", saved.getUsername());
            assertEquals("newpassword", saved.getPassword());
            assertEquals("test@example.com", saved.getEmail());
            assertEquals("+905551234567", saved.getPhone());
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrowWhenNotFound() {
            when(userJpaRepository.findById(99L)).thenReturn(Optional.empty());

            User user = new User(new UserId(99L), "nonexistent", "password", Role.ROLE_USER);
            assertThrows(IllegalArgumentException.class, () -> adapter.save(user));
        }

        @Test
        @DisplayName("should set email and phone to null when not present")
        void shouldSetEmailAndPhoneToNullWhenNotPresent() {
            UserJpaEntity existingEntity = new UserJpaEntity();
            existingEntity.setId(1L);
            existingEntity.setEmail("old@example.com");
            existingEntity.setPhone("+901234567890");
            when(userJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

            User user = new User(new UserId(1L), "testuser", "password", Role.ROLE_USER);
            adapter.save(user);

            ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
            verify(userJpaRepository).save(captor.capture());
            assertNull(captor.getValue().getEmail());
            assertNull(captor.getValue().getPhone());
        }
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
            entity.setPassword("password");
            entity.setRole("ROLE_USER");
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
