package com.bank.app.user.adapter.out.notification;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bank.app.common.domain.TokenDigest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security contract of the simulation reset channel: the raw token is
 * password-equivalent and must never reach logs, while a digest prefix and a
 * masked contact are logged for correlation.
 */
@SuppressWarnings("null")
class LoggingPasswordResetNotificationAdapterTest {

    private final LoggingPasswordResetNotificationAdapter notifier = new LoggingPasswordResetNotificationAdapter();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(LoggingPasswordResetNotificationAdapter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private String lastMessage() {
        assertFalse(appender.list.isEmpty(), "expected a log line");
        return appender.list.get(appender.list.size() - 1).getFormattedMessage();
    }

    @Test
    void shouldNeverLogRawResetToken() {
        String rawToken = "raw-reset-token-that-must-not-leak";

        notifier.sendPasswordResetToken("alice", "alice@example.com", rawToken);

        assertFalse(lastMessage().contains(rawToken));
    }

    @Test
    void shouldLogOnlyTheDigestPrefixForCorrelation() {
        String rawToken = "raw-reset-token";
        String expectedPrefix = TokenDigest.sha256Hex(rawToken).substring(0, 12) + "...";

        notifier.sendPasswordResetToken("alice", "alice@example.com", rawToken);

        assertTrue(lastMessage().contains(expectedPrefix));
    }

    @Test
    void shouldMaskTheContactAddress() {
        notifier.sendPasswordResetToken("alice", "alice@example.com", "token");

        String message = lastMessage();
        assertFalse(message.contains("alice@example.com"));
        assertTrue(message.contains("a***m"));
    }

    @Test
    void shouldMaskShortOrNullContact() {
        notifier.sendPasswordResetToken("alice", "abc", "token");
        // A three-character contact is entirely masked; boundary mutants that
        // switch to <= / < would leak the first and last characters.
        assertTrue(lastMessage().contains("contact '***'"));

        notifier.sendPasswordResetToken("alice", null, "token");
        assertTrue(lastMessage().contains("contact '***'"));
    }

    @Test
    void shouldHandleNullTokenWithoutThrowing() {
        assertDoesNotThrow(() -> notifier.sendPasswordResetToken("alice", "a@b.co", null));

        assertTrue(lastMessage().contains("null"));
    }
}
