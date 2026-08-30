package com.techeazyhq.cm.testkit;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/**
 * Enforces the Clean Architecture dependency rule (plan §5) as a normal test, not a manual
 * review step: Domain depends on nothing internal; Application depends on Domain only;
 * Infrastructure/Interfaces depend on Application+Domain but never on each other. Each
 * service extends this, pointing at its own base package (the {@code -boot} module's test
 * sourceset scans the whole service since it's the only module that depends on all four).
 *
 * <p>Package convention: {@code <basePackage>.dom}, {@code .app}, {@code .infra}, {@code .ifc}
 * — note "ifc", not "if", since {@code if} is a reserved Java keyword and cannot be a package
 * segment even though the Gradle module is named {@code -if-rest} per the plan.
 */
public abstract class LayeredArchitectureTest {

    protected abstract String basePackage();

    @Test
    void respectsCleanArchitectureLayering() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(basePackage());

        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(basePackage() + ".dom..")
                .layer("Application").definedBy(basePackage() + ".app..")
                .layer("Infrastructure").definedBy(basePackage() + ".infra..")
                .layer("Interfaces").definedBy(basePackage() + ".ifc..")
                .whereLayer("Domain").mayNotAccessAnyLayer()
                .whereLayer("Application").mayOnlyAccessLayers("Domain")
                .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain", "Application")
                .whereLayer("Interfaces").mayOnlyAccessLayers("Domain", "Application")
                .check(importedClasses);
    }
}
