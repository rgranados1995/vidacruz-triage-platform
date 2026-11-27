package com.vidacruz.assistant.architecture;

import com.tngtech.archunit.lang.ArchRule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Rules of the clean architecture of the assistant, written for a base package so the same rules check the real code
 * and the fixtures that prove each rule fails when it is broken. They fail the build: no rule only warns.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CleanArchitectureRules {

    /** The domain (model and use cases) depends only on Java, Lombok and Reactor: no framework, driver or SDK. */
    static ArchRule domainUsesNoTechnology(String base) {
        return noClasses().that().resideInAnyPackage(base + ".model..", base + ".usecase..")
                .should().dependOnClassesThat().resideOutsideOfPackages(
                        base + ".model..", base + ".usecase..", "java..", "lombok..", "reactor.core..",
                        "reactor.util..")
                .as("The domain of the assistant depends only on Java, Lombok and Reactor");
    }

    /**
     * Each adapter talks only to the domain: entry points never reach a driven adapter, adapters never reach each
     * other, and only the application wiring (config) sees them all.
     */
    static ArchRule layersKeepTheirDirection(String base) {
        return layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Model").definedBy(base + ".model..")
                .layer("UseCases").definedBy(base + ".usecase..")
                .layer("HttpApi").definedBy(base + ".api..")
                .layer("TriageEvents").definedBy(base + ".kafka..")
                .layer("Corpus").definedBy(base + ".corpus..")
                .layer("Claude").definedBy(base + ".claude..")
                .layer("Redis").definedBy(base + ".redis..")
                .layer("Config").definedBy(base + ".config..")
                .whereLayer("UseCases").mayOnlyBeAccessedByLayers("HttpApi", "TriageEvents", "Config")
                .whereLayer("HttpApi").mayNotBeAccessedByAnyLayer()
                .whereLayer("TriageEvents").mayNotBeAccessedByAnyLayer()
                .whereLayer("Corpus").mayOnlyBeAccessedByLayers("Config")
                .whereLayer("Claude").mayOnlyBeAccessedByLayers("Config")
                .whereLayer("Redis").mayOnlyBeAccessedByLayers("Config")
                .whereLayer("Model").mayNotAccessAnyLayer()
                .whereLayer("UseCases").mayOnlyAccessLayers("Model")
                .as("The layers of the assistant keep the direction of the clean architecture");
    }

    /** The assistant never uses code of the triage: it only reads the published event contract from Kafka. */
    static ArchRule doesNotDependOnTheTriage(String base, String triageBase) {
        return noClasses().that().resideInAPackage(base + "..")
                .should().dependOnClassesThat().resideInAPackage(triageBase + "..")
                .as("The assistant does not depend on the triage");
    }

    /** A use case is shared by every request: its state is fixed at construction. */
    static ArchRule useCasesHaveOnlyFinalFields(String base) {
        return fields().that().areDeclaredInClassesThat().resideInAPackage(base + ".usecase..")
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("UseCase")
                .and().areNotStatic()
                .should().beFinal()
                .allowEmptyShould(true)
                .as("Use cases only have final fields");
    }

    /** Nothing in the service waits for a reactive result: no block, blockFirst, blockLast, toIterable or toStream. */
    static ArchRule neverBlocks(String base) {
        return noClasses().that().resideInAPackage(base + "..")
                .should().callMethod(Mono.class, "block")
                .orShould().callMethod(Mono.class, "block", Duration.class)
                .orShould().callMethod(Mono.class, "blockOptional")
                .orShould().callMethod(Mono.class, "blockOptional", Duration.class)
                .orShould().callMethod(Flux.class, "blockFirst")
                .orShould().callMethod(Flux.class, "blockLast")
                .orShould().callMethod(Flux.class, "toIterable")
                .orShould().callMethod(Flux.class, "toStream")
                .as("The assistant never blocks on a reactive result");
    }
}
