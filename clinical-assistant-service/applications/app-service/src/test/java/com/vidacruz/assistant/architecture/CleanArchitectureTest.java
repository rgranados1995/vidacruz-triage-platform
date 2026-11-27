package com.vidacruz.assistant.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The clean architecture of the assistant, checked on its production classes (every module is on the classpath of
 * app-service). A broken rule fails this test and the build.
 */
class CleanArchitectureTest {

    private static final String ASSISTANT = "com.vidacruz.assistant";
    private static final String TRIAGE = "com.vidacruz.triage";

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ASSISTANT);
    }

    @Test
    void theDomainUsesNoTechnology() {
        CleanArchitectureRules.domainUsesNoTechnology(ASSISTANT).check(production);
    }

    @Test
    void theLayersKeepTheirDirection() {
        CleanArchitectureRules.layersKeepTheirDirection(ASSISTANT).check(production);
    }

    @Test
    void theAssistantDoesNotDependOnTheTriage() {
        CleanArchitectureRules.doesNotDependOnTheTriage(ASSISTANT, TRIAGE).check(production);
    }

    @Test
    void useCasesHaveOnlyFinalFields() {
        CleanArchitectureRules.useCasesHaveOnlyFinalFields(ASSISTANT).check(production);
    }

    @Test
    void nothingBlocks() {
        CleanArchitectureRules.neverBlocks(ASSISTANT).check(production);
    }
}
