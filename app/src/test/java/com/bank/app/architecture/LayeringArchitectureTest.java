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
                .allowEmptyShould(true);

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
    void applicationLayerShouldNotDependOnSpringSecurity() {
        // Application services throw common AuthorizationException, never Spring's
        // AccessDeniedException. Security framework stays in adapters/infrastructure.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.security..")
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
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void loginUseCaseShouldBeTransactional() {
        // Login's only relational access is reads (credential check + user lookup);
        // login-attempt state lives in Redis, outside the DB transaction — so it
        // must run read-only, not in a read-write transaction.
        ArchRule rule = classes()
                .that().haveSimpleName("LoginUserUseCaseImpl")
                .should().beAnnotatedWith(ReadOnlyUseCase.class)
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }
}
