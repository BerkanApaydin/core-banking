package com.bank.app.architecture;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@SuppressWarnings("null")
class ModuleBoundariesArchitectureTest extends ArchitectureTest {

    @Test
    void accountApplicationShouldNotDependOnTransferAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.account.application..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.transfer.adapter..");

        rule.check(importedClasses);
    }

    @Test
    void transferApplicationShouldNotDependOnAccountAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.application..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.account.adapter..");

        rule.check(importedClasses);
    }

    @Test
    void transferModuleShouldNotDependOnAccountDomainDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.account.domain..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void transferModuleShouldNotDependOnAccountModule() {
        // transfer consumes the account-api published language only, never the account module.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.account..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void accountModuleShouldNotDependOnTransferModule() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.account..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.transfer..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void infrastructureShouldNotDependOnTransferModule() {
        // The shared snapshot-cache contract lives in account-api, so infrastructure
        // must not compile against the transfer module (it still implements
        // user/audit-owned ports, which is by design).
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.transfer..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void accountApiShouldOnlyDependOnSharedKernel() {
        // Published language: stable contract, framework-free, no BC dependencies.
        ArchRule rule = classes()
                .that().resideInAnyPackage("com.bank.app.accountapi..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.accountapi..",
                        "com.bank.app.common..",
                        "java..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void adaptersShouldNotDependOnOtherModuleAdapters() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.account.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.transfer.adapter..",
                        "com.bank.app.user.adapter..",
                        "com.bank.app.audit.adapter..");

        rule.check(importedClasses);

        rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account.adapter..",
                        "com.bank.app.user.adapter..",
                        "com.bank.app.audit.adapter..");

        rule.check(importedClasses);
    }

    @Test
    void infrastructureShouldNotDependOnModuleAdapters() {
        // Infrastructure may implement context-owned ports, but must never depend on
        // concrete adapter classes of a bounded context (DIP). Cross-context reads go
        // through framework-free abstractions such as AuthenticatedPrincipal.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account.adapter..",
                        "com.bank.app.transfer.adapter..",
                        "com.bank.app.user.adapter..",
                        "com.bank.app.audit.adapter..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void jpaEntitiesShouldNotReferenceOtherModuleEntities() {
        // Intentional no-FK decision for BC autonomy (see V19): aggregates reference
        // each other by ID only. JPA associations across BCs would re-couple modules
        // through the persistence layer. The shared AuditableJpaEntity base is allowed.
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("JpaEntity")
                .and().resideOutsideOfPackage("com.bank.app.persistence..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account.adapter.out.persistence..",
                        "com.bank.app.transfer.adapter.out.persistence..",
                        "com.bank.app.user.adapter.out.persistence..",
                        "com.bank.app.audit.adapter.out.persistence..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void transferAdapterShouldNotUseSpringCacheDirectly() {
        // Caching at the ACL edge goes through AccountSnapshotCache (infrastructure owns backend).
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.cache..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }
}
