package com.bank.app.architecture;

import com.bank.app.common.application.aspect.UseCaseAspectOrders;
import com.bank.app.common.application.port.in.RequiresNewUseCase;
import com.bank.app.infrastructure.adapter.in.idempotency.IdempotencyAspect;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Field;
import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AV-1..AV-5 regression: module-boundary violations found by static review.
 * Each rule pins one fix so a rename or a new violation turns red instead of
 * silently reopening the coupling.
 */
@SuppressWarnings("null")
class BoundaryArchitectureTest extends ArchitectureTest {

    @Test
    void applicationLayerShouldNotDependOnSpringDao() {
        // AV-4: Spring DAO translation lives in adapters (e.g.
        // UserPersistenceAdapter); the application layer stays framework-free
        // for persistence failures (was: RegisterUserUseCaseImpl).
        noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.dao..")
                .allowEmptyShould(false)
                .check(importedClasses);
    }

    @Test
    void securityFilterShouldNotDependOnUserDomain() {
        // AV-2: JwtAuthenticationFilter reads the narrow token-version
        // projection (LoadUserPort.findTokenVersionById), never the User
        // aggregate.
        noClasses()
                .that().resideInAPackage("..infrastructure.adapter.in.security..")
                .should().dependOnClassesThat().resideInAPackage("..user.domain..")
                .allowEmptyShould(false)
                .check(importedClasses);
    }

    @Test
    void auditDomainFromInfrastructureOnlyThroughEventBridge() {
        // AV-3: the common->audit translation (AuditAction/AuditLog) is pinned
        // to the single bridge adapter; no other infrastructure class may
        // reach into audit.domain.
        var bridge = "AuditEventPublisherAdapter";
        var auditDomain = importedClasses.stream()
                .filter(c -> c.getPackageName().contains("infrastructure"))
                .filter(c -> !c.getSimpleName().equals(bridge))
                .toList();
        assertScopeNotEmpty("infrastructure classes", !auditDomain.isEmpty());
        for (var clazz : auditDomain) {
            boolean touchesAuditDomain = clazz.getDirectDependenciesFromSelf().stream()
                    .anyMatch(dep -> dep.getTargetClass().getPackageName().contains("audit.domain"));
            if (touchesAuditDomain) {
                throw new AssertionError(
                        "Only AuditEventPublisherAdapter may depend on audit.domain, found: "
                        + clazz.getFullName());
            }
        }
    }

    @Test
    void auditRequiresNewSemanticsMustBeAnnotated() {
        // AV-1: REQUIRES_NEW is declared in the owning module via marker
        // annotation, not hidden in an infrastructure package literal.
        classes()
                .that().haveSimpleName("AuditLoggerUseCaseImpl")
                .should().beAnnotatedWith(RequiresNewUseCase.class)
                .allowEmptyShould(false)
                .check(importedClasses);
    }

    @Test
    void bcConfigShouldNotInstantiateOtherLayerAdapters() {
        // AV: transfer.config wires only application beans; adapter
        // construction lives in TransferAccountAclConfiguration next to the
        // adapters.
        noClasses()
                .that().resideInAPackage("..transfer.config..")
                .should().dependOnClassesThat().resideInAPackage("..transfer.adapter.out..")
                .allowEmptyShould(false)
                .check(importedClasses);
    }

    @Test
    void aspectsShouldUseSharedOrderConstants() {
        // Low: @Order literals drift from UseCaseAspectOrders (was:
        // IdempotencyAspect @Order(1) vs documented HIGHEST_PRECEDENCE + 1).
        // NOTE: ArchUnit cannot pin this — `public static final int`
        // constants are inlined at compile time, so no bytecode edge exists.
        // A runtime @Order read is the only honest check.
        assertThat(Arrays.stream(UseCaseAspectOrders.class.getDeclaredFields())
                        .map(Field::getName))
                .contains("IDEMPOTENCY", "TRANSFER_RETRY", "USE_CASE_TRANSACTION");
        Order order = IdempotencyAspect.class.getAnnotation(Order.class);
        assertThat(order)
                .as("IdempotencyAspect must carry @Order")
                .isNotNull();
        assertThat(order.value())
                .as("@Order must equal UseCaseAspectOrders.IDEMPOTENCY, not a literal")
                .isEqualTo(UseCaseAspectOrders.IDEMPOTENCY);
    }
}
