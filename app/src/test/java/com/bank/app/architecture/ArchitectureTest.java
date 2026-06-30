package com.bank.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.conditions.ArchConditions;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@SuppressWarnings("null")
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
        DescribedPredicate<JavaClass> forbiddenPackages =
                JavaClass.Predicates.resideInAnyPackage(
                        "org.springframework..",
                        "..infrastructure..",
                        "..adapter..",
                        "..jakarta.persistence..")
                .and(JavaClass.Predicates.resideOutsideOfPackage("org.springframework.lang.."));

        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should(ArchConditions.dependOnClassesThat(forbiddenPackages))
                .as("no domain classes should depend on Spring (except org.springframework.lang) or infrastructure")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void accountApplicationShouldNotDependOnTransferAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.account.application..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.transfer.adapter..");

        rule.check(importedClasses);
    }

    @Test
    void transferApplicationShouldNotDependOnAccountAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.application..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.account.adapter..");

        rule.check(importedClasses);
    }

    @Test
    void useCasesShouldResideInApplicationLayer() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .or().haveSimpleNameEndingWith("Query")
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
                        "com.bank.app.account.adapter..",
                        "com.bank.app.transfer.adapter..",
                        "com.bank.app.user.adapter..",
                        "com.bank.app.audit.adapter..");

        rule.check(importedClasses);
    }

    @Test
    void controllersShouldNotContainBusinessLogic() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..adapter.in.web..")
                .or().resideInAnyPackage("..infrastructure.web..")
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

    @Test
    void applicationLayerShouldNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "org.springframework.stereotype..",
                        "org.springframework.web..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void applicationDtoShouldNotDependOnJakartaValidation() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application.dto..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.validation..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void layeredArchitectureShouldBeRespected() {
        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Domain").definedBy("..domain..")
                .layer("Application").definedBy("..application..")
                .layer("Adapter").definedBy("..adapter..")
                .layer("Infrastructure").definedBy("..infrastructure..", "..bootstrap..")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Infrastructure")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Infrastructure")
                .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Adapter")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void domainShouldNotDependOnFrameworkAnnotations() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..",
                        "jakarta.validation..",
                        "org.springframework.transaction..",
                        "org.springframework.retry..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void portInterfacesShouldFollowNamingConvention() {
        ArchRule inPorts = classes()
                .that().resideInAnyPackage("..port.in..")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("UseCase")
                .orShould().haveSimpleNameEndingWith("Query")
                .orShould().haveSimpleNameEndingWith("Command")
                .allowEmptyShould(true);

        ArchRule outPorts = classes()
                .that().resideInAnyPackage("..port.out..")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("Port")
                .orShould().haveSimpleNameEndingWith("Acl")
                .allowEmptyShould(true);

        inPorts.check(importedClasses);
        outPorts.check(importedClasses);
    }

    @Test
    void domainEventsShouldNotDependOnFramework() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .and().haveSimpleNameEndingWith("Event")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta..",
                        "org.springframework..",
                        "com.fasterxml.jackson..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void useCaseImplementationsShouldFollowNamingConvention() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..application.usecase..")
                .and().doNotHaveSimpleName("TransferAccountHelper")
                .should().haveSimpleNameEndingWith("Impl")
                .orShould().haveSimpleNameEndingWith("Handler")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void portsShouldNotDependOnImplementations() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..port..")
                .should().dependOnClassesThat().resideInAnyPackage("..usecase..");

        rule.check(importedClasses);
    }

    @Test
    void transferModuleShouldNotDependOnAccountDomainDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.account.domain..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void commonDomainShouldNotDependOnCommonInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.common.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.common.adapter..",
                        "org.springframework..",
                        "jakarta..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void commonInfrastructureShouldNotDependOnModuleDomains() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account..",
                        "com.bank.app.transfer..",
                        "com.bank.app.user..",
                        "com.bank.app.audit..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void domainEventsShouldOnlyBePublishedByDomainEntities() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..domain..")
                .and().haveSimpleNameEndingWith("Event")
                .should().resideInAPackage("..domain..");

        rule.check(importedClasses);
    }

    @Test
    void adaptersShouldNotDependOnOtherModuleAdapters() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.account.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.transfer.adapter..",
                        "com.bank.app.user.adapter..",
                        "com.bank.app.audit.adapter..");

        rule.check(importedClasses);

        rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bank.app.account.adapter..",
                        "com.bank.app.user.adapter..",
                        "com.bank.app.audit.adapter..");

        rule.check(importedClasses);
    }

    @Test
    void outboundAdaptersShouldDependOnOutPorts() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..adapter.out..")
                .and().haveSimpleNameEndingWith("Adapter")
                .should().dependOnClassesThat().resideInAnyPackage("..port.out..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void applicationLayerShouldNotDependOnJpaEntities() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("JpaEntity");

        rule.check(importedClasses);
    }

    @Test
    void applicationLayerShouldNotDependOnSpringTransaction() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.transaction..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }
}
