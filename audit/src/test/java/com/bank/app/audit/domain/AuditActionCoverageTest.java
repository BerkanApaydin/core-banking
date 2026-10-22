package com.bank.app.audit.domain;

import com.bank.app.common.domain.event.AuditEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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

    /**
     * Ledger actions that deliberately have no producer yet. PASSWORD_CHANGED
     * is reserved for a future password-change flow (see AuditAction); every
     * other action must be producible, otherwise the ledger promises an
     * event that can never be audited.
     */
    private static final Set<String> ACTIONS_WITHOUT_A_PRODUCER = Set.of("PASSWORD_CHANGED");

    private static Set<String> auditEventConstants() throws Exception {
        Set<String> constants = new TreeSet<>();
        for (Field field : AuditEvent.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) && Modifier.isFinal(mods)
                    && field.getType() == String.class
                    && field.getName().equals(field.getName().toUpperCase())) {
                constants.add((String) field.get(null));
            }
        }
        return constants;
    }

    @Test
    void everyAuditEventConstantResolvesToEnum() throws Exception {
        Set<String> constants = auditEventConstants();
        assertThat(constants).as("AuditEvent must declare action constants").isNotEmpty();
        for (String action : constants) {
            assertThatCode(() -> AuditAction.fromString(action))
                    .as("AuditEvent constant must resolve: %s", action)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void transferMarkedFailedIsPersistable() {
        assertThatCode(() -> AuditAction.fromString(AuditEvent.TRANSFER_MARKED_FAILED))
                .doesNotThrowAnyException();
    }

    @Test
    void everyLedgerActionHasAProducerConstant() throws Exception {
        // Reverse direction: a ledger action nobody can publish is an audit
        // hole — the event would be rejected at runtime and abort the business
        // transaction that produced it.
        Set<String> producers = auditEventConstants();
        Set<String> withoutProducer = Arrays.stream(AuditAction.values())
                .map(Enum::name)
                .filter(action -> !producers.contains(action))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(withoutProducer)
                .as("ledger actions without a producer constant (keep the explicit exception list in sync)")
                .containsExactlyInAnyOrderElementsOf(ACTIONS_WITHOUT_A_PRODUCER);
    }

    @Test
    void theLedgerAndTheProducerConstantsStayInStep() throws Exception {
        // An accidental add/remove on either side turns red here: the count
        // must always be the producer count plus the documented exceptions.
        Set<String> producers = auditEventConstants();
        long ledgerActions = AuditAction.values().length;

        assertThat(ledgerActions)
                .as("ledger actions vs producer constants vs documented exceptions")
                .isEqualTo(producers.size() + ACTIONS_WITHOUT_A_PRODUCER.size());
        assertThat(producers).doesNotContain(ACTIONS_WITHOUT_A_PRODUCER.toArray(String[]::new));
    }
}
