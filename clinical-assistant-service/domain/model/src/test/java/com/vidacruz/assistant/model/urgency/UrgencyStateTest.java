package com.vidacruz.assistant.model.urgency;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UrgencyStateTest {

    private static final Instant AT = Instant.parse("2026-11-26T10:00:00Z");

    @Test
    void shouldListEveryLevelAndAddThemUp() {
        var counts = new HashMap<TriageLevel, Long>(Map.of(TriageLevel.CRITICAL, 2L, TriageLevel.NON_URGENT, 3L));

        var state = new UrgencyState(counts, Optional.of(AT));
        counts.put(TriageLevel.URGENT, 9L);

        assertThat(state.activeByLevel()).containsOnlyKeys(TriageLevel.values())
                .containsEntry(TriageLevel.CRITICAL, 2L)
                .containsEntry(TriageLevel.URGENT, 0L)
                .containsEntry(TriageLevel.NON_URGENT, 3L);
        assertThat(state.totalActive()).isEqualTo(5);
        assertThat(state.updatedAt()).contains(AT);
    }

    @Test
    void shouldStartEmptyBeforeAnyEvent() {
        var state = new UrgencyState(Map.of(), Optional.empty());

        assertThat(state.totalActive()).isZero();
        assertThat(state.activeByLevel().values()).containsOnly(0L);
        assertThat(state.updatedAt()).isEmpty();
    }

    @Test
    void shouldCountEveryPatientNotDischargedAsActive() {
        var id = UUID.randomUUID();

        assertThat(new PatientSnapshot(id, TriageLevel.URGENT, PatientStatus.REGISTERED, AT).isActive()).isTrue();
        assertThat(new PatientSnapshot(id, TriageLevel.URGENT, PatientStatus.IN_CONSULTATION, AT).isActive()).isTrue();
        assertThat(new PatientSnapshot(id, TriageLevel.URGENT, PatientStatus.DISCHARGED, AT).isActive()).isFalse();
    }

    @Test
    void shouldRequireEveryFieldOfTheSnapshot() {
        var id = UUID.randomUUID();

        assertThatNullPointerException().isThrownBy(() -> new PatientSnapshot(null, TriageLevel.URGENT,
                PatientStatus.REGISTERED, AT));
        assertThatNullPointerException().isThrownBy(() -> new PatientSnapshot(id, null, PatientStatus.REGISTERED, AT));
        assertThatNullPointerException().isThrownBy(() -> new PatientSnapshot(id, TriageLevel.URGENT, null, AT));
        assertThatNullPointerException().isThrownBy(() -> new PatientSnapshot(id, TriageLevel.URGENT,
                PatientStatus.REGISTERED, null));
    }
}
