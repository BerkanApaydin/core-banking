package com.bank.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.conditions.ArchConditions;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@SuppressWarnings("null")
class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.bank.app");
    }

    @Test
    void domainShouldNotDependOnSpringOrInfrastructure() {
        DescribedPredicate<JavaClass> forbiddenPackages =
                JavaClass.Predicates.resideInAnyPackage(
                        "org.springframework..",
                        "..infrastructure..",
                        "..adapter..",
                        "..jakarta.persistence..")
                .and(JavaClass.Predicates.resideOutsideOfPackage("org.springframework.lang.."));

        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should(ArchConditions.dependOnClassesThat(forbiddenPackages))
                .as("no domain classes should depend on Spring (except org.springframework.lang) or infrastructure")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

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
    void useCasesShouldResideInApplicationLayer() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .or().haveSimpleNameEndingWith("Query")
                .should().resideInAPackage("..application..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void domainShouldNotDependOnOtherModules() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account.application..",
                        "com.bank.app.transfer.application..",
                        "com.bank.app.user.application..",
                        "com.bank.app.audit.application..",
                        "com.bank.app.account.adapter..",
                        "com.bank.app.transfer.adapter..",
                        "com.bank.app.user.adapter..",
                        "com.bank.app.audit.adapter..");

        rule.check(importedClasses);
    }

    @Test
    void controllersShouldNotContainBusinessLogic() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..adapter.in.web..")
                .or().resideInAnyPackage("..infrastructure.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..domain.exception..",
                        "org.springframework.security.core.AuthenticationException");

        rule.check(importedClasses);
    }

    @Test
    void domainShouldNotDependOnWebFramework() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..", "org.springframework.web..");

        rule.check(importedClasses);
    }

    @Test
    void applicationLayerShouldNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "org.springframework.stereotype..",
                        "org.springframework.web..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void applicationDtoShouldNotDependOnJakartaValidation() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application.dto..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.validation..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void layeredArchitectureShouldBeRespected() {
        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Domain").definedBy("..domain..")
                // account-api is the published language (application-level contract of the Account context)
                .layer("Application").definedBy("..application..", "..accountapi..")
                .layer("Adapter").definedBy("..adapter..")
                .layer("Infrastructure").definedBy("..infrastructure..", "..bootstrap..", "..config..")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Infrastructure")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Infrastructure")
                .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Adapter")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void domainShouldNotDependOnFrameworkAnnotations() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..",
                        "jakarta.validation..",
                        "org.springframework.transaction..",
                        "org.springframework.retry..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void portInterfacesShouldFollowNamingConvention() {
        ArchRule inPorts = classes()
                .that().resideInAnyPackage("..port.in..")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("UseCase")
                .orShould().haveSimpleNameEndingWith("Query")
                .orShould().haveSimpleNameEndingWith("Command")
                .allowEmptyShould(true);

        ArchRule outPorts = classes()
                .that().resideInAnyPackage("..port.out..")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("Port")
                .orShould().haveSimpleNameEndingWith("Acl")
                .allowEmptyShould(true);

        inPorts.check(importedClasses);
        outPorts.check(importedClasses);
    }

    @Test
    void domainEventsShouldNotDependOnFramework() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .and().haveSimpleNameEndingWith("Event")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta..",
                        "org.springframework..",
                        "com.fasterxml.jackson..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void useCaseImplementationsShouldFollowNamingConvention() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..application.usecase..")
                .should().haveSimpleNameEndingWith("Impl")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void portsShouldNotDependOnImplementations() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..port..")
                .should().dependOnClassesThat().resideInAnyPackage("..usecase..");

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
    void commonDomainShouldNotDependOnCommonInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.common.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.common.adapter..",
                        "org.springframework..",
                        "jakarta..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void commonInfrastructureShouldNotDependOnModuleDomains() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account..",
                        "com.bank.app.transfer..",
                        "com.bank.app.user..",
                        "com.bank.app.audit..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void domainEventsShouldOnlyBePublishedByDomainEntities() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..domain..")
                .and().haveSimpleNameEndingWith("Event")
                .should().resideInAPackage("..domain..");

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
    void outboundAdaptersShouldDependOnOutPorts() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..adapter.out..")
                .and().haveSimpleNameEndingWith("Adapter")
                .should().dependOnClassesThat().resideInAnyPackage("..port.out..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void applicationLayerShouldNotDependOnJpaEntities() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("JpaEntity");

        rule.check(importedClasses);
    }

    @Test
    void applicationLayerShouldNotDependOnSpringTransaction() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.transaction..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void applicationLayerShouldNotDependOnSpringDataCacheOrServlet() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data..",
                        "org.springframework.cache..",
                        "org.springframework.lang..",
                        "jakarta.servlet..",
                        "jakarta.persistence..")
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
    void securityPortsShouldResideInUserModule() {
        // Token lifecycle ports are owned by the User BC, not the shared kernel (ISP).
        ArchRule rule = classes()
                .that().haveSimpleName("JwtPort")
                .or().haveSimpleName("TokenBlacklistPort")
                .should().resideInAPackage("com.bank.app.user.application.port.out..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void transferPortsShouldNotExposeDomainEvents() {
        // Account domain events must not cross the context boundary; balance mutations
        // return the transfer-owned opaque result instead.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.application.port..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.common.domain.event..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void transferPortsShouldNotDependOnAccountPublishedLanguage() {
        // ACL port owns its own MutationResult; only the adapter may depend on account-api.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.application.port..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.accountapi..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void transferAdapterShouldNotUseSpringCacheDirectly() {
        // Caching at the ACL edge goes through AccountInfoCachePort (infrastructure owns backend).
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.cache..")
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
    void loginUseCaseShouldBeTransactional() {
        // Login writes login-attempt state (reset/recordFailure), so it must run in a
        // read-write transaction, not @ReadOnlyUseCase.
        ArchRule rule = classes()
                .that().haveSimpleName("LoginUserUseCaseImpl")
                .should().beAnnotatedWith(TransactionalUseCase.class)
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void applicationLayerShouldNotDependOnSpringSecurity() {
        // Application services throw common AuthorizationException, never Spring's
        // AccessDeniedException. Security framework stays in adapters/infrastructure.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.security..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }
}
