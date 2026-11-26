package com.vidacruz.assistant.usecase.urgencystate;

import com.vidacruz.assistant.model.urgency.UrgencyState;
import com.vidacruz.assistant.model.urgency.gateways.UrgencyStateRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * RF-04 part B: the active patients per level, read from the projection (never from the triage API).
 */
@RequiredArgsConstructor
public class GetUrgencyStateUseCase {

    private final UrgencyStateRepository urgencyStateRepository;

    public Mono<UrgencyState> current() {
        return urgencyStateRepository.current();
    }
}
