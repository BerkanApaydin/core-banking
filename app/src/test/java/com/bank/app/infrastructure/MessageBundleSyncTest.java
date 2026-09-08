package com.bank.app.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@code error.*}/{@code validation.*} key triple
 * (code + {@code messages.properties} + {@code messages_tr.properties}):
 * a key missing in either locale silently falls back to the raw key at runtime.
 */
class MessageBundleSyncTest {

    private static Set<String> keys(String baseName, Locale locale) {
        return ResourceBundle.getBundle(baseName, locale).keySet().stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Test
    void englishAndTurkishBundlesShouldDefineTheSameKeys() {
        Set<String> en = keys("messages", Locale.ENGLISH);
        Set<String> tr = keys("messages", Locale.forLanguageTag("tr"));

        assertThat(en).as("EN bundle must not be empty").isNotEmpty();
        assertThat(tr)
                .as("keys present in EN but missing in TR")
                .containsExactlyInAnyOrderElementsOf(en);
    }
}
