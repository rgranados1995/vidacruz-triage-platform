package com.vidacruz.triage.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The clean architecture of the triage, checked on its production classes (every module is on the classpath of
 * app-service). A broken rule fails this test and the build.
 */
class CleanArchitectureTest {

    private static final String TRIAGE = "com.vidacruz.triage";
    private static final String ASSISTANT = "com.vidacruz.assistant";

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(TRIAGE);
    }

    @Test
    void theDomainUsesNoTechnology() {
        CleanArchitectureRules.domainUsesNoTechnology(TRIAGE).check(production);
    }

    @Test
    void theLayersKeepTheirDirection() {
        CleanArchitectureRules.layersKeepTheirDirection(TRIAGE).check(production);
    }

    @Test
    void theTriageDoesNotDependOnTheAssistant() {
        CleanArchitectureRules.doesNotDependOnTheAssistant(TRIAGE, ASSISTANT).check(production);
    }

    @Test
    void useCasesHaveOnlyFinalFields() {
        CleanArchitectureRules.useCasesHaveOnlyFinalFields(TRIAGE).check(production);
    }

    @Test
    void nothingBlocks() {
        CleanArchitectureRules.neverBlocks(TRIAGE).check(production);
    }
}
