package com.bank.app.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards authorization at the boundary (G-1) and the vacuous-rule trap (T-1).
 *
 * <p>G-1: admin-only use cases ({@code SuspendAccountUseCase},
 * {@code GetAuditLogsQuery}) must never be reachable from non-admin web
 * controllers. A future public endpoint that accidentally wires an admin use
 * case fails the build instead of silently opening an admin operation to any
 * authenticated user. (Defense in depth: {@code SecurityConfig} additionally
 * enforces {@code hasRole("ADMIN")} on {@code /api/v1/admin/**}; the use-case
 * role check stays as the inner layer.)
 *
 * <p>T-1 net: several ArchUnit rules use {@code allowEmptyShould(true)}, which
 * passes vacuously when a package rename removes every match. The sanity test
 * below pins the key packages and the admin use-case types as non-empty, so
 * such a rename fails loudly here instead of turning every rule green.
 */
@SuppressWarnings("null")
class AdminAuthorizationArchitectureTest extends ArchitectureTest {

    @Test
    void architectureModelMustContainKeyPackages() {
        assertThat(importedClasses).as("ArchUnit model must not be empty").isNotEmpty();
        assertThat(importedClasses.stream()
                .filter(c -> c.getPackageName().contains(".application."))
                .count()).as("application layer must not be empty — rules would be vacuous").isPositive();
        assertThat(importedClasses.stream()
                .filter(c -> c.getPackageName().contains(".adapter.in.web."))
                .count()).as("web adapter layer must not be empty — rules would be vacuous").isPositive();
        assertThat(importedClasses.stream()
                .filter(c -> c.getPackageName().contains(".domain."))
                .count()).as("domain layer must not be empty — rules would be vacuous").isPositive();
    }

    @Test
    void suspendAndAuditQueryTypesMustExist() {
        long adminTypes = importedClasses.stream()
                .map(JavaClass::getSimpleName)
                .filter(n -> n.equals("SuspendAccountUseCase") || n.equals("GetAuditLogsQuery"))
                .count();
        assertThat(adminTypes).as("admin use-case types must exist — rule would be vacuous").isEqualTo(2L);
    }

    @Test
    void nonAdminControllersMustNotReachSuspendAccountUseCase() {
        assertScopeNotEmpty("non-admin web controllers",
                importedClasses.stream().anyMatch(c -> c.getPackageName().contains(".adapter.in.web.")
                        && !c.getSimpleName().contains("Admin")));
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..adapter.in.web..")
                .and().haveSimpleNameNotContaining("Admin")
                .should().dependOnClassesThat().haveSimpleName("SuspendAccountUseCase")
                .because("suspending accounts is admin-only; non-admin endpoints must not wire it");
        rule.allowEmptyShould(false);
        rule.check(importedClasses);
    }

    @Test
    void nonAdminControllersMustNotReachAuditQuery() {
        assertScopeNotEmpty("non-admin web controllers",
                importedClasses.stream().anyMatch(c -> c.getPackageName().contains(".adapter.in.web.")
                        && !c.getSimpleName().contains("Admin")));
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..adapter.in.web..")
                .and().haveSimpleNameNotContaining("Admin")
                .should().dependOnClassesThat().haveSimpleName("GetAuditLogsQuery")
                .because("reading audit logs is admin-only; non-admin endpoints must not wire it");
        rule.allowEmptyShould(false);
        rule.check(importedClasses);
    }
}
