package com.bank.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Long term #4 (Spring Modulith readiness): seals the modularity contract
 * without adding the Modulith dependency. When Modulith is added, this test
 * is replaced with {@code ApplicationModules.verify()}; the package rules stay.
 */
class ApplicationModularityTest extends ArchitectureTest {

    @Test
    void boundedContextsShouldExistAsTopLevelModules() {
        JavaClasses classes = ArchitectureTest.importedClasses;
        assertThat(classes).isNotEmpty();
        for (String bc : new String[]{
                "com.bank.app.account", "com.bank.app.transfer",
                "com.bank.app.user", "com.bank.app.audit",
                "com.bank.app.common", "com.bank.app.infrastructure"}) {
            long count = classes.stream()
                    .filter(c -> c.getPackageName().startsWith(bc))
                    .count();
            assertThat(count)
                    .as("bounded context %s must contain classes (Modulith readiness)", bc)
                    .isPositive();
        }
    }

    @Test
    void applicationModulesShouldNotBeEmpty() {
        assertThat(ArchitectureTest.importedClasses).isNotEmpty();
    }
}
