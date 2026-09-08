package com.bank.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;

/**
 * Shared ArchUnit bootstrap: imports production classes once per subclass.
 * Concrete rule groups live in the *ArchitectureTest classes in this package.
 */
abstract class ArchitectureTest {

    protected static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.bank.app");
    }
}
