package com.bank.app.transfer.application.service;

import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
@DisplayName("TransferAuthorizationService")
class TransferAuthorizationServiceTest {

    @Mock
    private AccountAclPort accountAclPort;

    @Mock
    private UserContextService userContextService;

    private TransferAuthorizationService service;

    private static final Long ACCOUNT_ID = 1L;
    private static final Long SENDER_USER_ID = 100L;
    private static final Long RECEIVER_USER_ID = 200L;
    private static final String IBAN = "TR290006200000000000000111";
    private static final String ERROR_MESSAGE = "You are not authorized.";
    private static final AccountInfo ACCOUNT_INFO = new AccountInfo(ACCOUNT_ID, SENDER_USER_ID, "TRY", "ACTIVE");

    @BeforeEach
    void setUp() {
        service = new TransferAuthorizationService(accountAclPort, userContextService);
    }

    @Nested
    @DisplayName("authorizeSender")
    class AuthorizeSender {

        @Test
        @DisplayName("should return account info when sender is authorized")
        void shouldReturnAccountInfoWhenAuthorized() {
            when(accountAclPort.getAccountInfoForTransfer(IBAN)).thenReturn(ACCOUNT_INFO);

            AccountInfo result = service.authorizeSender(IBAN);

            assertThat(result).isEqualTo(ACCOUNT_INFO);
            verify(accountAclPort).getAccountInfoForTransfer(IBAN);
            verify(userContextService).checkUserAuthorization(eq(SENDER_USER_ID),
                    eq("You are not authorized to transfer from this account."));
        }
    }

    @Nested
    @DisplayName("getReceiverInfo")
    class GetReceiverInfo {

        @Test
        @DisplayName("should return account info for receiver")
        void shouldReturnReceiverInfo() {
            when(accountAclPort.getAccountInfoForTransfer(IBAN)).thenReturn(ACCOUNT_INFO);

            AccountInfo result = service.getReceiverInfo(IBAN);

            assertThat(result).isEqualTo(ACCOUNT_INFO);
            verify(accountAclPort).getAccountInfoForTransfer(IBAN);
            verifyNoInteractions(userContextService);
        }
    }

    @Nested
    @DisplayName("authorizeByAccountId")
    class AuthorizeByAccountId {

        @Test
        @DisplayName("should return account info when authorized by account id")
        void shouldReturnAccountInfoWhenAuthorized() {
            when(accountAclPort.getAccountInfo(ACCOUNT_ID)).thenReturn(ACCOUNT_INFO);

            AccountInfo result = service.authorizeByAccountId(ACCOUNT_ID);

            assertThat(result).isEqualTo(ACCOUNT_INFO);
            verify(accountAclPort).getAccountInfo(ACCOUNT_ID);
            verify(userContextService).checkUserAuthorization(eq(SENDER_USER_ID),
                    eq("You are not authorized to cancel this transfer."));
        }
    }

    @Nested
    @DisplayName("authorizeAccountAccess")
    class AuthorizeAccountAccess {

        @Test
        @DisplayName("should return account info when access is authorized")
        void shouldReturnAccountInfoWhenAuthorized() {
            when(accountAclPort.getAccountInfo(ACCOUNT_ID)).thenReturn(ACCOUNT_INFO);

            AccountInfo result = service.authorizeAccountAccess(ACCOUNT_ID, ERROR_MESSAGE);

            assertThat(result).isEqualTo(ACCOUNT_INFO);
            verify(accountAclPort).getAccountInfo(ACCOUNT_ID);
            verify(userContextService).checkUserAuthorization(eq(SENDER_USER_ID), eq(ERROR_MESSAGE));
        }
    }

    @Nested
    @DisplayName("authorizeTransferAccess")
    class AuthorizeTransferAccess {

        @Test
        @DisplayName("should pass when current user matches sender")
        void shouldPassWhenSenderMatches() {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(SENDER_USER_ID));

            service.authorizeTransferAccess(SENDER_USER_ID, RECEIVER_USER_ID, ERROR_MESSAGE);

            verify(userContextService).getCurrentUserId();
        }

        @Test
        @DisplayName("should pass when current user matches receiver")
        void shouldPassWhenReceiverMatches() {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(RECEIVER_USER_ID));

            service.authorizeTransferAccess(SENDER_USER_ID, RECEIVER_USER_ID, ERROR_MESSAGE);

            verify(userContextService).getCurrentUserId();
        }

        @Test
        @DisplayName("should throw when current user matches neither sender nor receiver")
        void shouldThrowWhenNoMatch() {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(999L));

            assertThatThrownBy(() -> service.authorizeTransferAccess(SENDER_USER_ID, RECEIVER_USER_ID, ERROR_MESSAGE))
                    .isExactlyInstanceOf(AuthorizationException.class)
                    .hasMessage(ERROR_MESSAGE);
        }

        @Test
        @DisplayName("should throw when session not found")
        void shouldThrowWhenSessionNotFound() {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.authorizeTransferAccess(SENDER_USER_ID, RECEIVER_USER_ID, ERROR_MESSAGE))
                    .isExactlyInstanceOf(AuthorizationException.class)
                    .hasMessage("Session not found.");
        }
    }
}
