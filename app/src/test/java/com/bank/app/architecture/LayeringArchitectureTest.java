package com.bank.app.architecture;

import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@SuppressWarnings("null")
class LayeringArchitectureTest extends ArchitectureTest {

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
                .allowEmptyShould(false);

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
                .allowEmptyShould(false);

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
                .allowEmptyShould(false);

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
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void applicationLayerShouldNotDependOnSpringSecurity() {
        // Application services throw common AuthorizationException, never Spring's
        // AccessDeniedException. Security framework stays in adapters/infrastructure.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.security..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void applicationDtoShouldNotDependOnJakartaValidation() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application.dto..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.validation..")
                .allowEmptyShould(false);

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
    void useCasesShouldResideInApplicationLayer() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .or().haveSimpleNameEndingWith("Query")
                .should().resideInAPackage("..application..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void loginShouldUseTheIdentityReturnedByAuthentication() {
        noClasses().that().haveSimpleName("LoginUserUseCaseImpl")
                .should().dependOnClassesThat().haveSimpleName("LoadUserPort")
                .because("reloading a user both adds SQL and disconnects token claims from the credential check")
                .check(importedClasses);
    }

    @Test
    void loginUseCaseShouldNotHoldTransactionAcrossRedisCalls() {
        // Each credential lookup owns a short read-only adapter transaction.
        // The login use case also calls Redis and must not hold a DB transaction.
        ArchRule rule = noClasses()
                .that().haveSimpleName("LoginUserUseCaseImpl")
                .should().beAnnotatedWith(ReadOnlyUseCase.class)
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void adaptersShouldNotDependOnInfrastructure() {
        // Pinned clean state: bounded-context adapters program against ports and
        // the shared kernel only. Infrastructure implements context-owned ports;
        // the dependency arrow never reverses (DIP). The layered-architecture
        // rule above leaves the Adapter<->Infrastructure edge loose for the
        // composition root, so this explicit rule fails the build on the first
        // reintroduction of an adapter->infrastructure import.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "com.bank.app.account.adapter..",
                        "com.bank.app.transfer.adapter..",
                        "com.bank.app.user.adapter..",
                        "com.bank.app.audit.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.infrastructure..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }
}
