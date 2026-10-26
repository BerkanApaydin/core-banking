package com.bank.app.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;
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
                .allowEmptyShould(false);

        ArchRule outPorts = classes()
                .that().resideInAnyPackage("..port.out..")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("Port")
                .orShould().haveSimpleNameEndingWith("Acl")
                .allowEmptyShould(false);

        inPorts.check(importedClasses);
        outPorts.check(importedClasses);
    }

    @Test
    void useCaseImplementationsShouldFollowNamingConvention() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..application.usecase..")
                .should().haveSimpleNameEndingWith("Impl")
                .allowEmptyShould(false);

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
        // shared snapshot-cache contract). Inheriting an adapter base that
        // implements the port is equally valid.
        ArchCondition<JavaClass> programsAgainstAnOutPort = new ArchCondition<>(
                "depend on an out-port or the account API") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean dependsDirectly = item.getDirectDependenciesFromSelf().stream()
                        .map(Dependency::getTargetClass)
                        .anyMatch(PortsAndNamingArchitectureTest::isOutPortOrApi);
                boolean implementsInherited = item.getAllRawInterfaces().stream()
                        .anyMatch(PortsAndNamingArchitectureTest::isOutPortOrApi);
                if (!dependsDirectly && !implementsInherited) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getDescription() + " does not depend on an out-port or the account API"));
                }
            }
        };

        ArchRule rule = classes()
                .that().resideInAnyPackage("..adapter.out..")
                .and().haveSimpleNameEndingWith("Adapter")
                .should(programsAgainstAnOutPort)
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    private static boolean isOutPortOrApi(JavaClass type) {
        String packageName = type.getPackageName();
        return packageName.matches(".*\\.port\\.out(\\..*)?")
                || packageName.equals("com.bank.app.account.api")
                || packageName.startsWith("com.bank.app.account.api.");
    }

    @Test
    void securityPortsShouldResideInUserModule() {
        // Token lifecycle ports are owned by the User BC, not the shared kernel (ISP).
        ArchRule rule = classes()
                .that().haveSimpleName("JwtPort")
                .or().haveSimpleName("TokenBlacklistPort")
                .should().resideInAPackage("com.bank.app.user.application.port.out..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void transferPortsShouldNotExposeDomainEvents() {
        // Account domain events must not cross the context boundary; balance mutations
        // return the transfer-owned opaque result instead.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.application.port..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.common.domain.event..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void transferPortsShouldNotDependOnAccountPublishedLanguage() {
        // ACL port owns its own MutationResult; only the adapter may depend on account-api.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.transfer.application.port..")
                .should().dependOnClassesThat().resideInAnyPackage("com.bank.app.account.api..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void adaptersShouldResideInAdapterPackages() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Adapter")
                .should().resideInAPackage("..adapter..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void restControllersShouldLiveInWebAdapterPackagesAndEndWithController() {
        ArchRule packageRule = classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..adapter.in.web..")
                .allowEmptyShould(false);

        ArchRule namingRule = classes()
                .that().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .allowEmptyShould(false);

        packageRule.check(importedClasses);
        namingRule.check(importedClasses);
    }

    @Test
    void applicationImplementationsShouldResideInUseCasePackage() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..application..")
                .and().haveSimpleNameEndingWith("Impl")
                .should().resideInAPackage("..application.usecase..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }
}
