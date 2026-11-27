package com.vidacruz.triage.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each rule of {@link CleanArchitectureRules} catches a deliberate violation: the fixtures of
 * {@code com.vidacruz.triage.archfixture} break one rule each, so a rule that stopped working would fail here.
 */
class CleanArchitectureRulesTest {

    private static final String FIXTURES = "com.vidacruz.triage.archfixture";
    private static final String FOREIGN = "com.vidacruz.assistant.archfixture";

    private static JavaClasses fixtures;

    @BeforeAll
    static void importFixtures() {
        fixtures = new ClassFileImporter().importPackages(FIXTURES, FOREIGN);
    }

    @Test
    void shouldCatchADomainClassThatKnowsSpring() {
        assertViolation(CleanArchitectureRules.domainUsesNoTechnology(FIXTURES), "SpringModel");
    }

    @Test
    void shouldCatchAnEntryPointThatSkipsTheDomain() {
        assertViolation(CleanArchitectureRules.layersKeepTheirDirection(FIXTURES), "LeakyHandler");
    }

    @Test
    void shouldCatchADependencyOnTheOtherService() {
        assertViolation(CleanArchitectureRules.doesNotDependOnTheAssistant(FIXTURES, FOREIGN), "CrossServicePublisher");
    }

    @Test
    void shouldCatchAUseCaseWithMutableState() {
        assertViolation(CleanArchitectureRules.useCasesHaveOnlyFinalFields(FIXTURES), "calls");
    }

    @Test
    void shouldCatchABlockingCall() {
        assertViolation(CleanArchitectureRules.neverBlocks(FIXTURES), "MutableUseCase");
    }

    private static void assertViolation(ArchRule rule, String culprit) {
        var result = rule.evaluate(fixtures);

        assertThat(result.hasViolation()).as(rule.getDescription()).isTrue();
        assertThat(result.getFailureReport().toString()).contains(culprit);
    }
}
