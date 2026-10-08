package com.bank.app.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
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
}
