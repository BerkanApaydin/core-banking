package com.bank.app.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FieldLimits} is consumed as compile-time constants in
 * {@code @Size}/{@code @Pattern} annotations of other modules, so a drift
 * between the int and the String form (or between the constant and the
 * regex bound) is silent until a request is rejected in production.
 */
@DisplayName("FieldLimits")
class FieldLimitsTest {

    private static final Pattern USERNAME_LENGTH_BOUND = Pattern.compile("\\{1,(\\d+)}");

    @Test
    @DisplayName("should keep the int and String representations of the text cap in sync")
    void shouldKeepIntAndStringRepresentationsInSync() {
        assertThat(FieldLimits.MAX_TEXT_LENGTH).isEqualTo(255);
        assertThat(FieldLimits.MAX_TEXT).isEqualTo(String.valueOf(FieldLimits.MAX_TEXT_LENGTH));
    }

    @Test
    @DisplayName("should bound the username regex by the canonical text cap")
    void shouldBoundUsernameRegexByMaxTextLength() {
        Matcher matcher = USERNAME_LENGTH_BOUND.matcher(FieldLimits.USERNAME_REGEX);

        assertThat(matcher.find()).as("username regex must carry an explicit length bound").isTrue();
        assertThat(Integer.parseInt(matcher.group(1))).isEqualTo(FieldLimits.MAX_TEXT_LENGTH);
    }

    @Test
    @DisplayName("should anchor the username regex")
    void shouldAnchorUsernameRegex() {
        assertThat(FieldLimits.USERNAME_REGEX).startsWith("^").endsWith("$");
    }

    @Test
    @DisplayName("should accept a legal username")
    void shouldAcceptLegalUsername() {
        assertThat("user.name-1_2").matches(FieldLimits.USERNAME_REGEX);
        assertThat("A").matches(FieldLimits.USERNAME_REGEX);
        assertThat("user_42").matches(FieldLimits.USERNAME_REGEX);
    }

    @Test
    @DisplayName("should accept a username of exactly the maximum length")
    void shouldAcceptUsernameOfExactlyMaxLength() {
        String maxLength = "a".repeat(FieldLimits.MAX_TEXT_LENGTH);

        assertThat(maxLength).matches(FieldLimits.USERNAME_REGEX);
    }

    @Test
    @DisplayName("should reject an empty username")
    void shouldRejectEmptyUsername() {
        assertThat("").doesNotMatch(FieldLimits.USERNAME_REGEX);
    }

    @Test
    @DisplayName("should reject a username containing whitespace")
    void shouldRejectUsernameWithWhitespace() {
        assertThat("user name").doesNotMatch(FieldLimits.USERNAME_REGEX);
        assertThat(" user").doesNotMatch(FieldLimits.USERNAME_REGEX);
        assertThat("user ").doesNotMatch(FieldLimits.USERNAME_REGEX);
        assertThat("user\tname").doesNotMatch(FieldLimits.USERNAME_REGEX);
    }

    @Test
    @DisplayName("should reject a username longer than the text cap")
    void shouldRejectOverlongUsername() {
        String tooLong = "a".repeat(FieldLimits.MAX_TEXT_LENGTH + 1);

        assertThat(tooLong).doesNotMatch(FieldLimits.USERNAME_REGEX);
    }

    @Test
    @DisplayName("should reject glob metacharacters that would break the Redis SCAN key")
    void shouldRejectGlobMetacharacters() {
        // The username is embedded in a Redis SCAN glob; a wildcard would
        // make the key namespace ambiguous and widen the matched key set.
        assertThat("user*name").doesNotMatch(FieldLimits.USERNAME_REGEX);
        assertThat("user?name").doesNotMatch(FieldLimits.USERNAME_REGEX);
        assertThat("user[name]").doesNotMatch(FieldLimits.USERNAME_REGEX);
        assertThat("user\\name").doesNotMatch(FieldLimits.USERNAME_REGEX);
    }
}
