package com.bank.app.i18n;

import com.bank.app.common.messages.MessageKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Single-source-of-truth contract for i18n problem-message keys.
 *
 * <p>Production code must reference {@link MessageKeys} constants instead of raw
 * {@code "error.*"} literals (shotgun-surgery hotspot: a key rename previously
 * required touching every throw site and handler with no compiler help). This
 * test pins both directions:
 * <ol>
 *   <li>every constant resolves in the EN and TR bundles (a rename without a
 *   properties update fails the build instead of silently falling back to the
 *   hard-coded default text), and</li>
 *   <li>every {@code error.*} entry in the EN bundle has a constant (a newly
 *   added key without centralization fails the build).</li>
 * </ol>
 * Test fixtures intentionally keep synthetic keys ({@code error.test},
 * {@code error.unknown_key}, ...) to exercise the unknown-key fallback path;
 * those live only in {@code src/test} and MUST NOT gain constants here.
 */
@DisplayName("MessageKeys contract (constants vs bundles)")
class MessageKeysContractTest {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private static List<String> constantValues() throws Exception {
        List<String> values = new ArrayList<>();
        for (Field f : MessageKeys.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                values.add((String) f.get(null));
            }
        }
        return values.stream().sorted().toList();
    }

    @Test
    @DisplayName("every constant resolves in the EN and TR bundles")
    void everyConstantResolvesInBothBundles() throws Exception {
        ResourceBundle en = ResourceBundle.getBundle("messages", Locale.ENGLISH);
        ResourceBundle tr = ResourceBundle.getBundle("messages", TR);

        List<String> unresolvable = new ArrayList<>();
        for (String key : constantValues()) {
            if (!en.containsKey(key) || !tr.containsKey(key)) {
                unresolvable.add(key);
            }
        }

        assertThat(unresolvable)
                .as("MessageKeys constants missing from messages.properties/messages_tr.properties")
                .isEmpty();
    }

    @Test
    @DisplayName("every error.* bundle entry has a MessageKeys constant")
    void everyBundleKeyHasAConstant() throws Exception {
        ResourceBundle en = ResourceBundle.getBundle("messages", Locale.ENGLISH);
        Set<String> constants = new HashSet<>(constantValues());

        Set<String> bundleKeys = en.keySet().stream()
                .filter(k -> k.startsWith("error."))
                .collect(Collectors.toSet());
        bundleKeys.removeAll(constants);

        assertThat(bundleKeys)
                .as("messages.properties error.* keys without a MessageKeys constant "
                        + "(add the constant instead of a raw literal)")
                .isEmpty();
    }

    @Test
    @DisplayName("constant values are unique")
    void constantValuesAreUnique() throws Exception {
        List<String> values = constantValues();

        assertThat(new HashSet<>(values)).hasSameSizeAs(values);
    }
}
