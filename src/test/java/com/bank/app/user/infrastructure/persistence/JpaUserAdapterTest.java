package com.bank.app.user.infrastructure.persistence;

import com.bank.app.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JpaUserAdapterTest {

    private UserRepository userRepository;
    private JpaUserAdapter jpaUserAdapter;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jpaUserAdapter = new JpaUserAdapter(userRepository);
    }

    @Test
    void shouldThrowExceptionWhenSavingNullUser() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
                jpaUserAdapter.save(null)
        );

        assertEquals("User null olamaz", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldSaveUserSuccessfully() {
        User user = new User(1L, "testuser", "password", "ROLE_USER");

        jpaUserAdapter.save(user);

        verify(userRepository).save(any(UserJpaEntity.class));
    }

    @Test
    void shouldFindUserByUsername() {
        UserJpaEntity jpaEntity = new UserJpaEntity(1L, "testuser", "password", "ROLE_USER");
        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(jpaEntity));

        java.util.Optional<User> result = jpaUserAdapter.findByUsername("testuser");

        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(java.util.Optional.empty());

        java.util.Optional<User> result = jpaUserAdapter.findByUsername("nonexistent");

        assertFalse(result.isPresent());
        verify(userRepository).findByUsername("nonexistent");
    }
}
