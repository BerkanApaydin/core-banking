package com.bank.app.audit.domain;

import com.bank.app.common.domain.event.AuditEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Magic-string regression: producers publish {@link AuditEvent} with String
 * actions while the ledger persists the {@link AuditAction} enum. Every
 * {@code AuditEvent} action constant must resolve via
 * {@link AuditAction#fromString}, so an enum rename turns red here instead of
 * silently breaking the audit trail (or throwing at runtime in the bridge).
 */
class AuditActionCoverageTest {

    @Test
    void everyAuditEventConstantResolvesToEnum() throws Exception {
        TreeSet<String> constants = new TreeSet<>();
        for (Field field : AuditEvent.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) && Modifier.isFinal(mods)
                    && field.getType() == String.class
                    && field.getName().equals(field.getName().toUpperCase())) {
                constants.add((String) field.get(null));
            }
        }
        assertThat(constants).as("AuditEvent must declare action constants").isNotEmpty();
        for (String action : constants) {
            assertThatCode(() -> AuditAction.fromString(action))
                    .as("AuditEvent constant must resolve: %s", action)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void reaperOutcomeIsPersistable() {
        assertThatCode(() -> AuditAction.fromString(AuditEvent.TRANSFER_MARKED_FAILED))
                .doesNotThrowAnyException();
    }
}
