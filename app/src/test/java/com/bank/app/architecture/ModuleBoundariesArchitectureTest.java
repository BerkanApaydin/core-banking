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
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void transferModuleShouldNotDependOnAccountModule() {
        // transfer consumes the account-api published language only, never the account module.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.account..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void accountModuleShouldNotDependOnTransferModule() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.account..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.transfer..")
                .allowEmptyShould(false);

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
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void infrastructureShouldNotDependOnAccountModule() {
        // Compile scope for account was removed on purpose (test-scope only for
        // IT slices); cross-context reads go via the account-api published
        // language. Pin it: any production import of com.bank.app.account..
        // fails the build (ArchitectureTest imports production classes only,
        // so the test-scope dependency is invisible here).
        // NOTE: "com.bank.app.account.." does not match "com.bank.app.accountapi.."
        // (ArchUnit matches package segments), so the legitimate account-api
        // usage in infrastructure keeps passing — same as the transfer/account
        // rules above coexisting with account-api consumption.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.account..")
                .allowEmptyShould(false);

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
                .allowEmptyShould(false);

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

        rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.user.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account.adapter..",
                        "com.bank.app.transfer.adapter..",
                        "com.bank.app.audit.adapter..");

        rule.check(importedClasses);

        rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.audit.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account.adapter..",
                        "com.bank.app.transfer.adapter..",
                        "com.bank.app.user.adapter..");

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
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void jpaEntitiesShouldNotReferenceOtherModuleEntities() {
        // Aggregates reference each other by scalar ID in Java, while V24 restores
        // PostgreSQL foreign keys for integrity in this single-database monolith.
        // JPA associations across BCs would still couple the Java modules.
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("JpaEntity")
                .and().resideOutsideOfPackage("com.bank.app.persistence..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account.adapter.out.persistence..",
                        "com.bank.app.transfer.adapter.out.persistence..",
                        "com.bank.app.user.adapter.out.persistence..",
                        "com.bank.app.audit.adapter.out.persistence..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void transferAdapterShouldNotUseSpringCacheDirectly() {
        // Caching at the ACL edge goes through AccountSnapshotCache (infrastructure owns backend).
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.cache..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }
}
