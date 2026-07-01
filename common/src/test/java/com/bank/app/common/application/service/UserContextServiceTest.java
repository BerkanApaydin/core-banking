package com.bank.app.common.application.service;

import com.bank.app.common.application.port.out.SecurityContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextServiceTest {

    private StubSecurityContextPort port;
    private UserContextService service;

    @BeforeEach
    void setUp() {
        port = new StubSecurityContextPort();
        service = new UserContextService(port);
    }

    @Test
    void getCurrentUserIdShouldReturnFromPort() {
        port.userId = Optional.of(42L);
        assertThat(service.getCurrentUserId()).contains(42L);
    }

    @Test
    void getCurrentUserIdShouldReturnEmptyWhenPortReturnsEmpty() {
        port.userId = Optional.empty();
        assertThat(service.getCurrentUserId()).isEmpty();
    }

    @Test
    void getCurrentUsernameShouldReturnFromPort() {
        port.username = Optional.of("testuser");
        assertThat(service.getCurrentUsername()).contains("testuser");
    }

    @Test
    void getCurrentUsernameShouldReturnEmptyWhenPortReturnsEmpty() {
        port.username = Optional.empty();
        assertThat(service.getCurrentUsername()).isEmpty();
    }

    @Test
    void checkUserAuthorizationShouldDelegateToPort() {
        service.checkUserAuthorization(1L, "not authorized");
        assertThat(port.lastCheckedUserId).isEqualTo(1L);
        assertThat(port.lastCheckedMessage).isEqualTo("not authorized");
    }

    @Test
    void checkUserAuthorizationShouldThrowWhenPortThrows() {
        port.throwOnCheck = true;
        assertThatThrownBy(() -> service.checkUserAuthorization(1L, "error"))
                .isExactlyInstanceOf(SecurityException.class)
                .hasMessage("error");
    }

    private static class StubSecurityContextPort implements SecurityContextPort {
        Optional<Long> userId = Optional.empty();
        Optional<String> username = Optional.empty();
        Long lastCheckedUserId;
        String lastCheckedMessage;
        boolean throwOnCheck;

        @Override
        public Optional<Long> getCurrentUserId() {
            return userId;
        }

        @Override
        public Optional<String> getCurrentUsername() {
            return username;
        }

        @Override
        public void checkUserAuthorization(Long resourceUserId, String errorMessage) {
            if (throwOnCheck) {
                throw new SecurityException(errorMessage);
            }
            lastCheckedUserId = resourceUserId;
            lastCheckedMessage = errorMessage;
        }
    }
}
