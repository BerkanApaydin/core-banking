package com.bank.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.math.BigDecimal;
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

    @Test
    void noProgrammaticSpringTransactionsInBoundedContexts() {
        // Bounded contexts observe the transaction boundary through
        // TransactionBoundaryPort (common, framework-free). Direct use of
        // Spring programmatic control pins adapters to Spring and hides tx
        // control inside business modules; the single Spring-backed
        // implementation lives in infrastructure (SpringTransactionBoundaryAdapter).
        // Persistence failure *types* (spring-dao) stay allowed: they are the
        // shared failure taxonomy mapped centrally by the problem handlers.
        DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass> programmaticTxControl =
                new DescribedPredicate<>("Spring programmatic transaction control") {
                    private final java.util.Set<String> controlled = java.util.Set.of(
                            "org.springframework.transaction.support.TransactionSynchronizationManager",
                            "org.springframework.transaction.support.TransactionSynchronization",
                            "org.springframework.transaction.support.TransactionTemplate",
                            "org.springframework.transaction.PlatformTransactionManager",
                            "org.springframework.transaction.TransactionDefinition");
                    @Override
                    public boolean test(com.tngtech.archunit.core.domain.JavaClass javaClass) {
                        return controlled.contains(javaClass.getName());
                    }
                };
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.account..", "com.bank.app.transfer..",
                        "com.bank.app.user..", "com.bank.app.audit..")
                .should().accessClassesThat(programmaticTxControl)
                .allowEmptyShould(false);
        rule.check(importedClasses);
    }

    @Test
    void noDirectTransactionalAnnotationInUseCases() {        // R7/P5-2: use cases declare transactions via @TransactionalUseCase /
        // @ReadOnlyUseCase markers (UseCaseTransactionAspect owns isolation +
        // timeout). A direct @Transactional would silently bypass the aspect's
        // READ_COMMITTED/timeout contract and split the tx model in two.
        // Allowed homes for @Transactional: infrastructure (IdempotencyGuard
        // REQUIRES_NEW isolation, outbox machinery) and tests.
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..application.usecase..")
                .should().beAnnotatedWith(Transactional.class)
                .allowEmptyShould(false);
        rule.check(importedClasses);
    }

    @Test
    void spendableTransferPathMustEnforceIbanChecksum() {
        // R8/P4-1: new Iban() is format-only by design (legacy rows stay
        // readable); the boundary that creates spendable transfers must enforce
        // MOD 97-10 via Iban.checked — the guard must not be forgettable.
        ArchRule rule = classes()
                .that().haveSimpleName("TransferDomainService")
                .should().callMethod(Iban.class, "checked", String.class)
                .allowEmptyShould(false);
        rule.check(importedClasses);
    }

    @Test
    void accountCreationPathMustEnforceIbanChecksum() {
        // Same invariant for the account-creation boundary: either checked()
        // or new Iban() + requireValidChecksum() (CreateAccountUseCaseImpl
        // spells it out for the seed-vs-generated branch). A future path that
        // mints spendable IBANs without either call fails here.
        DescribedPredicate<JavaMethodCall> checksumEnforcement =
                new DescribedPredicate<JavaMethodCall>("IBAN checksum enforcement") {
                    @Override
                    public boolean test(JavaMethodCall call) {
                        if (!call.getTarget().getOwner().isAssignableTo(Iban.class)) {
                            return false;
                        }
                        String name = call.getTarget().getName();
                        return "checked".equals(name) || "requireValidChecksum".equals(name);
                    }
                };
        ArchRule rule = classes()
                .that().haveSimpleName("CreateAccountUseCaseImpl")
                .should().callMethodWhere(checksumEnforcement)
                .allowEmptyShould(false);
        rule.check(importedClasses);
    }

    @Test
    void moneyInstantiatedOnlyThroughFactories() {
        // R-1: the canonical constructor cannot be private (records require a
        // public canonical constructor — javac rejects anything stronger with
        // "attempting to assign stronger access privileges"), so the
        // single-factory invariant is pinned here instead: production code
        // builds Money only via exact/of/ofTransferAmount/rounded, whose
        // bodies live inside Money itself.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app..")
                .and().haveSimpleNameNotContaining("Money")
                .should().callConstructor(Money.class, BigDecimal.class, Currency.class)
                .allowEmptyShould(false);
        rule.check(importedClasses);
    }
}
