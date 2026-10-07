package com.bank.app.architecture;

import com.bank.app.common.domain.exception.BusinessException;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wire codes must be explicit literals, not string surgery on class names.
 *
 * <p>{@code BusinessException.getErrorCode} derives from message keys and,
 * as a last resort, camelCase class names — so renaming a class silently
 * changes the API error code. Every concrete exception therefore declares its
 * own literal, except the instance-variant codes below whose value
 * legitimately differs per throw site (collapsing them would merge distinct
 * client-visible failures; each documents why inline).
 */
@SuppressWarnings("null")
class ExceptionCodeArchitectureTest extends ArchitectureTest {

    private static final Set<String> INSTANCE_VARIANT_CODES = Set.of(
            "com.bank.app.transfer.domain.exception.TransferNotCancellableException",
            "com.bank.app.account.domain.exception.AccountNotFoundException",
            "com.bank.app.accountapi.AccountNotFoundException",
            "com.bank.app.common.domain.exception.ConcurrentRequestException");

    @Test
    void businessExceptionsShouldDeclareExplicitErrorCode() {
        List<String> violators = importedClasses.stream()
                // Anonymous test doubles ride along inside the common test-jar
                // on this classpath; only named production types are ruled.
                .filter(c -> !c.isInterface() && !c.getModifiers().contains(JavaModifier.ABSTRACT)
                        && !c.isAnonymousClass())
                .filter(c -> c.isAssignableTo(BusinessException.class)
                        && !c.getName().equals(BusinessException.class.getName()))
                .filter(c -> !INSTANCE_VARIANT_CODES.contains(c.getName()))
                .filter(c -> c.getMethods().stream()
                        .noneMatch(m -> m.getName().equals("getErrorCode") && m.getOwner().equals(c)))
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertThat(violators)
                .as("concrete BusinessException subclasses must declare their own getErrorCode() literal "
                        + "(or be listed in INSTANCE_VARIANT_CODES with an inline justification)")
                .isEmpty();
    }
}
