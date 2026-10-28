package com.bank.app.architecture;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@SuppressWarnings("null")
class LedgerIntegrityArchitectureTest extends ArchitectureTest {

    @Test
    void ledgerRepositoryShouldOnlyBeUsedByItsPersistenceAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app..")
                .and().resideOutsideOfPackage("com.bank.app.account.adapter.out.persistence..")
                .should().dependOnClassesThat().haveSimpleName("LedgerEntryJpaRepository")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }
}
