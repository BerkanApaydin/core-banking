package com.bank.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Cross-cutting coding rules: DI hygiene + no blocking sleeps on request threads. */
class CodingRulesArchitectureTest extends ArchitectureTest {

    /**
     * Matches zero-arg calls only, so the approved {@code LocalDateTime.now(clock)}
     * pattern keeps passing while bare {@code now()} fails.
     */
    private static DescribedPredicate<JavaMethodCall> zeroArgCall(
            Class<?> owner, String methodName, String reason) {
        return new DescribedPredicate<JavaMethodCall>(
                "zero-arg " + owner.getSimpleName() + "." + methodName + " (" + reason + ")") {
            @Override
            public boolean test(JavaMethodCall call) {
                return call.getTarget().getName().equals(methodName)
                        && call.getTarget().getRawParameterTypes().isEmpty()
                        && call.getTarget().getOwner().isAssignableTo(owner);
            }
        };
    }

    @Test
    void noFieldInjection() {
        ArchRule rule = noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage("com.bank.app..")
                .should().beAnnotatedWith(Autowired.class)
                .allowEmptyShould(false);
        rule.check(importedClasses);
    }

    @Test
    void noThreadSleepInMainCode() {
        // Blocking sleep belongs in worker-pool retry policies, not request threads.
        // Allowed: transfer retry aspect + idempotency executor (jittered, bounded).
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app..")
                .and().haveSimpleNameNotContaining("RetryAspect")
                .and().haveSimpleNameNotContaining("IdempotentRetryExecutor")
                .should().callMethod(Thread.class, "sleep", long.class)
                .allowEmptyShould(false);
        rule.check(importedClasses);
    }

    @Test
    void noBareNowInMainCode() {
        // docs/decisions/time-strategy.md: every LocalDateTime is UTC, so the only
        // legal "now" takes an explicit clock. Bare now() reads the server zone.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app..")
                .should().callMethodWhere(zeroArgCall(LocalDateTime.class, "now",
                        "use LocalDateTime.now(clock) with the injected UTC clock"))
                .allowEmptyShould(false);
        rule.check(importedClasses);
    }

    @Test
    void noSystemDefaultZoneInMainCode() {
        // System zone makes laptops, CI and prod disagree (UTC vs UTC+3 broke five
        // scheduler/metric tests). Production clocks come from ClockProviderPort.
        ArchRule clockRule = noClasses()
                .that().resideInAnyPackage("com.bank.app..")
                .should().callMethodWhere(zeroArgCall(Clock.class, "systemDefaultZone",
                        "inject ClockProviderPort (UTC) instead"))
                .allowEmptyShould(false);
        clockRule.check(importedClasses);

        ArchRule zoneRule = noClasses()
                .that().resideInAnyPackage("com.bank.app..")
                .should().callMethodWhere(zeroArgCall(ZoneId.class, "systemDefault",
                        "use ZoneOffset.UTC instead"))
                .allowEmptyShould(false);
        zoneRule.check(importedClasses);
    }
}
