package com.bank.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.bank.app");
    }

    @Test
    void domainShouldNotDependOnSpringOrInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "..infrastructure..",
                        "..jakarta.persistence..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void accountApplicationShouldNotDependOnTransferInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.account.application..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.transfer.infrastructure..");

        rule.check(importedClasses);
    }

    @Test
    void transferApplicationShouldNotDependOnAccountInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.application..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.account.infrastructure..");

        rule.check(importedClasses);
    }

    @Test
    void useCasesShouldResideInApplicationLayer() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .should().resideInAPackage("..application..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void domainShouldNotDependOnOtherModules() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account.application..",
                        "com.bank.app.transfer.application..",
                        "com.bank.app.user.application..",
                        "com.bank.app.audit.application..",
                        "com.bank.app.account.infrastructure..",
                        "com.bank.app.transfer.infrastructure..",
                        "com.bank.app.user.infrastructure..",
                        "com.bank.app.audit.infrastructure..");

        rule.check(importedClasses);
    }

    @Test
    void controllersShouldNotContainBusinessLogic() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..infrastructure.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..domain.exception..",
                        "org.springframework.security.core.AuthenticationException");

        rule.check(importedClasses);
    }

    @Test
    void domainShouldNotDependOnWebFramework() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..", "org.springframework.web..");

        rule.check(importedClasses);
    }
}
