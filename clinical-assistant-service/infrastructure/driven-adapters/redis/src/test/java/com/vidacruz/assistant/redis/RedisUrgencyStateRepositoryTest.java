package com.vidacruz.assistant.redis;

import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.model.urgency.PatientSnapshot;
import com.vidacruz.assistant.model.urgency.PatientStatus;
import com.vidacruz.assistant.model.urgency.TriageLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the adapter hands to the scripts, without Redis (the scripts themselves run in the integration test).
 */
class RedisUrgencyStateRepositoryTest {

    private static final UUID PATIENT = UUID.fromString("3f2b8c1e-5d4a-4f6b-9c2d-1a7e8b9c0d12");
    private static final Instant AT = Instant.parse("2026-11-26T10:04:12.345678Z");

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandTheScriptEveryKeyAndTheStateOfThePatient() throws Exception {
        var keys = ArgumentCaptor.forClass(List.class);
        var arguments = ArgumentCaptor.forClass(List.class);
        when(redis.execute(any(RedisScript.class), keys.capture(), arguments.capture())).thenReturn(Flux.just(1L));

        StepVerifier.create(repository().apply(new PatientSnapshot(PATIENT, TriageLevel.URGENT,
                        PatientStatus.DISCHARGED, AT)))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        assertThat(keys.getValue()).containsExactly("{urgency}:patient:" + PATIENT, "{urgency}:updated-at",
                "{urgency}:active:CRITICAL", "{urgency}:active:URGENT", "{urgency}:active:URGENT_2",
                "{urgency}:active:NON_URGENT", "{urgency}:active:NON_URGENT_2", "{urgency}:active:URGENT");
        assertThat(arguments.getValue()).containsExactly(PATIENT.toString(), "URGENT", "DISCHARGED",
                "1795687452345678", "0", "604800");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldTellAnActivePatientAndAnIgnoredEventApart() throws Exception {
        var arguments = ArgumentCaptor.forClass(List.class);
        when(redis.execute(any(RedisScript.class), anyList(), arguments.capture())).thenReturn(Flux.just(0L));

        StepVerifier.create(repository().apply(new PatientSnapshot(PATIENT, TriageLevel.CRITICAL,
                        PatientStatus.IN_CONSULTATION, AT)))
                .expectNext(Boolean.FALSE)
                .verifyComplete();

        assertThat(arguments.getValue().get(4)).isEqualTo("1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReadTheCountsOfEveryLevelAndTheLatestUpdate() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(1L, 0L, 2L, 0L, 3L, "1795687452345678")));

        StepVerifier.create(repository().current())
                .assertNext(state -> {
                    assertThat(state.activeByLevel()).containsEntry(TriageLevel.CRITICAL, 1L)
                            .containsEntry(TriageLevel.URGENT_2, 2L).containsEntry(TriageLevel.NON_URGENT_2, 3L);
                    assertThat(state.updatedAt()).contains(AT);
                })
                .verifyComplete();
    }

    @Test
    void shouldReadAProjectionWithoutEvents() {
        var state = RedisUrgencyStateRepository.toState(List.of(0L, 0L, 0L, 0L, 0L, ""));

        assertThat(state.totalActive()).isZero();
        assertThat(state.updatedAt()).isEmpty();
        assertThat(RedisUrgencyStateRepository.toMicros(AT)).isEqualTo(1795687452345678L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseTheInitializationMark() throws Exception {
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        when(redis.hasKey("{urgency}:initialized")).thenReturn(Mono.just(Boolean.TRUE));
        when(redis.opsForValue()).thenReturn(values);
        when(values.set(eq("{urgency}:initialized"), anyString())).thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(repository().isInitialized()).expectNext(Boolean.TRUE).verifyComplete();
        StepVerifier.create(repository().markInitialized()).verifyComplete();
        verify(values).set(eq("{urgency}:initialized"), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReportTheStateUnavailableAndKeepABusinessErrorAsIs() throws Exception {
        var unavailable = new BusinessException(BusinessError.URGENCY_STATE_UNAVAILABLE, "");
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new IllegalStateException("connection reset")));
        when(redis.hasKey(anyString())).thenReturn(Mono.error(unavailable));

        StepVerifier.create(repository().current())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class)
                            .hasCauseInstanceOf(IllegalStateException.class);
                    assertThat(((BusinessException) error).getError())
                            .isEqualTo(BusinessError.URGENCY_STATE_UNAVAILABLE);
                })
                .verify();
        StepVerifier.create(repository().isInitialized()).expectErrorMatches(error -> error == unavailable).verify();
    }

    @Test
    void shouldRequireAPositiveRetention() {
        var zero = Duration.ZERO;

        assertThatIllegalArgumentException().isThrownBy(() -> new UrgencyStateProperties(zero));
        assertThatIllegalArgumentException().isThrownBy(() -> new UrgencyStateProperties(null));
    }

    private RedisUrgencyStateRepository repository() throws Exception {
        return new RedisUrgencyStateRepository(redis, new UrgencyStateProperties(Duration.ofDays(7)));
    }
}
