package com.auditlog.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class ArchitectureTest {

    static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.auditlog");
    }

    // -------------------------------------------------------------------------
    // Layer dependency rules
    // -------------------------------------------------------------------------

    @Test
    void layerDependenciesAreRespected() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy("com.auditlog.controller..")
                .layer("Facade").definedBy("com.auditlog.facade..")
                .layer("Service").definedBy("com.auditlog.service..")
                .layer("DAO").definedBy("com.auditlog.dao..")
                .layer("DTO").definedBy("com.auditlog.dto..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Facade").mayOnlyBeAccessedByLayers("Controller")
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Facade", "Service")
                .whereLayer("DAO").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("DTO").mayOnlyBeAccessedByLayers("Controller", "Facade", "Service", "DAO")
                .check(classes);
    }

    @Test
    void controllersMustNotAccessDaoDirectly() {
        noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..dao..")
                .check(classes);
    }

    @Test
    void controllersMustNotAccessServiceDirectly() {
        noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..service..")
                .check(classes);
    }

    @Test
    void facadesMustNotAccessDaoDirectly() {
        noClasses()
                .that().resideInAPackage("..facade..")
                .should().dependOnClassesThat().resideInAPackage("..dao..")
                .check(classes);
    }

    // -------------------------------------------------------------------------
    // Naming conventions
    // -------------------------------------------------------------------------

    @Test
    void controllerClassesMustHaveControllerSuffix() {
        classes()
                .that().resideInAPackage("..controller..")
                .should().haveSimpleNameEndingWith("Controller")
                .check(classes);
    }

    @Test
    void facadeClassesMustHaveFacadeSuffix() {
        classes()
                .that().resideInAPackage("..facade..")
                .should().haveSimpleNameEndingWith("Facade")
                .check(classes);
    }

    @Test
    void serviceTypesMustHaveServiceOrServiceImplSuffix() {
        classes()
                .that().resideInAPackage("..service..")
                .and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Service")
                .orShould().haveSimpleNameEndingWith("ServiceImpl")
                .check(classes);
    }

    @Test
    void repositoryTypesMustHaveRepositorySuffix() {
        classes()
                .that().resideInAPackage("..dao.repository..")
                .should().haveSimpleNameEndingWith("Repository")
                .check(classes);
    }

    @Test
    void entityTypesMustHaveEntitySuffix() {
        classes()
                .that().resideInAPackage("..dao.entity..")
                .should().haveSimpleNameEndingWith("Entity")
                .check(classes);
    }

    // -------------------------------------------------------------------------
    // Annotation rules
    // -------------------------------------------------------------------------

    @Test
    void controllersMustBeAnnotatedWithRestController() {
        classes()
                .that().resideInAPackage("..controller..")
                .should().beAnnotatedWith(RestController.class)
                .check(classes);
    }

    @Test
    void serviceImplementationsMustBeAnnotatedWithService() {
        classes()
                .that().resideInAPackage("..service.impl..")
                .should().beAnnotatedWith(Service.class)
                .check(classes);
    }

    @Test
    void entitiesMustBeAnnotatedWithEntity() {
        classes()
                .that().resideInAPackage("..dao.entity..")
                .should().beAnnotatedWith(Entity.class)
                .check(classes);
    }

    @Test
    void entitiesMustNotHaveSpringStereotypeAnnotations() {
        noClasses()
                .that().resideInAPackage("..dao.entity..")
                .should().beAnnotatedWith(Service.class)
                .orShould().beAnnotatedWith(org.springframework.stereotype.Component.class)
                .orShould().beAnnotatedWith(org.springframework.stereotype.Repository.class)
                .check(classes);
    }
}
