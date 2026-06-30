package com.bank.app.user.application.usecase;
import com.bank.app.common.application.port.out.JwtPort;

import com.bank.app.common.application.port.out.TokenBlacklistPort;
import com.bank.app.user.application.port.in.LogoutUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutUseCaseImpl")
class LogoutUseCaseImplTest {

    @Mock
    private TokenBlacklistPort tokenBlacklistPort;

    @Mock
    private JwtPort jwtPort;

    private LogoutUseCase logoutUseCase;

    @BeforeEach
    void setUp() {
        logoutUseCase = new LogoutUseCaseImpl(tokenBlacklistPort, jwtPort);
    }

    @Test
    @DisplayName("should blacklist token when valid Bearer token is provided")
    void shouldBlacklistTokenWhenBearerTokenIsValid() {
        String authHeader = "Bearer valid-jwt-token";
        when(jwtPort.getExpirationMs()).thenReturn(3600000L);

        logoutUseCase.execute(authHeader);

        verify(jwtPort).getExpirationMs();
        verify(tokenBlacklistPort).blacklist("valid-jwt-token", 3600000L);
    }

    @Test
    @DisplayName("should do nothing when header does not start with Bearer")
    void shouldDoNothingWhenHeaderIsNotBearer() {
        String authHeader = "Basic token123";

        logoutUseCase.execute(authHeader);

        verifyNoInteractions(jwtPort);
        verifyNoInteractions(tokenBlacklistPort);
    }

    @Test
    @DisplayName("should do nothing when header is null")
    void shouldDoNothingWhenHeaderIsNull() {
        logoutUseCase.execute(null);

        verifyNoInteractions(jwtPort);
        verifyNoInteractions(tokenBlacklistPort);
    }
}
