package com.bank.app.architecture;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@SuppressWarnings("null")
class PortsAndNamingArchitectureTest extends ArchitectureTest {

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
    void useCaseImplementationsShouldFollowNamingConvention() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..application.usecase..")
                .should().haveSimpleNameEndingWith("Impl")
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
    void outboundAdaptersShouldDependOnOutPorts() {
        // Outbound adapters must program against abstractions: module-owned
        // out-ports or the account-api published language (which hosts the
        // shared snapshot-cache contract).
        ArchRule rule = classes()
                .that().resideInAnyPackage("..adapter.out..")
                .and().haveSimpleNameEndingWith("Adapter")
                .should().dependOnClassesThat().resideInAnyPackage("..port.out..", "com.bank.app.accountapi..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void securityPortsShouldResideInUserModule() {
        // Token lifecycle ports are owned by the User BC, not the shared kernel (ISP).
        ArchRule rule = classes()
                .that().haveSimpleName("JwtPort")
                .or().haveSimpleName("TokenBlacklistPort")
                .should().resideInAPackage("com.bank.app.user.application.port.out..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void transferPortsShouldNotExposeDomainEvents() {
        // Account domain events must not cross the context boundary; balance mutations
        // return the transfer-owned opaque result instead.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.application.port..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.common.domain.event..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void transferPortsShouldNotDependOnAccountPublishedLanguage() {
        // ACL port owns its own MutationResult; only the adapter may depend on account-api.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.application.port..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.accountapi..")
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }
}
