package com.bank.app.architecture;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Infrastructure split step 1 (docs/decisions/infrastructure-split.md): seal
 * the in-package boundaries before the Maven split. Security/outbox/observability
 * must not depend on each other directly; sharing goes through common + ports.
 */
class InfrastructureSplitArchitectureTest extends ArchitectureTest {

    @Test
    void securityShouldNotDependOnOutboxInternals() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..infrastructure.adapter.in.security..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.adapter.in.outbox..")
                .allowEmptyShould(false);
        rule.check(importedClasses);
    }

    @Test
    void outboxShouldNotDependOnSecurityInternals() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..infrastructure.adapter.in.outbox..",
                        "..infrastructure.adapter.out.persistence..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.adapter.in.security..")
                .allowEmptyShould(false);
        rule.check(importedClasses);
    }
}
