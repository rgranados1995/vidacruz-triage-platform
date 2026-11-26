package com.vidacruz.assistant.usecase.urgencystate;

import com.vidacruz.assistant.model.urgency.PatientSnapshot;
import com.vidacruz.assistant.model.urgency.PatientStatus;
import com.vidacruz.assistant.model.urgency.TriageLevel;
import com.vidacruz.assistant.model.urgency.UrgencyState;
import com.vidacruz.assistant.model.urgency.gateways.UrgencyStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrgencyStateUseCasesTest {

    private static final PatientSnapshot REGISTERED = new PatientSnapshot(UUID.randomUUID(), TriageLevel.CRITICAL,
            PatientStatus.REGISTERED, Instant.parse("2026-11-26T10:00:00Z"));

    @Mock
    private UrgencyStateRepository repository;

    @Test
    void shouldApplyEachEventToTheProjection() {
        when(repository.apply(REGISTERED)).thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(new ProjectTriageEventUseCase(repository).project(REGISTERED))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
    }

    @Test
    void shouldRebuildOnlyAProjectionThatWasNeverInitialized() {
        when(repository.isInitialized()).thenReturn(Mono.just(Boolean.FALSE), Mono.just(Boolean.TRUE));
        var useCase = new ProjectTriageEventUseCase(repository);

        StepVerifier.create(useCase.needsRebuild()).expectNext(Boolean.TRUE).verifyComplete();
        StepVerifier.create(useCase.needsRebuild()).expectNext(Boolean.FALSE).verifyComplete();
    }

    @Test
    void shouldMarkTheProjectionOnceTheRebuildStarts() {
        when(repository.markInitialized()).thenReturn(Mono.empty());

        StepVerifier.create(new ProjectTriageEventUseCase(repository).rebuildStarted()).verifyComplete();
        verify(repository).markInitialized();
    }

    @Test
    void shouldReadTheCurrentState() {
        var state = new UrgencyState(Map.of(TriageLevel.CRITICAL, 1L), Optional.empty());
        when(repository.current()).thenReturn(Mono.just(state));

        StepVerifier.create(new GetUrgencyStateUseCase(repository).current()).expectNext(state).verifyComplete();
    }
}
