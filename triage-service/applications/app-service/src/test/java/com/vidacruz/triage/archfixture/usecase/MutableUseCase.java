package com.vidacruz.triage.archfixture.usecase;

import reactor.core.publisher.Mono;

/** Fixture: a use case with mutable state that blocks (breaks useCasesHaveOnlyFinalFields and neverBlocks). */
public class MutableUseCase {

    private int calls;

    public Integer run() {
        calls++;
        return Mono.just(calls).block();
    }
}
