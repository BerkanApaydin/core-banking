package com.bank.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared ArchUnit bootstrap: imports production classes once per test run.
 * The result is cached in a static field guarded against re-import, so the
 * N subclasses share a single ClassFileImporter pass instead of paying
 * ~1.4-4.3s per subclass (Q-3).
 *
 * <p>Two exclusions keep the import production-only: the predefined
 * {@code DO_NOT_INCLUDE_TESTS} (test sources) plus test jars — {@code common}
 * ships a test-jar that lands on this classpath, and without the second filter
 * test classes (e.g. {@code AuditEventTest}) would be checked against
 * production-only rules like the time-strategy bans.
 *
 * <p>Concrete rule groups live in the *ArchitectureTest classes in this package.
 */
abstract class ArchitectureTest {

    protected static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        if (importedClasses == null) {
            importedClasses = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .withImportOption(location -> !location.toString().contains("-tests.jar"))
                    .importPackages("com.bank.app");
            // K-1/T-1 guard: a vacuous import (empty classes) would let every
            // allowEmptyShould rule pass silently. Fail fast instead.
            assertThat(importedClasses)
                    .as("ArchUnit import must not be empty — rules would be vacuous")
                    .isNotEmpty();
        }
    }

    /**
     * K-1 precondition for rules that match by package/name: asserts the rule
     * scope is non-empty before checking, so a package rename can never turn
     * a protection rule silently green.
     */
    protected static void assertScopeNotEmpty(String description, boolean nonEmpty) {
        assertThat(nonEmpty)
                .as("%s — rule scope must not be empty", description)
                .isTrue();
    }
}
