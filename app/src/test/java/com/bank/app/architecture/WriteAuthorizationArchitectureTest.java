package com.bank.app.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the inner authorization layer (M-6).
 *
 * <p>{@code @EnableMethodSecurity} is intentionally off — role checks live in
 * the application layer — so a refactor that drops the explicit
 * authorization call inside a money-moving use case would otherwise go
 * unnoticed. These rules pin the dependency: money/account mutations must
 * reach their dedicated authorization service.
 */
@SuppressWarnings("null")
class WriteAuthorizationArchitectureTest extends ArchitectureTest {

    @Test
    void placeTransferMustReachTransferAuthorization() {
        assertScopeNotEmpty("PlaceTransferUseCaseImpl must exist",
                importedClasses.stream().anyMatch(c -> c.getSimpleName().equals("PlaceTransferUseCaseImpl")));
        ArchRule rule = classes()
                .that().haveSimpleName("PlaceTransferUseCaseImpl")
                .should().dependOnClassesThat().haveSimpleName("TransferAuthorizationService")
                .because("placing a transfer must authorize the sender; dropping the call must fail the build");
        rule.check(importedClasses);
    }

    @Test
    void cancelTransferMustReachTransferAuthorization() {
        assertScopeNotEmpty("CancelTransferUseCaseImpl must exist",
                importedClasses.stream().anyMatch(c -> c.getSimpleName().equals("CancelTransferUseCaseImpl")));
        ArchRule rule = classes()
                .that().haveSimpleName("CancelTransferUseCaseImpl")
                .should().dependOnClassesThat().haveSimpleName("TransferAuthorizationService")
                .because("cancelling a transfer must authorize the requester; dropping the call must fail the build");
        rule.check(importedClasses);
    }

    @Test
    void createAccountMustReachAccountAuthorization() {
        assertScopeNotEmpty("CreateAccountUseCaseImpl must exist",
                importedClasses.stream().anyMatch(c -> c.getSimpleName().equals("CreateAccountUseCaseImpl")));
        ArchRule rule = classes()
                .that().haveSimpleName("CreateAccountUseCaseImpl")
                .should().dependOnClassesThat().haveSimpleName("AccountAuthorizationService")
                .because("creating an account must authorize the owner; dropping the call must fail the build");
        rule.check(importedClasses);
    }

    @Test
    void suspendAccountMustEnforceAdminRole() {
        long count = importedClasses.stream()
                .filter(c -> c.getSimpleName().equals("SuspendAccountUseCaseImpl"))
                .count();
        assertThat(count).as("SuspendAccountUseCaseImpl must exist — rule would be vacuous").isPositive();
        JavaClass suspend = importedClasses.stream()
                .filter(c -> c.getSimpleName().equals("SuspendAccountUseCaseImpl"))
                .findFirst().orElseThrow();
        assertThat(suspend.getDirectDependenciesFromSelf())
                .as("SuspendAccountUseCaseImpl must reference UserContextService/UserContext for the ADMIN check")
                .anyMatch(d -> d.getTargetClass().getSimpleName().contains("UserContext"));
    }

    @Test
    void placeTransferMustCallTransferAuthorization() {
        // dependOn above pins the wiring; this pins the invocation: an unused
        // import would satisfy dependOn while dropping the authorize() call.
        assertScopeNotEmpty("PlaceTransferUseCaseImpl must exist",
                importedClasses.stream().anyMatch(c -> c.getSimpleName().equals("PlaceTransferUseCaseImpl")));
        ArchRule rule = classes()
                .that().haveSimpleName("PlaceTransferUseCaseImpl")
                .should(callAuthorizationService(
                        "com.bank.app.transfer.application.service.TransferAuthorizationService"))
                .because("placing a transfer must invoke the authorization service; an unused import must fail the build");
        rule.check(importedClasses);
    }

    @Test
    void cancelTransferMustCallTransferAuthorization() {
        assertScopeNotEmpty("CancelTransferUseCaseImpl must exist",
                importedClasses.stream().anyMatch(c -> c.getSimpleName().equals("CancelTransferUseCaseImpl")));
        ArchRule rule = classes()
                .that().haveSimpleName("CancelTransferUseCaseImpl")
                .should(callAuthorizationService(
                        "com.bank.app.transfer.application.service.TransferAuthorizationService"))
                .because("cancelling a transfer must invoke the authorization service; an unused import must fail the build");
        rule.check(importedClasses);
    }

    @Test
    void createAccountMustCallAccountAuthorization() {
        assertScopeNotEmpty("CreateAccountUseCaseImpl must exist",
                importedClasses.stream().anyMatch(c -> c.getSimpleName().equals("CreateAccountUseCaseImpl")));
        ArchRule rule = classes()
                .that().haveSimpleName("CreateAccountUseCaseImpl")
                .should(callAuthorizationService(
                        "com.bank.app.account.application.service.AccountAuthorizationService"))
                .because("creating an account must invoke the authorization service; an unused import must fail the build");
        rule.check(importedClasses);
    }

    /**
     * Invocation check with one-hop same-class helper tolerance (same pattern as
     * {@code CacheInvalidationArchitectureTest}): the use case may call the
     * authorization service directly or through a private same-class helper
     * (e.g. {@code authorize(...)}), so a future extract-method refactor that
     * keeps the authorization does not fail the build — but dropping it does.
     */
    private static ArchCondition<JavaClass> callAuthorizationService(String serviceClassName) {
        return new ArchCondition<>("call " + serviceClassName) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!callsService(javaClass, serviceClassName)) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName()
                                    + " must call " + serviceClassName
                                    + " (directly or via a same-class helper)"));
                }
            }
        };
    }

    private static boolean callsService(JavaClass javaClass, String serviceClassName) {
        if (javaClass.getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().getName().equals(serviceClassName))) {
            return true;
        }
        for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
            if (!call.getTargetOwner().equals(javaClass)) {
                continue;
            }
            String helperName = call.getName();
            boolean helperCallsService = javaClass.getMethods().stream()
                    .filter(helper -> helper.getName().equals(helperName))
                    .anyMatch(helper -> helper.getMethodCallsFromSelf().stream()
                            .anyMatch(c -> c.getTargetOwner().getName().equals(serviceClassName)));
            if (helperCallsService) {
                return true;
            }
        }
        return false;
    }
}
