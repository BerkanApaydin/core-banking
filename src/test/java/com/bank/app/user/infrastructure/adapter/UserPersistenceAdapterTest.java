package com.bank.app.user.infrastructure.adapter;

import com.bank.app.user.domain.User;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserJpaAdapterTest {

    @Mock private UserRepository userRepository;

    private UserPersistenceAdapter UserPersistenceAdapter;

    @BeforeEach
    void setUp() {
        UserPersistenceAdapter = new UserPersistenceAdapter(userRepository);
    }

    @Test
    void shouldThrowExceptionWhenSavingNullUser() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                UserPersistenceAdapter.save(null)
        );

        assertEquals("User null olamaz", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldSaveUserSuccessfully() {
        User user = new User(1L, "testuser", "password", "ROLE_USER");

        UserPersistenceAdapter.save(user);

        verify(userRepository).save(any(UserJpaEntity.class));
    }

    @Test
    void shouldFindUserByUsername() {
        UserJpaEntity jpaEntity = new UserJpaEntity(1L, "testuser", "password", "ROLE_USER");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(jpaEntity));

        Optional<User> result = UserPersistenceAdapter.findByUsername("testuser");

        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        Optional<User> result = UserPersistenceAdapter.findByUsername("nonexistent");

        assertFalse(result.isPresent());
        verify(userRepository).findByUsername("nonexistent");
    }
}
