package com.bank.app.architecture;

import com.bank.app.common.domain.BaseAggregateRoot;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.User;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.conditions.ArchConditions;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("null")
class DomainPurityArchitectureTest extends ArchitectureTest {

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
                .allowEmptyShould(false);

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
    void domainShouldNotDependOnWebFramework() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..", "org.springframework.web..");

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
                .allowEmptyShould(false);

        rule.check(importedClasses);
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
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void domainEventsShouldOnlyBeRegisteredByDomainEntities() {
        DescribedPredicate<JavaClass> callsRegisterEvent = callRegisterEvent();
        // Non-vacuous scope: production aggregates actually call
        // BaseAggregateRoot.registerEvent; a rename must fail loudly instead
        // of turning the rule green.
        long callers = importedClasses.stream().filter(callsRegisterEvent).count();
        assertThat(callers)
                .as("BaseAggregateRoot.registerEvent must have production callers — rule would be vacuous")
                .isPositive();

        ArchRule rule = classes()
                .that(callsRegisterEvent)
                .should().resideInAnyPackage("..domain..")
                .allowEmptyShould(false)
                .because("aggregates outside the domain must not register domain events directly");
        rule.check(importedClasses);
    }

    private static DescribedPredicate<JavaClass> callRegisterEvent() {
        return new DescribedPredicate<>("call BaseAggregateRoot.registerEvent") {
            @Override
            public boolean test(JavaClass javaClass) {
                return javaClass.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTargetOwner()
                                        .isAssignableTo(BaseAggregateRoot.class)
                                && call.getName().equals("registerEvent"));
            }
        };
    }

    @Test
    void commonDomainShouldNotDependOnCommonInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.common.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.common.adapter..",
                        "org.springframework..",
                        "jakarta..")
                .allowEmptyShould(false);

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
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void roleChangesRequireTokenVersioning() {
        // Role is embedded in issued JWTs: no production code may call
        // User.assignRole until token versioning exists — outstanding tokens
        // would otherwise keep the old role until expiry. (Test sources are
        // excluded from the import, so UserTest may still exercise it.)
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app..")
                .and().resideOutsideOfPackage("com.bank.app.user.domain..")
                .should().callMethod(User.class, "assignRole", Role.class)
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }
}
