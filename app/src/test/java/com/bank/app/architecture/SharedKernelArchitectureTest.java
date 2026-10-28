package com.bank.app.architecture;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@SuppressWarnings("null")
class SharedKernelArchitectureTest extends ArchitectureTest {

    @Test
    void commonShouldNotContainUtilityPackages() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.common..")
                .should().resideInAnyPackage("..util..", "..utils..", "..helper..", "..helpers..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void commonShouldNotGrowUtilityTypeNames() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bank.app.common..")
                .should().haveNameMatching(".*(?:Util|Utils|Helper|Helpers)$")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }
}
