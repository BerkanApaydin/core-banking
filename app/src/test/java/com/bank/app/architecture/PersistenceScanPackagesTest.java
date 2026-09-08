package com.bank.app.architecture;

import com.bank.app.BankApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the composition root's persistence scan to explicit per-BC packages:
 * repository and entity scans must cover each other, and every
 * {@code *JpaRepository} / {@code *JpaEntity} on the classpath must live in a
 * listed package — so a new persistence type can never silently fall out of
 * (or into) the single persistence unit.
 */
class PersistenceScanPackagesTest {

    private static Set<String> repoPackages() {
        return new HashSet<>(Arrays.asList(
                BankApplication.class.getAnnotation(EnableJpaRepositories.class).basePackages()));
    }

    private static Set<String> entityPackages() {
        return new HashSet<>(Arrays.asList(
                BankApplication.class.getAnnotation(EntityScan.class).basePackages()));
    }

    @Test
    void repositoryAndEntityScansShouldCoverTheSamePackages() {
        assertThat(repoPackages())
                .as("EnableJpaRepositories and EntityScan must not drift apart")
                .containsExactlyInAnyOrderElementsOf(entityPackages());
    }

    @Test
    void allJpaRepositoriesShouldResideInScannedPackages() {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.bank.app");
        String[] packages = repoPackages().toArray(String[]::new);

        classes()
                .that().areInterfaces()
                .and().haveSimpleNameEndingWith("JpaRepository")
                .should().resideInAnyPackage(packages)
                .because("every repository must be picked up by EnableJpaRepositories")
                .check(imported);
    }

    @Test
    void allJpaEntitiesShouldResideInScannedPackages() {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.bank.app");
        String[] packages = entityPackages().toArray(String[]::new);

        classes()
                .that().haveSimpleNameEndingWith("JpaEntity")
                .should().resideInAnyPackage(packages)
                .because("every entity (incl. mapped superclasses) must be picked up by EntityScan")
                .check(imported);
    }
}
